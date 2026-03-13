package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
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
import static org.mockito.Mockito.when;

class AdminCompanySettingsControllerTest {

    @Test
    void updateUsesI18nSuccessKeyOnSuccessfulSave() {
        CompanyService companyService = mock(CompanyService.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        CurrentContextService contextService = mock(CurrentContextService.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, tenantContextService, false
        );
        Company company = new Company();
        company.setId(15L);
        company.setName("Tenant 15");
        when(contextService.currentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(mailSettingsService.getOrCreate(company)).thenReturn(new MailSettings());

        Model model = new ExtendedModelMap();
        String view = controller.update(
            null, "ignored", "team@example.com", "+38111111", "Main 1", "example.com", "#111111",
            "RSD", null, null, null, null, null, null, model
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
        TenantContextService tenantContextService = mock(TenantContextService.class);

        AdminCompanySettingsController controller = new AdminCompanySettingsController(
            companyService, brandingService, contextService, mailSettingsService, tenantContextService, false
        );
        Company company = new Company();
        company.setId(16L);
        company.setName("Tenant 16");
        MailSettings settings = new MailSettings();
        settings.setCompany(company);
        when(contextService.currentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(mailSettingsService.getOrCreate(company)).thenReturn(settings);
        doThrow(new RuntimeException()).when(companyService).updateCompany(eq(16L), any());

        Model model = new ExtendedModelMap();
        String view = controller.update(
            null, "ignored", "team@example.com", "+38111111", "Main 1", "example.com", "#111111",
            "RSD", null, null, null, null, null, null, model
        );

        assertEquals("admin/company/settings", view);
        assertEquals("company.settings.update_failed", model.getAttribute("errorMessage"));
    }
}
