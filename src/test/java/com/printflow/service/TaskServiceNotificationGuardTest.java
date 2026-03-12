package com.printflow.service;

import com.printflow.entity.Task;
import com.printflow.entity.User;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.CommentRepository;
import com.printflow.repository.TaskActivityRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.TimeEntryRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceNotificationGuardTest {

    @Test
    void updateTaskStatusSkipsNotificationWhenCreatorIdIsNull() {
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
        task.setId(201L);
        task.setStatus(TaskStatus.NEW);
        User creator = new User();
        creator.setId(null);
        creator.setRole(User.Role.ADMIN);
        task.setCreatedBy(creator);

        User actor = new User();
        actor.setId(7L);

        when(tenantGuard.requireCompanyId()).thenReturn(1L);
        when(taskRepository.findByIdAndCompany_Id(201L, 1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByIdAndCompany_Id(7L, 1L)).thenReturn(Optional.of(actor));

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

        assertDoesNotThrow(() -> service.updateTaskStatus(201L, TaskStatus.IN_PROGRESS, null, 7L));
        verify(notificationService, never()).sendTaskStatusNotification(any(), any(), any(), any(), any());
    }
}
