package com.printflow.service;

import com.printflow.dto.TaskDTO;
import com.printflow.entity.Company;
import com.printflow.entity.Task;
import com.printflow.entity.enums.TaskPriority;
import com.printflow.entity.enums.TaskStatus;
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

class TaskServiceAdminUpdateValidationTest {

    @Test
    void updateTaskFromAdmin_rejectsMissingPayload() {
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.updateTaskFromAdmin(1L, null, null));
        assertEquals("Task payload is required", ex.getMessage());
    }

    @Test
    void updateTaskFromAdmin_rejectsBlankTitle() {
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
        Task task = new Task();
        task.setId(7L);
        task.setCompany(company);
        task.setStatus(TaskStatus.PENDING);

        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(7L, 1L)).thenReturn(Optional.of(task));

        TaskDTO dto = new TaskDTO();
        dto.setTitle("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.updateTaskFromAdmin(7L, dto, null));
        assertEquals("Task title is required", ex.getMessage());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void updateTaskFromAdmin_trimsTitleAndValidatesPriority() {
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
        Task task = new Task();
        task.setId(8L);
        task.setCompany(company);
        task.setStatus(TaskStatus.PENDING);

        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(8L, 1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskDTO dto = new TaskDTO();
        dto.setTitle("  Updated Task  ");
        dto.setPriority(TaskPriority.HIGH.name());

        service.updateTaskFromAdmin(8L, dto, null);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals("Updated Task", captor.getValue().getTitle());
        assertEquals(TaskPriority.HIGH, captor.getValue().getPriority());
    }

    @Test
    void updateTaskFromAdmin_rejectsInvalidPriority() {
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
        Task task = new Task();
        task.setId(9L);
        task.setCompany(company);
        task.setStatus(TaskStatus.PENDING);

        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(9L, 1L)).thenReturn(Optional.of(task));

        TaskDTO dto = new TaskDTO();
        dto.setTitle("Task");
        dto.setPriority("NOT_A_PRIORITY");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.updateTaskFromAdmin(9L, dto, null));
        assertEquals("Invalid task priority", ex.getMessage());
        verify(taskRepository, never()).save(any(Task.class));
    }
}
