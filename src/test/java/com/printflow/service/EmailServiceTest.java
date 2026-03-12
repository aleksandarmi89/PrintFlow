package com.printflow.service;

import com.printflow.dto.EmailMessage;
import com.printflow.entity.Company;
import com.printflow.entity.EmailOutbox;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.EmailOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void asyncSendSwallowsOutboxPersistenceFailure() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(companyRepository.existsById(any())).thenReturn(true);
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("fk failure"));

        EmailService service = new EmailService(
            resolver,
            companyRepository,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            2,
            0L,
            Optional.empty()
        );

        EmailMessage msg = new EmailMessage();
        msg.setTo("customer@example.com");
        msg.setSubject("Subject");
        msg.setTextBody("Body");
        Company company = new Company();
        company.setId(1L);

        assertDoesNotThrow(() -> service.send(msg, company, "test-template"));
        verify(outboxRepository).save(any());
    }

    @Test
    void sendNowPropagatesOutboxPersistenceFailure() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(companyRepository.existsById(any())).thenReturn(true);
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("fk failure"));

        EmailService service = new EmailService(
            resolver,
            companyRepository,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            2,
            0L,
            Optional.empty()
        );

        EmailMessage msg = new EmailMessage();
        msg.setTo("customer@example.com");
        msg.setSubject("Subject");
        msg.setTextBody("Body");
        Company company = new Company();
        company.setId(1L);

        assertThrows(RuntimeException.class, () -> service.sendNow(msg, company, "test-template"));
        verify(outboxRepository).save(any());
    }

    @Test
    void sendNowRetriesTransientMailFailure() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(companyRepository.existsById(any())).thenReturn(true);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var sender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("temporary smtp failure"))
            .doNothing()
            .when(sender)
            .send(any(MimeMessage.class));
        when(resolver.resolve(any())).thenReturn(new MailSenderResolver.ResolvedMailSender(sender, null));

        EmailService service = new EmailService(
            resolver,
            companyRepository,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            2,
            0L,
            Optional.empty()
        );

        EmailMessage msg = new EmailMessage();
        msg.setTo("customer@example.com");
        msg.setSubject("Subject");
        msg.setTextBody("Body");
        Company company = new Company();
        company.setId(1L);

        assertDoesNotThrow(() -> service.sendNow(msg, company, "test-template"));
        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void sendNowIncrementsFailureMetricWhenSmtpFails() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(companyRepository.existsById(any())).thenReturn(true);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var sender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("smtp down")).when(sender).send(any(MimeMessage.class));
        when(resolver.resolve(any())).thenReturn(new MailSenderResolver.ResolvedMailSender(sender, null));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EmailService service = new EmailService(
            resolver,
            companyRepository,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            1,
            0L,
            Optional.of(registry)
        );

        EmailMessage msg = new EmailMessage();
        msg.setTo("customer@example.com");
        msg.setSubject("Subject");
        msg.setTextBody("Body");
        Company company = new Company();
        company.setId(1L);

        assertThrows(RuntimeException.class, () -> service.sendNow(msg, company, "test-template"));
        double failures = registry.get("printflow_email_send_failures_total").counter().count();
        double retries = registry.get("printflow_email_send_retries_total").counter().count();
        assertEquals(1.0d, failures);
        assertEquals(0.0d, retries);
    }

    @Test
    void sendNowAppliesBackoffBetweenRetries() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(companyRepository.existsById(any())).thenReturn(true);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var sender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("temporary smtp failure"))
            .doNothing()
            .when(sender)
            .send(any(MimeMessage.class));
        when(resolver.resolve(any())).thenReturn(new MailSenderResolver.ResolvedMailSender(sender, null));

        EmailService service = new EmailService(
            resolver,
            companyRepository,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            2,
            30L,
            Optional.empty()
        );

        EmailMessage msg = new EmailMessage();
        msg.setTo("customer@example.com");
        msg.setSubject("Subject");
        msg.setTextBody("Body");
        Company company = new Company();
        company.setId(1L);

        long startNs = System.nanoTime();
        assertDoesNotThrow(() -> service.sendNow(msg, company, "test-template"));
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        verify(sender, times(2)).send(any(MimeMessage.class));
        assertTrue(elapsedMs >= 25, "Expected retry backoff to add delay, elapsedMs=" + elapsedMs);
    }

    @Test
    void sendNowDropsStaleCompanyReferenceFromOutbox() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(companyRepository.existsById(5L)).thenReturn(false);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var sender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mimeMessage);
        when(resolver.resolve(any())).thenReturn(new MailSenderResolver.ResolvedMailSender(sender, null));

        EmailService service = new EmailService(
            resolver,
            companyRepository,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            1,
            0L,
            Optional.empty()
        );

        EmailMessage msg = new EmailMessage();
        msg.setTo("customer@example.com");
        msg.setSubject("Subject");
        msg.setTextBody("Body");
        Company company = new Company();
        company.setId(5L);

        assertDoesNotThrow(() -> service.sendNow(msg, company, "test-template"));
        var captor = forClass(EmailOutbox.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        EmailOutbox firstSaved = captor.getAllValues().get(0);
        assertNull(firstSaved.getCompany());
    }

    @Test
    void sendNowRetriesOutboxPersistWithoutCompanyOnForeignKeyViolation() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(companyRepository.existsById(9L)).thenReturn(true);
        AtomicInteger saveAttempt = new AtomicInteger(0);
        List<Long> companyIdsBySaveAttempt = new ArrayList<>();
        when(outboxRepository.save(any())).thenAnswer(inv -> {
            EmailOutbox outbox = inv.getArgument(0);
            companyIdsBySaveAttempt.add(outbox.getCompany() == null ? null : outbox.getCompany().getId());
            if (saveAttempt.getAndIncrement() == 0) {
                throw new DataIntegrityViolationException("fk violation");
            }
            return outbox;
        });

        var sender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mimeMessage);
        when(resolver.resolve(any())).thenReturn(new MailSenderResolver.ResolvedMailSender(sender, null));

        EmailService service = new EmailService(
            resolver,
            companyRepository,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            1,
            0L,
            Optional.empty()
        );

        EmailMessage msg = new EmailMessage();
        msg.setTo("customer@example.com");
        msg.setSubject("Subject");
        msg.setTextBody("Body");
        Company company = new Company();
        company.setId(9L);

        assertDoesNotThrow(() -> service.sendNow(msg, company, "test-template"));
        verify(outboxRepository, times(3)).save(any(EmailOutbox.class));
        assertEquals(9L, companyIdsBySaveAttempt.get(0));
        assertNull(companyIdsBySaveAttempt.get(1));
    }
}
