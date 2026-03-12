package com.printflow.service;

import com.printflow.entity.Task;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.CommentRepository;
import com.printflow.repository.TaskActivityRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.TimeEntryRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

class TaskServiceStatusValidationTest {

    @Test
    void updateTaskStatusRejectsNullStatus() {
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

        Task task = new Task();
        task.setId(101L);
        task.setStatus(TaskStatus.NEW);
        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(101L, 1L)).thenReturn(Optional.of(task));

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

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.updateTaskStatus(101L, null, null, 5L));
        assertEquals("Status is required", ex.getMessage());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void updateTaskStatusForWorkerRejectsNullStatus() {
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

        Task task = new Task();
        task.setId(102L);
        task.setStatus(TaskStatus.NEW);
        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(102L, 1L)).thenReturn(Optional.of(task));

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

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.updateTaskStatusForWorker(102L, null, null, 5L));
        assertEquals("Status is required", ex.getMessage());
        verify(taskRepository, never()).save(any(Task.class));
    }
}
