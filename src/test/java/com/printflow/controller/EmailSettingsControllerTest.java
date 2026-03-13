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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
