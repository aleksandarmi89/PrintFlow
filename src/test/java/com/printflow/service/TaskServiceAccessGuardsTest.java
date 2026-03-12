package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.Task;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.CommentRepository;
import com.printflow.repository.TaskActivityRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.TimeEntryRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyLong;

class TaskServiceAccessGuardsTest {

    @Test
    void nullInputsReturnFalseWithoutRepositoryCalls() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TaskActivityRepository taskActivityRepository = mock(TaskActivityRepository.class);
        TimeEntryRepository timeEntryRepository = mock(TimeEntryRepository.class);
        CommentRepository commentRepository = mock(CommentRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        TaskService service = new TaskService(
            taskRepository,
            userRepository,
            workOrderRepository,
            taskActivityRepository,
            timeEntryRepository,
            commentRepository,
            fileStorageService,
            tenantGuard,
            notificationService,
            auditLogService,
            2000
        );

        assertFalse(service.isTaskAssignedToUser(null, 1L));
        assertFalse(service.isTaskAssignedToUser(1L, null));
        assertFalse(service.canUserAccessTask(null, 1L));
        assertFalse(service.canUserAccessTask(1L, null));
        assertFalse(service.isTaskAvailable(null));

        verify(taskRepository, never()).existsByIdAndAssignedToId(anyLong(), anyLong());
    }

    @Test
    void canUserAccessTaskAllowsDirectOrWorkOrderAssignment() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TaskActivityRepository taskActivityRepository = mock(TaskActivityRepository.class);
        TimeEntryRepository timeEntryRepository = mock(TimeEntryRepository.class);
        CommentRepository commentRepository = mock(CommentRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        TaskService service = new TaskService(
            taskRepository,
            userRepository,
            workOrderRepository,
            taskActivityRepository,
            timeEntryRepository,
            commentRepository,
            fileStorageService,
            tenantGuard,
            notificationService,
            auditLogService,
            2000
        );

        Company company = new Company();
        company.setId(1L);
        User user = new User();
        user.setId(77L);
        user.setCompany(company);

        Task taskDirect = new Task();
        taskDirect.setId(10L);
        taskDirect.setCompany(company);
        taskDirect.setAssignedTo(user);

        Task taskViaOrder = new Task();
        taskViaOrder.setId(11L);
        taskViaOrder.setCompany(company);
        WorkOrder order = new WorkOrder();
        order.setAssignedTo(user);
        taskViaOrder.setWorkOrder(order);

        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(10L, 1L)).thenReturn(Optional.of(taskDirect));
        when(taskRepository.findByIdAndCompany_Id(11L, 1L)).thenReturn(Optional.of(taskViaOrder));
        when(userRepository.findByIdAndCompany_Id(77L, 1L)).thenReturn(Optional.of(user));

        assertTrue(service.canUserAccessTask(10L, 77L));
        assertTrue(service.canUserAccessTask(11L, 77L));
    }

    @Test
    void isTaskAvailableRequiresPendingAndUnassigned() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TaskActivityRepository taskActivityRepository = mock(TaskActivityRepository.class);
        TimeEntryRepository timeEntryRepository = mock(TimeEntryRepository.class);
        CommentRepository commentRepository = mock(CommentRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        TaskService service = new TaskService(
            taskRepository,
            userRepository,
            workOrderRepository,
            taskActivityRepository,
            timeEntryRepository,
            commentRepository,
            fileStorageService,
            tenantGuard,
            notificationService,
            auditLogService,
            2000
        );

        Task pending = new Task();
        pending.setStatus(TaskStatus.PENDING);
        pending.setAssignedTo(null);

        Task assigned = new Task();
        assigned.setStatus(TaskStatus.PENDING);
        assigned.setAssignedTo(new User());

        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(20L, 1L)).thenReturn(Optional.of(pending));
        when(taskRepository.findByIdAndCompany_Id(21L, 1L)).thenReturn(Optional.of(assigned));

        assertTrue(service.isTaskAvailable(20L));
        assertFalse(service.isTaskAvailable(21L));
    }
}
