package com.printflow.service;

import com.printflow.dto.EmailMessage;
import com.printflow.entity.Company;
import com.printflow.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
            "no-reply@printflow.local"
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
            "no-reply@printflow.local"
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
}
