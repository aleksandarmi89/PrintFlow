package com.printflow.controller;

import com.printflow.config.PaginationConfig;
import com.printflow.dto.CompanyDTO;
import com.printflow.service.AuditLogService;
import com.printflow.service.CompanyBrandingService;
import com.printflow.service.CompanyService;
import com.printflow.service.TenantContextService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
