package com.printflow.controller;

import com.printflow.config.PaginationConfig;
import com.printflow.dto.TaskDTO;
import com.printflow.dto.TaskStatisticsDTO;
import com.printflow.entity.User;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.service.AuditLogService;
import com.printflow.service.DashboardService;
import com.printflow.service.FileStorageService;
import com.printflow.service.NotificationService;
import com.printflow.service.TaskService;
import com.printflow.service.TenantContextService;
import com.printflow.service.UserService;
import com.printflow.service.WorkOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerControllerTest {

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void myTasksInvalidStatusFallsBackToNullAndKeepsNormalizedPageSize() {
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        DashboardService dashboardService = mock(DashboardService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        NotificationService notificationService = mock(NotificationService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        WorkOrderItemRepository workOrderItemRepository = mock(WorkOrderItemRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        WorkerController controller = new WorkerController(
            workOrderService,
            taskService,
            userService,
            dashboardService,
            fileStorageService,
            notificationService,
            paginationConfig,
            workOrderItemRepository,
            tenantContextService,
            auditLogService
        );

        User user = new User();
        user.setId(44L);
        user.setUsername("worker");
        when(userService.findByUsername("worker")).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("worker", "n/a", Collections.emptyList())
        );

        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(taskService.getWorkerTaskStatistics(44L)).thenReturn(new TaskStatisticsDTO());
        when(taskService.getTasksByWorkerFiltered(eq(44L), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.myTasks(model, "bad-status", null, null, null, null, 0, null);

        assertEquals("worker/tasks/list", view);
        assertNull(model.getAttribute("status"));
        assertEquals(20, model.getAttribute("size"));
        assertEquals("worker.tasks.error.invalid_status", model.getAttribute("errorMessage"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskService).getTasksByWorkerFiltered(eq(44L), isNull(), isNull(), isNull(), isNull(), isNull(), pageableCaptor.capture());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void myTasksAcceptsValidLowercaseStatus() {
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        TaskService taskService = mock(TaskService.class);
        UserService userService = mock(UserService.class);
        DashboardService dashboardService = mock(DashboardService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        NotificationService notificationService = mock(NotificationService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        WorkOrderItemRepository workOrderItemRepository = mock(WorkOrderItemRepository.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        WorkerController controller = new WorkerController(
            workOrderService,
            taskService,
            userService,
            dashboardService,
            fileStorageService,
            notificationService,
            paginationConfig,
            workOrderItemRepository,
            tenantContextService,
            auditLogService
        );

        User user = new User();
        user.setId(55L);
        user.setUsername("worker2");
        when(userService.findByUsername("worker2")).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("worker2", "n/a", Collections.emptyList())
        );

        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(50)).thenReturn(50);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(taskService.getWorkerTaskStatistics(55L)).thenReturn(new TaskStatisticsDTO());

        Page<TaskDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(taskService.getTasksByWorkerFiltered(eq(55L), eq(TaskStatus.IN_PROGRESS), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(page);

        Model model = new ExtendedModelMap();
        String view = controller.myTasks(model, "in_progress", null, null, null, null, 0, 50);

        assertEquals("worker/tasks/list", view);
        assertEquals("IN_PROGRESS", model.getAttribute("status"));
        assertEquals(50, model.getAttribute("size"));
        assertNull(model.getAttribute("errorMessage"));

        verify(taskService).getTasksByWorkerFiltered(eq(55L), eq(TaskStatus.IN_PROGRESS), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }
}
