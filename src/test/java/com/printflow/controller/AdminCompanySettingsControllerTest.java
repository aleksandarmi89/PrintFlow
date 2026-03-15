package com.printflow.controller;

import com.printflow.dto.CompanyDTO;
import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.entity.User;
import com.printflow.repository.UserRepository;
import com.printflow.service.CompanyBrandingService;
import com.printflow.service.CompanyService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.MailSettingsService;
import com.printflow.service.TenantContextService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AdminCompanySettingsControllerTest {

    @Test
    void updateUsesI18nSuccessKeyOnSuccessfulSave() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );
        Company company = new Company();
        company.setId(15L);
        company.setName("Tenant 15");
        when(contextService.currentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(mailSettingsService.getOrCreate(company)).thenReturn(new MailSettings());

        Model model = new ExtendedModelMap();
        String view = controller.update(
            null, "ignored", "team@example.com", "+38111111", "Main 1", "example.com",
            null, null, null, null, null, null,
            "#111111", "RSD", null, null, null, null, null, null, model
        );

        assertEquals("redirect:/admin/company", view);
        assertEquals("company.settings.updated", model.getAttribute("successMessage"));
        verify(companyService).updateCompany(eq(15L), any());
    }

    @Test
    void updateUsesFallbackErrorKeyWhenExceptionHasNoMessage() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );
        Company company = new Company();
        company.setId(16L);
        company.setName("Tenant 16");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        when(contextService.currentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("none");
        doThrow(new RuntimeException()).when(companyService).updateCompany(eq(16L), any());

        Model model = new ExtendedModelMap();
        String view = controller.update(
            null, "ignored", "team@example.com", "+38111111", "Main 1", "example.com",
            null, null, null, null, null, null,
            "#111111", "RSD", null, null, null, null, null, null, model
        );

        assertEquals("admin/company/settings", view);
        assertEquals("company.settings.update_failed", model.getAttribute("errorMessage"));
        assertEquals("none", model.getAttribute("smtpSource"));
        assertEquals(false, model.getAttribute("smtpConfigured"));
    }

    @Test
    void settingsExposesMailSettingsSourceWhenConfigured() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );
        Company company = new Company();
        company.setId(17L);
        company.setName("Tenant 17");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("noreply@example.com");
        settings.setSmtpPasswordEnc("enc");
        CompanyDTO dto = new CompanyDTO();
        dto.setId(17L);
        dto.setName("Tenant 17");

        when(contextService.currentCompany()).thenReturn(company);
        User currentUser = new User();
        currentUser.setUsername("superadmin");
        when(contextService.currentUser()).thenReturn(currentUser);
        when(companyService.getCompanyById(17L)).thenReturn(dto);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.isConfigured(settings)).thenReturn(true);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("mail_settings");

        Model model = new ExtendedModelMap();
        String view = controller.settings(null, null, null, null, model);

        assertEquals("admin/company/settings", view);
        assertEquals("mail_settings", model.getAttribute("smtpSource"));
        assertEquals(true, model.getAttribute("smtpConfigured"));
    }

    @Test
    void settingsExposesLegacySourceWhenMailSettingsMissingButLegacyExists() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );
        Company company = new Company();
        company.setId(18L);
        company.setName("Tenant 18");
        company.setSmtpHost("legacy.example.com");
        company.setSmtpPort(587);
        company.setSmtpUser("legacy-user");
        company.setSmtpPassword("legacy-pass");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        CompanyDTO dto = new CompanyDTO();
        dto.setId(18L);
        dto.setName("Tenant 18");

        when(contextService.currentCompany()).thenReturn(company);
        User currentUser = new User();
        currentUser.setUsername("superadmin");
        when(contextService.currentUser()).thenReturn(currentUser);
        when(companyService.getCompanyById(18L)).thenReturn(dto);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.isConfigured(settings)).thenReturn(false);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("legacy_company");

        Model model = new ExtendedModelMap();
        String view = controller.settings(null, null, null, null, model);

        assertEquals("admin/company/settings", view);
        assertEquals("legacy_company", model.getAttribute("smtpSource"));
        assertEquals(true, model.getAttribute("smtpConfigured"));
    }

    @Test
    void updateValidationErrorKeepsSmtpStatusState() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, true
        );
        Company company = new Company();
        company.setId(19L);
        company.setName("Tenant 19");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        CompanyDTO dto = new CompanyDTO();
        dto.setId(19L);
        dto.setName("Tenant 19");

        when(contextService.currentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("legacy_company");

        Model model = new ExtendedModelMap();
        String view = controller.update(
            null, "ignored", "team@example.com", "+38111111", "Main 1", "example.com",
            null, null, null, null, null, null,
            "#111111", "RSD", "smtp.example.com", 587, "", "", true, null, model
        );

        assertEquals("admin/company/settings", view);
        assertEquals("legacy_company", model.getAttribute("smtpSource"));
        assertEquals(true, model.getAttribute("smtpConfigured"));
        assertEquals(true, model.getAttribute("smtpFallbackEnabled"));
        assertEquals("company.smtp.error.user_required", model.getAttribute("errorKey"));
    }

    @Test
    void settingsTrimsErrorAndSuccessKeys() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );
        Company company = new Company();
        company.setId(20L);
        company.setName("Tenant 20");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        CompanyDTO dto = new CompanyDTO();
        dto.setId(20L);
        dto.setName("Tenant 20");

        when(contextService.currentCompany()).thenReturn(company);
        User currentUser = new User();
        currentUser.setUsername("superadmin");
        when(contextService.currentUser()).thenReturn(currentUser);
        when(companyService.getCompanyById(20L)).thenReturn(dto);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.resolveSmtpSource(company, settings)).thenReturn("none");

        Model model = new ExtendedModelMap();
        String view = controller.settings("  company.smtp.error.invalid_email  ", "  company.smtp.test_sent  ", "  company.platform_contact.error.name_required  ", "  company.platform_contact.updated  ", model);

        assertEquals("admin/company/settings", view);
        assertEquals("company.smtp.error.invalid_email", model.getAttribute("errorKey"));
        assertEquals("company.smtp.test_sent", model.getAttribute("successKey"));
        assertEquals("company.platform_contact.error.name_required", model.getAttribute("platformErrorKey"));
        assertEquals("company.platform_contact.updated", model.getAttribute("platformSuccessKey"));
    }

    @Test
    void testSmtpTrimsRecipientBeforeSend() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );
        Company company = new Company();
        company.setId(21L);
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("user");
        settings.setSmtpPasswordEnc("enc");

        when(contextService.currentCompany()).thenReturn(company);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        when(mailSettingsService.isConfiguredWithLegacyFallback(company, settings)).thenReturn(true);

        String view = controller.testSmtp("  test@example.com  ", new ExtendedModelMap());

        assertEquals("redirect:/admin/company?successKey=company.smtp.test_sent", view);
        verify(companyService).sendTestSmtpEmail(21L, "test@example.com");
        verify(companyService, never()).sendTestSmtpEmail(21L, "  test@example.com  ");
    }

    @Test
    void platformContactRejectsNonSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );

        String view = controller.updatePlatformContact("Owner", "owner@example.com", "+381");
        assertEquals("redirect:/admin/company?platformErrorKey=company.platform_contact.error.forbidden", view);
        verify(userRepository, never()).save(any());
    }

    @Test
    void platformContactUpdatesCurrentSuperAdminProfile() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        User user = new User();
        user.setUsername("superadmin");
        when(contextService.currentUser()).thenReturn(user);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, userRepository, tenantContextService, false
        );

        String view = controller.updatePlatformContact("Owner Name", "owner@example.com", "+381 11 123");

        assertEquals("redirect:/admin/company?platformSuccessKey=company.platform_contact.updated", view);
        verify(userRepository).save(user);
        assertEquals("Owner", user.getFirstName());
        assertEquals("Name", user.getLastName());
        assertEquals("owner@example.com", user.getEmail());
        assertEquals("+381 11 123", user.getPhone());
    }
}
