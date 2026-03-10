package com.printflow.service;

import com.printflow.dto.EmailMessage;
import com.printflow.entity.Company;
import com.printflow.repository.EmailOutboxRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void asyncSendSwallowsOutboxPersistenceFailure() {
        MailSenderResolver resolver = mock(MailSenderResolver.class);
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("fk failure"));

        EmailService service = new EmailService(
            resolver,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            2
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
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("fk failure"));

        EmailService service = new EmailService(
            resolver,
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            2
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
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        TenantEmailRateLimiter rateLimiter = mock(TenantEmailRateLimiter.class);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
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
            outboxRepository,
            rateLimiter,
            true,
            "no-reply@printflow.local",
            2
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
}
