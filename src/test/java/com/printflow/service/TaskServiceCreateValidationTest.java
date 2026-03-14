package com.printflow.service;

import com.printflow.dto.TaskDTO;
import com.printflow.entity.Company;
import com.printflow.entity.Task;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.repository.CommentRepository;
import com.printflow.repository.TaskActivityRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.TimeEntryRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceCreateValidationTest {

    @Test
    void createTask_rejectsUnknownCreator() {
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

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.getCurrentCompany()).thenReturn(company);
        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(userRepository.findByIdAndCompany_Id(999L, 1L)).thenReturn(Optional.empty());

        TaskDTO dto = new TaskDTO();
        dto.setTitle("Task");

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.createTask(dto, null, null, 999L));
        assertEquals("User not found", ex.getMessage());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void createTask_trimsTitleBeforePersist() {
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

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.getCurrentCompany()).thenReturn(company);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            task.setId(42L);
            return task;
        });

        TaskDTO dto = new TaskDTO();
        dto.setTitle("  New Task  ");

        service.createTask(dto, null, null, null);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals("New Task", captor.getValue().getTitle());
    }

    @Test
    void createTask_rejectsMissingCompanyContextForNonSuperAdmin() {
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

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.getCurrentCompany()).thenReturn(null);

        TaskDTO dto = new TaskDTO();
        dto.setTitle("Task");

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.createTask(dto, null, null, null));
        assertEquals("Company context is required", ex.getMessage());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void createTask_usesWorkOrderCompanyForUserLookup() {
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
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(55L);
        workOrder.setCompany(company);

        User assigned = new User();
        assigned.setId(10L);
        assigned.setRole(User.Role.WORKER_DESIGN);
        User createdBy = new User();
        createdBy.setId(20L);

        when(tenantGuard.requireCompanyId()).thenReturn(99L);
        when(workOrderRepository.findByIdAndCompany_Id(55L, 99L)).thenReturn(Optional.of(workOrder));
        when(userRepository.findByIdAndCompany_Id(10L, 1L)).thenReturn(Optional.of(assigned));
        when(userRepository.findByIdAndCompany_Id(20L, 1L)).thenReturn(Optional.of(createdBy));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            task.setId(44L);
            return task;
        });

        TaskDTO dto = new TaskDTO();
        dto.setTitle("Task");

        service.createTask(dto, 10L, 55L, 20L);

        verify(userRepository).findByIdAndCompany_Id(10L, 1L);
        verify(userRepository).findByIdAndCompany_Id(20L, 1L);
    }
}
