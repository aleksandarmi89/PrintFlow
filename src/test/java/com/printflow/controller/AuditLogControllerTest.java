package com.printflow.controller;

import com.printflow.config.PaginationConfig;
import com.printflow.entity.AuditLog;
import com.printflow.entity.enums.AuditAction;
import com.printflow.service.AuditLogService;
import com.printflow.service.CompanyService;
import com.printflow.service.TenantContextService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogControllerTest {

    @Test
    void listParsesLowercaseActionFilter() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompanyService companyService = mock(CompanyService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);

        AuditLogController controller = new AuditLogController(auditLogService, companyService, tenantContextService, paginationConfig);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(tenantContextService.requireCompanyId()).thenReturn(11L);
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(auditLogService.searchAuditLogs(eq(11L), eq(AuditAction.CREATE), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(new AuditLog()), PageRequest.of(0, 20), 1));

        Model model = new ExtendedModelMap();
        String view = controller.list(null, "create", null, null, null, null, null, null, model);

        assertEquals("admin/audit-logs/list", view);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogService).searchAuditLogs(eq(11L), eq(AuditAction.CREATE), isNull(), isNull(), isNull(), isNull(), pageableCaptor.capture());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void listIgnoresInvalidActionFilter() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompanyService companyService = mock(CompanyService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);

        AuditLogController controller = new AuditLogController(auditLogService, companyService, tenantContextService, paginationConfig);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(tenantContextService.requireCompanyId()).thenReturn(12L);
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(50)).thenReturn(50);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(auditLogService.searchAuditLogs(eq(12L), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        Model model = new ExtendedModelMap();
        String view = controller.list(null, "bad-action", null, null, null, null, 1, 50, model);

        assertEquals("admin/audit-logs/list", view);
        verify(auditLogService).searchAuditLogs(eq(12L), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void exportNormalizesPageAndSizeAndParsesActionCaseInsensitive() throws Exception {
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompanyService companyService = mock(CompanyService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);

        AuditLogController controller = new AuditLogController(auditLogService, companyService, tenantContextService, paginationConfig);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(tenantContextService.requireCompanyId()).thenReturn(13L);
        when(paginationConfig.normalizePage(-2)).thenReturn(0);
        when(paginationConfig.normalizeSize(5000)).thenReturn(200);
        when(auditLogService.searchAuditLogs(eq(13L), eq(AuditAction.DELETE), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.export(null, " delete ", null, null, null, null, -2, 5000, response);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogService).searchAuditLogs(eq(13L), eq(AuditAction.DELETE), isNull(), isNull(), isNull(), isNull(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(200, pageableCaptor.getValue().getPageSize());
        assertTrue(response.getContentType().startsWith("text/csv"));
    }

    @Test
    void listTrimsQueryAndEntityTypeBeforeSearchAndModel() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompanyService companyService = mock(CompanyService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);

        AuditLogController controller = new AuditLogController(auditLogService, companyService, tenantContextService, paginationConfig);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(tenantContextService.requireCompanyId()).thenReturn(14L);
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(20)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(auditLogService.searchAuditLogs(eq(14L), eq(AuditAction.UPDATE), eq("john"), eq(3L), eq(9L), eq("WorkOrder"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.list(null, " update ", "  john  ", 3L, 9L, "  WorkOrder  ", 1, 20, model);

        assertEquals("admin/audit-logs/list", view);
        assertEquals("john", model.getAttribute("query"));
        assertEquals("WorkOrder", model.getAttribute("entityType"));
        assertEquals("update", model.getAttribute("action"));
        verify(auditLogService).searchAuditLogs(eq(14L), eq(AuditAction.UPDATE), eq("john"), eq(3L), eq(9L), eq("WorkOrder"), any(Pageable.class));
    }
}
