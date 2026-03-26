package com.printflow.controller;

import com.printflow.dto.CompanyDTO;
import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.entity.enums.EmailOutboxStatus;
import com.printflow.service.CompanyService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.EmailOutboxService;
import com.printflow.service.EmailService;
import com.printflow.service.MailSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class EmailSettingsControllerTest {

    @Test
    void settingsUsesMailSettingsAsSmtpSourceWhenConfigured() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(31L);
        company.setName("Tenant 31");
        CompanyDTO dto = new CompanyDTO();
        dto.setId(31L);
        dto.setName("Tenant 31");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("noreply@example.com");
        settings.setSmtpPasswordEnc("enc");
        settings.setEnabled(true);

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.isConfigured(settings)).thenReturn(true);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("mail_settings");
        when(mailSettingsService.toDto(settings)).thenReturn(new com.printflow.dto.MailSettingsDTO());
        when(companyService.getCompanyById(31L)).thenReturn(dto);
        when(emailOutboxService.listForCompany(company, null, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        when(emailOutboxService.totalForCompany(company)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.SENT)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.FAILED)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.PENDING)).thenReturn(0L);

        Model model = new ExtendedModelMap();
        String view = controller.settings(null, null, null, null, null, 0, model);

        assertEquals("admin/settings/email", view);
        assertEquals("mail_settings", model.getAttribute("smtpSource"));
        assertEquals(true, model.getAttribute("smtpConfigured"));
        assertEquals(true, model.getAttribute("smtpTestEnabled"));
    }

    @Test
    void settingsFallsBackToLegacyCompanySmtpSource() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(32L);
        company.setName("Tenant 32");
        company.setSmtpHost("legacy.example.com");
        company.setSmtpPort(587);
        company.setSmtpUser("legacy");
        company.setSmtpPassword("legacy-secret");

        CompanyDTO dto = new CompanyDTO();
        dto.setId(32L);
        dto.setName("Tenant 32");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        settings.setEnabled(true);

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.isConfigured(settings)).thenReturn(false);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("legacy_company");
        when(mailSettingsService.toDto(settings)).thenReturn(new com.printflow.dto.MailSettingsDTO());
        when(companyService.getCompanyById(32L)).thenReturn(dto);
        when(emailOutboxService.listForCompany(company, null, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        when(emailOutboxService.totalForCompany(company)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.SENT)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.FAILED)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.PENDING)).thenReturn(0L);

        Model model = new ExtendedModelMap();
        String view = controller.settings(null, null, null, null, null, 0, model);

        assertEquals("admin/settings/email", view);
        assertEquals("legacy_company", model.getAttribute("smtpSource"));
        assertEquals(true, model.getAttribute("smtpConfigured"));
        assertEquals(false, model.getAttribute("smtpTestEnabled"));
    }

    @Test
    void settingsClampsNegativeOutboxPageToZero() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(33L);
        company.setName("Tenant 33");
        CompanyDTO dto = new CompanyDTO();
        dto.setId(33L);
        dto.setName("Tenant 33");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.isConfigured(settings)).thenReturn(false);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("none");
        when(mailSettingsService.toDto(settings)).thenReturn(new com.printflow.dto.MailSettingsDTO());
        when(companyService.getCompanyById(33L)).thenReturn(dto);
        when(emailOutboxService.listForCompany(company, null, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        when(emailOutboxService.totalForCompany(company)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.SENT)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.FAILED)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.PENDING)).thenReturn(0L);

        Model model = new ExtendedModelMap();
        String view = controller.settings(null, null, null, null, null, -7, model);

        assertEquals("admin/settings/email", view);
        verify(emailOutboxService).listForCompany(company, null, 0, 20);
    }

    @Test
    void testEmailRejectsLegacySmtpSource() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(34L);
        MailSettings settings = new MailSettings();
        settings.setEnabled(true);

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("legacy_company");
        when(mailSettingsService.isConfigured(settings)).thenReturn(false);

        String result = controller.test("admin@printflow.test");

        assertEquals("redirect:/settings/email?errorKey=company.smtp.error.legacy_source", result);
        verify(emailService, never()).sendNow(any(), any(), any());
    }

    @Test
    void testEmailSendsWhenMailSettingsConfiguredAndEnabled() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(35L);
        company.setName("Tenant 35");
        MailSettings settings = new MailSettings();
        settings.setEnabled(true);
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("noreply@example.com");
        settings.setSmtpPasswordEnc("enc");

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("mail_settings");
        when(mailSettingsService.isConfigured(settings)).thenReturn(true);

        String result = controller.test("admin@printflow.test");

        assertEquals("redirect:/settings/email?successKey=company.smtp.test_sent", result);
        verify(emailService).sendNow(any(), eq(company), eq("smtp-test"));
    }

    @Test
    void testEmailTrimsRecipientBeforeSend() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(351L);
        company.setName("Tenant 351");
        MailSettings settings = new MailSettings();
        settings.setEnabled(true);
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("noreply@example.com");
        settings.setSmtpPasswordEnc("enc");

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("mail_settings");
        when(mailSettingsService.isConfigured(settings)).thenReturn(true);

        String result = controller.test("  admin@printflow.test  ");

        assertEquals("redirect:/settings/email?successKey=company.smtp.test_sent", result);
        verify(emailService).sendNow(argThat(msg -> "admin@printflow.test".equals(msg.getTo())), eq(company), eq("smtp-test"));
    }

    @Test
    void updateValidationErrorPopulatesSmtpStateForTemplate() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(36L);
        CompanyDTO dto = new CompanyDTO();
        dto.setId(36L);
        dto.setName("Tenant 36");
        MailSettings settings = new MailSettings();
        settings.setEnabled(true);

        when(contextService.currentCompany()).thenReturn(company);
        when(companyService.getCompanyById(36L)).thenReturn(dto);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("legacy_company");

        Model model = new ExtendedModelMap();
        String view = controller.update(
            true,
            "",
            null,
            "smtp-user",
            "",
            true,
            false,
            "invalid-email",
            "Tenant",
            model
        );

        assertEquals("admin/settings/email", view);
        assertEquals("legacy_company", model.getAttribute("smtpSource"));
        assertEquals(true, model.getAttribute("smtpConfigured"));
        assertEquals(false, model.getAttribute("smtpTestEnabled"));
    }

    @Test
    void settingsSanitizesNonKeyErrorAndSuccessValues() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(37L);
        company.setName("Tenant 37");
        CompanyDTO dto = new CompanyDTO();
        dto.setId(37L);
        dto.setName("Tenant 37");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("none");
        when(mailSettingsService.toDto(settings)).thenReturn(new com.printflow.dto.MailSettingsDTO());
        when(companyService.getCompanyById(37L)).thenReturn(dto);
        when(emailOutboxService.listForCompany(company, null, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        when(emailOutboxService.totalForCompany(company)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.SENT)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.FAILED)).thenReturn(0L);
        when(emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.PENDING)).thenReturn(0L);

        Model model = new ExtendedModelMap();
        controller.settings("  company.smtp.test_sent<script>  ", "  raw message  ", "  email.settings.saved!  ", null, null, 0, model);

        assertEquals(null, model.getAttribute("errorKey"));
        assertEquals(null, model.getAttribute("successKey"));
        assertEquals("raw message", model.getAttribute("errorMessage"));
    }

    @Test
    void cleanupFailedAllowsZeroDaysForImmediateCleanup() {
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        CompanyService companyService = mock(CompanyService.class);
        EmailService emailService = mock(EmailService.class);
        EmailOutboxService emailOutboxService = mock(EmailOutboxService.class);

        EmailSettingsController controller = new EmailSettingsController(
            contextService, mailSettingsService, companyService, emailService, emailOutboxService
        );
        Company company = new Company();
        company.setId(38L);
        when(contextService.currentCompany()).thenReturn(company);
        when(emailOutboxService.cleanupFailed(company, 0)).thenReturn(5L);

        String view = controller.cleanupFailed(0);

        assertEquals("redirect:/settings/email?successKey=email.outbox.cleanup_success&cleanupCount=5", view);
        verify(emailOutboxService).cleanupFailed(company, 0);
    }
}
