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
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
