package com.printflow.controller;

import com.printflow.config.PaginationConfig;
import com.printflow.entity.Company;
import com.printflow.entity.PublicOrderRequest;
import com.printflow.entity.enums.AuditAction;
import com.printflow.entity.enums.PublicOrderRequestStatus;
import com.printflow.repository.TaskRepository;
import com.printflow.service.AuditLogService;
import com.printflow.service.EmailService;
import com.printflow.service.PublicOrderRequestConversionService;
import com.printflow.service.PublicOrderRequestService;
import com.printflow.service.TaskService;
import com.printflow.service.TenantContextService;
import com.printflow.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPublicOrderRequestControllerTest {

    @Test
    void updateStatusRejectsInvalidValueAndSkipsUpdate() {
        PublicOrderRequestService requestService = mock(PublicOrderRequestService.class);
        PublicOrderRequestConversionService conversionService = mock(PublicOrderRequestConversionService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        EmailService emailService = mock(EmailService.class);

        AdminPublicOrderRequestController controller = new AdminPublicOrderRequestController(
            requestService, conversionService, paginationConfig, auditLogService, tenantContextService,
            taskService, userService, taskRepository, emailService
        );

        PublicOrderRequest request = new PublicOrderRequest();
        request.setStatus(PublicOrderRequestStatus.NEW);
        when(requestService.getForCurrentTenant(10L)).thenReturn(request);

        Model model = new ExtendedModelMap();
        String view = controller.updateStatus(10L, "not-valid", model);

        assertEquals("redirect:/admin/public-requests/10", view);
        verify(requestService, never()).updateStatus(eq(10L), any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateStatusAcceptsLowercaseStatus() {
        PublicOrderRequestService requestService = mock(PublicOrderRequestService.class);
        PublicOrderRequestConversionService conversionService = mock(PublicOrderRequestConversionService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        EmailService emailService = mock(EmailService.class);

        AdminPublicOrderRequestController controller = new AdminPublicOrderRequestController(
            requestService, conversionService, paginationConfig, auditLogService, tenantContextService,
            taskService, userService, taskRepository, emailService
        );

        PublicOrderRequest request = new PublicOrderRequest();
        request.setStatus(PublicOrderRequestStatus.NEW);
        when(requestService.getForCurrentTenant(11L)).thenReturn(request);
        when(tenantContextService.getCurrentCompany()).thenReturn(new Company());

        Model model = new ExtendedModelMap();
        String view = controller.updateStatus(11L, "converted", model);

        assertEquals("redirect:/admin/public-requests/11", view);
        verify(requestService).updateStatus(11L, PublicOrderRequestStatus.CONVERTED);
        verify(auditLogService).log(
            eq(AuditAction.STATUS_CHANGE),
            eq("PublicOrderRequest"),
            eq(11L),
            eq("NEW"),
            eq("CONVERTED"),
            eq("Public request status changed by admin"),
            any(Company.class)
        );
    }

    @Test
    void listNormalizesStatusAndSearchFilters() {
        PublicOrderRequestService requestService = mock(PublicOrderRequestService.class);
        PublicOrderRequestConversionService conversionService = mock(PublicOrderRequestConversionService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        EmailService emailService = mock(EmailService.class);

        AdminPublicOrderRequestController controller = new AdminPublicOrderRequestController(
            requestService, conversionService, paginationConfig, auditLogService, tenantContextService,
            taskService, userService, taskRepository, emailService
        );

        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(requestService.listForCurrentTenant(eq(PublicOrderRequestStatus.CONVERTED), eq("acme"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0));

        MockHttpSession session = new MockHttpSession();
        Model model = new ExtendedModelMap();
        String view = controller.list(" converted ", "  acme  ", false, 0, null, session, model);

        assertEquals("admin/public-requests/list", view);
        assertEquals("CONVERTED", model.getAttribute("statusFilter"));
        assertEquals("acme", model.getAttribute("search"));
        assertEquals("CONVERTED", session.getAttribute("publicRequests.statusFilter"));
        assertEquals("acme", session.getAttribute("publicRequests.searchFilter"));
    }

    @Test
    void listDropsInvalidStatusStoredInSession() {
        PublicOrderRequestService requestService = mock(PublicOrderRequestService.class);
        PublicOrderRequestConversionService conversionService = mock(PublicOrderRequestConversionService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        EmailService emailService = mock(EmailService.class);

        AdminPublicOrderRequestController controller = new AdminPublicOrderRequestController(
            requestService, conversionService, paginationConfig, auditLogService, tenantContextService,
            taskService, userService, taskRepository, emailService
        );

        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(50)).thenReturn(50);
        when(requestService.listForCurrentTenant(isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 50), 0));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("publicRequests.statusFilter", "bad-status");
        Model model = new ExtendedModelMap();

        String view = controller.list(null, null, false, 0, 50, session, model);

        assertEquals("admin/public-requests/list", view);
        assertNull(model.getAttribute("statusFilter"));
        assertNull(session.getAttribute("publicRequests.statusFilter"));
    }

    @Test
    void bulkActionNormalizesActionValue() {
        PublicOrderRequestService requestService = mock(PublicOrderRequestService.class);
        PublicOrderRequestConversionService conversionService = mock(PublicOrderRequestConversionService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        EmailService emailService = mock(EmailService.class);

        AdminPublicOrderRequestController controller = new AdminPublicOrderRequestController(
            requestService, conversionService, paginationConfig, auditLogService, tenantContextService,
            taskService, userService, taskRepository, emailService
        );

        Model model = new ExtendedModelMap();
        String view = controller.bulkAction(List.of(21L), " CONVERT ", model);

        assertEquals("redirect:/admin/public-requests", view);
        verify(conversionService).getOrConvertToOrder(21L);
    }

    @Test
    void bulkActionRejectsUnknownActionAsNone() {
        PublicOrderRequestService requestService = mock(PublicOrderRequestService.class);
        PublicOrderRequestConversionService conversionService = mock(PublicOrderRequestConversionService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        EmailService emailService = mock(EmailService.class);

        AdminPublicOrderRequestController controller = new AdminPublicOrderRequestController(
            requestService, conversionService, paginationConfig, auditLogService, tenantContextService,
            taskService, userService, taskRepository, emailService
        );

        Model model = new ExtendedModelMap();
        String view = controller.bulkAction(List.of(22L), "bad-action", model);

        assertEquals("redirect:/admin/public-requests", view);
        verify(conversionService, never()).getOrConvertToOrder(22L);
    }
}
