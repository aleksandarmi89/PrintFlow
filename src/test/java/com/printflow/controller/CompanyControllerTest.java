package com.printflow.controller;

import com.printflow.config.PaginationConfig;
import com.printflow.dto.CompanyDTO;
import com.printflow.service.AuditLogService;
import com.printflow.service.CompanyBrandingService;
import com.printflow.service.CompanyService;
import com.printflow.service.TenantContextService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class CompanyControllerTest {

    @Test
    void updateCompanyPreservesBillingOverrideForNonSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );

        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        CompanyDTO existing = new CompanyDTO();
        existing.setBillingOverrideActive(true);
        LocalDateTime until = LocalDateTime.now().plusDays(10);
        existing.setBillingOverrideUntil(until);
        when(companyService.getCompanyById(11L)).thenReturn(existing);

        CompanyDTO incoming = new CompanyDTO();
        incoming.setName("Updated Name");
        incoming.setActive(true);
        incoming.setBillingOverrideActive(false);
        incoming.setBillingOverrideUntil(null);
        Model model = new ExtendedModelMap();

        String view = controller.updateCompany(11L, incoming, null, model);

        assertEquals("redirect:/admin/companies", view);
        verify(companyService).getCompanyById(11L);
        verify(companyService).updateCompany(eq(11L), any(CompanyDTO.class));
        assertEquals(true, incoming.isBillingOverrideActive());
        assertEquals(until, incoming.getBillingOverrideUntil());
    }

    @Test
    void disableCompanyIsForbiddenForNonSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );

        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        Model model = new ExtendedModelMap();

        String view = controller.disableCompany(22L, model);

        assertEquals("redirect:/admin/companies", view);
        verifyNoInteractions(companyService);
    }

    @Test
    void enableCompanyIsForbiddenForNonSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );

        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        Model model = new ExtendedModelMap();

        String view = controller.enableCompany(23L, model);

        assertEquals("redirect:/admin/companies", view);
        verifyNoInteractions(companyService);
    }

    @Test
    void disableCompanyCallsServiceForSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );

        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.disableCompany(24L, model);

        assertEquals("redirect:/admin/companies", view);
        verify(companyService).disableCompany(24L);
    }

    @Test
    void enableCompanyCallsServiceForSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );

        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.enableCompany(25L, model);

        assertEquals("redirect:/admin/companies", view);
        verify(companyService).enableCompany(25L);
    }

    @Test
    void createCompanyMapsDuplicateNameErrorToMessageKey() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );
        CompanyDTO incoming = new CompanyDTO();
        incoming.setName("Acme");
        Model model = new ExtendedModelMap();

        doThrow(new RuntimeException("Company name already exists"))
            .when(companyService).createCompany(any(CompanyDTO.class));

        String view = controller.createCompany(incoming, model);

        assertEquals("admin/companies/create", view);
        assertEquals("admin.companies.error.name_exists", model.getAttribute("errorMessage"));
        assertTrue(model.containsAttribute("company"));
    }

    @Test
    void createCompanyMapsTrimmedDuplicateNameErrorToMessageKey() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );
        CompanyDTO incoming = new CompanyDTO();
        incoming.setName("Acme");
        Model model = new ExtendedModelMap();

        doThrow(new RuntimeException("  Company name already exists  "))
            .when(companyService).createCompany(any(CompanyDTO.class));

        String view = controller.createCompany(incoming, model);

        assertEquals("admin/companies/create", view);
        assertEquals("admin.companies.error.name_exists", model.getAttribute("errorMessage"));
    }

    @Test
    void updateCompanyMapsTrimmedLogoErrorToMessageKey() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService,
            paginationConfig,
            brandingService,
            tenantContextService,
            auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        doThrow(new RuntimeException("  Logo must be PNG, JPG or SVG  "))
            .when(companyService).updateCompany(eq(41L), any(CompanyDTO.class));

        CompanyDTO incoming = new CompanyDTO();
        incoming.setName("Acme");
        Model model = new ExtendedModelMap();

        String view = controller.updateCompany(41L, incoming, null, model);

        assertEquals("admin/companies/edit", view);
        assertEquals("admin.companies.error.logo_type", model.getAttribute("errorMessage"));
    }

    @Test
    void activateProTrialIsForbiddenForNonSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        Model model = new ExtendedModelMap();

        String view = controller.activateProTrial(31L, model);

        assertEquals("redirect:/admin/companies/edit/31", view);
        verifyNoInteractions(companyService, auditLogService);
    }

    @Test
    void activateProTrialCallsServiceAndAuditForSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.activateProTrial(32L, model);

        assertEquals("redirect:/admin/companies/edit/32", view);
        verify(companyService).activateProTrial(32L, 30);
        verify(auditLogService).log(eq(com.printflow.entity.enums.AuditAction.UPDATE), eq("Company"), eq(32L),
            eq(null), eq("trial_pro_30"), eq("Activated PRO trial for 30 days"));
    }

    @Test
    void activateProOneYearCallsServiceAndAuditForSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.activateProOneYear(33L, model);

        assertEquals("redirect:/admin/companies/edit/33", view);
        verify(companyService).activateProOverrideForDays(33L, 365);
        verify(auditLogService).log(eq(com.printflow.entity.enums.AuditAction.UPDATE), eq("Company"), eq(33L),
            eq(null), eq("override_pro_365"), eq("Activated PRO override for 1 year"));
    }

    @Test
    void setBillingOverrideRejectsInvalidDateFormat() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.setBillingOverride(34L, true, "2026/03/13", model);

        assertEquals("redirect:/admin/companies/edit/34", view);
        verifyNoInteractions(companyService, auditLogService);
    }

    @Test
    void setBillingOverrideTrimsDateBeforeParsing() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.setBillingOverride(34L, true, " 2026-03-13T10:15:00 ", model);

        assertEquals("redirect:/admin/companies/edit/34", view);
        verify(companyService).setBillingOverride(eq(34L), eq(true), eq(LocalDateTime.parse("2026-03-13T10:15:00")));
    }

    @Test
    void setBillingOverrideIgnoresInvalidUntilWhenDeactivatingOverride() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.setBillingOverride(35L, false, "not-a-date", model);

        assertEquals("redirect:/admin/companies/edit/35", view);
        verify(companyService).setBillingOverride(eq(35L), eq(false), isNull());
    }

    @Test
    void listCompaniesTrimsOverrideFilterBeforeParsing() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(companyService.getCompanies(isNull(), isNull(), eq(true), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies(null, null, "  on  ", 0, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals("on", model.getAttribute("override"));
        verify(companyService).getCompanies(isNull(), isNull(), eq(true), any());
    }

    @Test
    void listCompaniesIgnoresUnknownOverrideFilterValues() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(companyService.getCompanies(isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies(null, null, "maybe", 0, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals(null, model.getAttribute("override"));
        verify(companyService).getCompanies(isNull(), isNull(), isNull(), any());
    }

    @Test
    void listCompaniesTrimsUnknownOverrideBeforeReturningToModel() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(companyService.getCompanies(isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies(null, null, "  maybe  ", 0, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals(null, model.getAttribute("override"));
        verify(companyService).getCompanies(isNull(), isNull(), isNull(), any());
    }

    @Test
    void listCompaniesTrimsPlanFilterBeforeParsing() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(companyService.getCompanies(isNull(), eq(com.printflow.entity.enums.PlanTier.PRO), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies(null, "  pro  ", null, 0, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals("pro", model.getAttribute("plan"));
        verify(companyService).getCompanies(isNull(), eq(com.printflow.entity.enums.PlanTier.PRO), isNull(), any());
    }

    @Test
    void listCompaniesDropsUnknownPlanFromModelAndDoesNotApplyFilter() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(companyService.getCompanies(isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies(null, "  enterprise  ", null, 0, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals(null, model.getAttribute("plan"));
        verify(companyService).getCompanies(isNull(), isNull(), isNull(), any());
    }

    @Test
    void listCompaniesTrimsSearchBeforeQueryAndModel() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(companyService.getCompanies(eq("Acme"), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies("  Acme  ", null, null, 0, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals("Acme", model.getAttribute("search"));
        verify(companyService).getCompanies(eq("Acme"), isNull(), isNull(), any());
    }

    @Test
    void listCompaniesConvertsBlankSearchToNull() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(companyService.getCompanies(isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies("   ", null, null, 0, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals(null, model.getAttribute("search"));
        verify(companyService).getCompanies(isNull(), isNull(), isNull(), any());
    }

    @Test
    void listCompaniesRefetchKeepsNormalizedFiltersWhenPageOutOfRange() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(paginationConfig.normalizePage(5)).thenReturn(5);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(companyService.getCompanies(eq("Acme"), eq(com.printflow.entity.enums.PlanTier.PRO), eq(false), any()))
            .thenAnswer(inv -> calls.getAndIncrement() == 0
                ? new PageImpl<>(List.of(), PageRequest.of(5, 20), 1)
                : new PageImpl<>(List.of(), PageRequest.of(0, 20), 1));

        Model model = new ExtendedModelMap();
        String view = controller.listCompanies("  Acme  ", "  PRO  ", "  off  ", 5, null, model);

        assertEquals("admin/companies/list", view);
        assertEquals("Acme", model.getAttribute("search"));
        assertEquals("PRO", model.getAttribute("plan"));
        assertEquals("off", model.getAttribute("override"));
        assertEquals(0, model.getAttribute("currentPage"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(companyService, times(2))
            .getCompanies(eq("Acme"), eq(com.printflow.entity.enums.PlanTier.PRO), eq(false), pageableCaptor.capture());
        List<Pageable> captured = pageableCaptor.getAllValues();
        assertEquals(5, captured.get(0).getPageNumber());
        assertEquals(0, captured.get(1).getPageNumber());
    }

    @Test
    void sendCompanyMessageIsForbiddenForNonSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        Model model = new ExtendedModelMap();

        String view = controller.sendCompanyMessage(
            55L,
            "billing@tenant.test",
            "Invoice",
            "Body",
            "invoice",
            "INV-001",
            "1500 RSD",
            "2026-03-31",
            model
        );

        assertEquals("redirect:/admin/companies/edit/55", view);
        verifyNoInteractions(companyService);
    }

    @Test
    void sendCompanyMessageCallsServiceWithInvoiceFieldsForSuperAdmin() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.sendCompanyMessage(
            56L,
            "billing@tenant.test",
            "Invoice",
            "Body",
            "invoice",
            "INV-2026-0001",
            "1250.00 RSD",
            "2026-03-31",
            model
        );

        assertEquals("redirect:/admin/companies/edit/56", view);
        verify(companyService).sendSuperAdminCompanyMessage(
            56L,
            "billing@tenant.test",
            "Invoice",
            "Body",
            "invoice",
            "INV-2026-0001",
            "1250.00 RSD",
            "2026-03-31"
        );
    }

    @Test
    void sendCompanyMessageMapsEmailServiceUnavailableErrorToKey() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        doThrow(new RuntimeException("Email service is not configured"))
            .when(companyService)
            .sendSuperAdminCompanyMessage(any(), any(), any(), any(), any(), any(), any(), any());
        Model model = new ExtendedModelMap();

        String view = controller.sendCompanyMessage(
            57L,
            "billing@tenant.test",
            "Notice",
            "Body",
            "general",
            null,
            null,
            null,
            model
        );

        assertEquals("redirect:/admin/companies/edit/57", view);
        assertEquals("admin.companies.message.error.email_service_unavailable", model.getAttribute("errorMessage"));
    }

    @Test
    void sendCompanyMessageMapsInvalidRecipientErrorToKey() {
        CompanyService companyService = mock(CompanyService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        CompanyBrandingService brandingService = mock(CompanyBrandingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        CompanyController controller = new CompanyController(
            companyService, paginationConfig, brandingService, tenantContextService, auditLogService
        );
        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        doThrow(new RuntimeException("Company recipient email is invalid"))
            .when(companyService)
            .sendSuperAdminCompanyMessage(any(), any(), any(), any(), any(), any(), any(), any());
        Model model = new ExtendedModelMap();

        String view = controller.sendCompanyMessage(
            58L,
            "bad-email",
            "Notice",
            "Body",
            "general",
            null,
            null,
            null,
            model
        );

        assertEquals("redirect:/admin/companies/edit/58", view);
        assertEquals("admin.companies.message.error.recipient_invalid", model.getAttribute("errorMessage"));
    }
}
