package com.printflow.service;

import com.printflow.dto.*;
import com.printflow.entity.*;
import com.printflow.entity.enums.*;
import com.printflow.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final CommentRepository commentRepository;
    private final FileStorageService fileStorageService;
    private final TenantGuard tenantGuard;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final int commentMaxLength;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository,
                       WorkOrderRepository workOrderRepository,
                       TaskActivityRepository taskActivityRepository,
                       TimeEntryRepository timeEntryRepository,
                       CommentRepository commentRepository,
                       FileStorageService fileStorageService,
                       TenantGuard tenantGuard,
                       NotificationService notificationService,
                       AuditLogService auditLogService,
                       @org.springframework.beans.factory.annotation.Value("${app.comment.max-length:2000}") int commentMaxLength) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.taskActivityRepository = taskActivityRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.commentRepository = commentRepository;
        this.fileStorageService = fileStorageService;
        this.tenantGuard = tenantGuard;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.commentMaxLength = commentMaxLength;
    }

    // =============== BASIC TASK METHODS ===============
    
    public Page<TaskDTO> getTasksByWorker(Long userId, Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            return taskRepository.findByAssignedToId(userId, pageable).map(this::convertToTaskDTO);
        }
        Long companyId = tenantGuard.requireCompanyId();
        return taskRepository.findByAssignedToIdAndCompany_Id(userId, companyId, pageable).map(this::convertToTaskDTO);
    }

    public Page<TaskDTO> getTasksByWorkerAndStatus(Long userId, TaskStatus status, Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            return taskRepository.findByAssignedToIdAndStatus(userId, status, pageable).map(this::convertToTaskDTO);
        }
        Long companyId = tenantGuard.requireCompanyId();
        return taskRepository.findByAssignedToIdAndStatusAndCompany_Id(userId, status, companyId, pageable).map(this::convertToTaskDTO);
    }

    public Page<TaskDTO> getTasksByWorkerFiltered(Long userId, TaskStatus status, String filter, String search,
                                                  String sort, String dir, Pageable pageable) {
        Specification<Task> spec = buildWorkerTaskSpec(userId, status, filter, search);
        Pageable effectivePageable = applyWorkerSort(pageable, sort, dir);
        return taskRepository.findAll(spec, effectivePageable).map(this::convertToTaskDTO);
    }

    

    // =============== ACTIVITY METHODS ===============
    
    public List<TaskActivityDTO> getRecentActivities(Long userId, int limit) {
        return taskActivityRepository.findByUserIdOrderByCreatedAtDesc(userId, 
            PageRequest.of(0, limit)).stream()
            .map(this::convertToTaskActivityDTO)
            .collect(Collectors.toList());
    }

    public List<TaskActivityDTO> getTaskActivities(Long taskId) {
        return taskActivityRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
            .map(this::convertToTaskActivityDTO)
            .collect(Collectors.toList());
    }

    public List<TaskActivityDTO> getActivitiesByWorkOrder(Long workOrderId) {
        List<Task> tasks = taskRepository.findByWorkOrderIdAndCompany_Id(workOrderId, tenantGuard.requireCompanyId());
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        return taskActivityRepository.findByTaskIdsOrderByCreatedAtDesc(taskIds).stream()
            .map(this::convertToTaskActivityDTO)
            .collect(Collectors.toList());
    }
    public List<AttachmentDTO> getAttachmentsByTask(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        
        if (task.getWorkOrder() == null) {
            return new ArrayList<>();
        }
        
        // Pozovite FileStorageService da dobijete attachment-e
        return fileStorageService.getAttachmentsByWorkOrder(task.getWorkOrder().getId());
    }
    // =============== STATISTICS ===============
    
    public TaskStatisticsDTO getWorkerTaskStatistics(Long userId) {
        TaskStatisticsDTO stats = new TaskStatisticsDTO();
        Long companyId = tenantGuard.isSuperAdmin() ? null : tenantGuard.requireCompanyId();

        long totalTasks = tenantGuard.isSuperAdmin()
            ? taskRepository.countByAssignedToId(userId)
            : taskRepository.countByAssignedToIdAndCompany_Id(userId, companyId);
        long inProgressTasks = tenantGuard.isSuperAdmin()
            ? taskRepository.countByAssignedToIdAndStatus(userId, TaskStatus.IN_PROGRESS)
            : taskRepository.countByAssignedToIdAndStatusAndCompany_Id(userId, TaskStatus.IN_PROGRESS, companyId);
        long completedTasks = tenantGuard.isSuperAdmin()
            ? taskRepository.countByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED)
            : taskRepository.countByAssignedToIdAndStatusAndCompany_Id(userId, TaskStatus.COMPLETED, companyId);
        long pendingTasks = tenantGuard.isSuperAdmin()
            ? taskRepository.countByAssignedToIdAndStatus(userId, TaskStatus.PENDING)
            : taskRepository.countByAssignedToIdAndStatusAndCompany_Id(userId, TaskStatus.PENDING, companyId);

        LocalDateTime now = LocalDateTime.now();
        long overdueTasks = tenantGuard.isSuperAdmin()
            ? taskRepository.countOverdueByAssignedToId(userId, now)
            : taskRepository.countOverdueByAssignedToIdAndCompany(userId, companyId, now);

        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd = now.withHour(23).withMinute(59).withSecond(59).withNano(999_000_000);
        long completedToday = taskRepository.countCompletedByAssignedToIdBetween(userId, todayStart, todayEnd);

        stats.setTotalTasks((int) totalTasks);
        stats.setInProgressTasks((int) inProgressTasks);
        stats.setCompletedTasks((int) completedTasks);
        stats.setPendingTasks((int) pendingTasks);
        long urgentTasks = tenantGuard.isSuperAdmin()
            ? taskRepository.countUrgentByAssignedToId(userId, TaskStatus.COMPLETED)
            : taskRepository.countUrgentByAssignedToIdAndCompany(userId, companyId, TaskStatus.COMPLETED);
        stats.setUrgentTasks((int) urgentTasks);
        stats.setCompletedToday((int) completedToday);
        stats.setOverdueTasks((int) overdueTasks);

        if (totalTasks > 0) {
            double completionRate = (double) completedTasks / totalTasks * 100;
            stats.setCompletionRate(Math.round(completionRate * 100.0) / 100.0);
        }

        return stats;
    }

    // =============== TASK STATUS MANAGEMENT ===============
    private TaskStatus normalizeTaskStatus(TaskStatus requested) {
        if (requested == null) {
            return null;
        }
        if (requested == TaskStatus.COMPLETED) {
            return TaskStatus.AWAITING_REVIEW;
        }
        return requested;
    }

    private void validateTaskTransition(TaskStatus oldStatus, TaskStatus newStatus, boolean worker) {
        if (newStatus == null || oldStatus == newStatus) {
            return;
        }
        if (oldStatus == TaskStatus.COMPLETED || oldStatus == TaskStatus.CANCELLED) {
            throw new RuntimeException("Cannot change status from terminal state: " + oldStatus);
        }
        java.util.Map<TaskStatus, java.util.Set<TaskStatus>> allowed = java.util.Map.of(
            TaskStatus.NEW, java.util.Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.WAITING_APPROVAL, TaskStatus.AWAITING_REVIEW, TaskStatus.ASSIGNED),
            TaskStatus.ASSIGNED, java.util.Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.WAITING_APPROVAL),
            TaskStatus.IN_PROGRESS, java.util.Set.of(TaskStatus.BLOCKED, TaskStatus.WAITING_APPROVAL, TaskStatus.AWAITING_REVIEW),
            TaskStatus.BLOCKED, java.util.Set.of(TaskStatus.IN_PROGRESS, TaskStatus.WAITING_APPROVAL),
            TaskStatus.WAITING_APPROVAL, java.util.Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.AWAITING_REVIEW),
            TaskStatus.AWAITING_REVIEW, java.util.Set.of(TaskStatus.IN_PROGRESS)
        );

        if (worker) {
            java.util.Set<TaskStatus> allowedForWorker = java.util.Set.of(
                TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.WAITING_APPROVAL, TaskStatus.AWAITING_REVIEW
            );
            if (!allowedForWorker.contains(newStatus)) {
                throw new RuntimeException("Invalid status for worker");
            }
        }

        java.util.Set<TaskStatus> allowedNext = allowed.get(oldStatus);
        if (allowedNext == null || !allowedNext.contains(newStatus)) {
            throw new RuntimeException("Invalid task status transition: " + oldStatus + " -> " + newStatus);
        }
    }
    
    public void updateTaskStatus(Long taskId, TaskStatus status, String notes, Long userId) {
        Task task = getTaskOrThrow(taskId);
        
        TaskStatus oldStatus = task.getStatus();
        TaskStatus nextStatus = normalizeTaskStatus(status);
        if (nextStatus == null) {
            throw new RuntimeException("Status is required");
        }
        validateTaskTransition(oldStatus, nextStatus, false);
        task.setStatus(nextStatus);
        task.setUpdatedAt(LocalDateTime.now());

        if (nextStatus == TaskStatus.IN_PROGRESS && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (nextStatus == TaskStatus.AWAITING_REVIEW) {
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }
        
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }
        
        taskRepository.save(task);
        
        // Log activity
        createTaskActivity(task, "STATUS_CHANGED", 
            String.format("Status changed from %s to %s", oldStatus, nextStatus), userId);
        auditLogService.log(AuditAction.STATUS_CHANGE, "Task", task.getId(),
            oldStatus != null ? oldStatus.name() : null,
            nextStatus != null ? nextStatus.name() : null,
            "Task status updated",
            task.getCompany());

        if (shouldSendStatusNotification(task, userId, nextStatus)) {
            notificationService.sendTaskStatusNotification(task.getId(), task.getCreatedBy().getId(),
                task.getTitle(), statusName(oldStatus), statusName(nextStatus));
        }

        if (nextStatus == TaskStatus.AWAITING_REVIEW) {
            notifyAdminsTaskAwaitingReview(task);
        }
    }

    public void updateTaskStatusForWorker(Long taskId, TaskStatus requestedStatus, String notes, Long userId) {
        Task task = taskRepository.findByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Task not found"));

        if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.UNDER_REVIEW || task.getStatus() == TaskStatus.COMPLETED) {
            throw new RuntimeException("Task status cannot be changed");
        }

        TaskStatus nextStatus = normalizeTaskStatus(requestedStatus);
        if (nextStatus == null) {
            throw new RuntimeException("Status is required");
        }

        validateTaskTransition(task.getStatus(), nextStatus, true);
        if (nextStatus == TaskStatus.BLOCKED && (notes == null || notes.trim().isEmpty())) {
            throw new RuntimeException("Block reason is required");
        }
        if (nextStatus == TaskStatus.AWAITING_REVIEW && !hasProofAttachment(taskId)) {
            throw new RuntimeException("Upload proof before requesting review");
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(nextStatus);
        task.setUpdatedAt(LocalDateTime.now());

        if (nextStatus == TaskStatus.IN_PROGRESS && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (nextStatus == TaskStatus.AWAITING_REVIEW) {
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }

        String noteText = notes;
        if (nextStatus == TaskStatus.BLOCKED && notes != null && !notes.trim().isEmpty()) {
            noteText = "Blocked: " + notes.trim();
        }
        if (noteText != null && !noteText.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? noteText : task.getNotes() + "\n" + noteText);
        }

        taskRepository.save(task);

        String activityDescription = String.format("Status changed from %s to %s", oldStatus, nextStatus);
        if (nextStatus == TaskStatus.BLOCKED && notes != null && !notes.trim().isEmpty()) {
            activityDescription += ". Reason: " + notes.trim();
        }
        createTaskActivity(task, "STATUS_CHANGED", activityDescription, userId);
        auditLogService.log(AuditAction.STATUS_CHANGE, "Task", task.getId(),
            oldStatus != null ? oldStatus.name() : null,
            nextStatus != null ? nextStatus.name() : null,
            "Task status updated (worker)",
            task.getCompany());

        if (shouldSendStatusNotification(task, userId, nextStatus)) {
            notificationService.sendTaskStatusNotification(task.getId(), task.getCreatedBy().getId(),
                task.getTitle(), statusName(oldStatus), statusName(nextStatus));
        }

        if (nextStatus == TaskStatus.AWAITING_REVIEW) {
            notifyAdminsTaskAwaitingReview(task);
        }
    }

    public void updateTaskProgress(Long taskId, int progress, Long userId, String notes) {
        Task task = getTaskOrThrow(taskId);

        int safeProgress = Math.max(0, Math.min(100, progress));
        TaskStatus oldStatus = task.getStatus();
        task.setProgress(safeProgress);

        if (safeProgress > 0 && task.getStatus() == TaskStatus.NEW) {
            task.setStatus(TaskStatus.IN_PROGRESS);
        }
        if (safeProgress >= 100) {
            if (!hasProofAttachment(taskId)) {
                throw new RuntimeException("Upload proof before requesting review");
            }
            validateTaskTransition(oldStatus, TaskStatus.AWAITING_REVIEW, false);
            task.setStatus(TaskStatus.AWAITING_REVIEW);
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }

        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        createTaskActivity(task, "PROGRESS_UPDATED",
            "Progress updated to " + safeProgress + "%", userId);

        if (shouldSendStatusNotification(task, userId, task.getStatus())) {
            notificationService.sendTaskStatusNotification(task.getId(), task.getCreatedBy().getId(),
                task.getTitle(), statusName(oldStatus), statusName(task.getStatus()));
        }

        if (safeProgress >= 100) {
            notifyAdminsTaskAwaitingReview(task);
        }
    }

    public void updateTaskProgressForWorker(Long taskId, int progress, Long userId, String notes) {
        Task task = taskRepository.findByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Task not found"));

        int safeProgress = Math.max(0, Math.min(100, progress));
        TaskStatus oldStatus = task.getStatus();
        task.setProgress(safeProgress);

        if (safeProgress > 0 && task.getStatus() == TaskStatus.NEW) {
            validateTaskTransition(oldStatus, TaskStatus.IN_PROGRESS, true);
            task.setStatus(TaskStatus.IN_PROGRESS);
        }
        if (safeProgress >= 100) {
            if (!hasProofAttachment(taskId)) {
                throw new RuntimeException("Upload proof before requesting review");
            }
            validateTaskTransition(oldStatus, TaskStatus.AWAITING_REVIEW, true);
            task.setStatus(TaskStatus.AWAITING_REVIEW);
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }

        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        createTaskActivity(task, "PROGRESS_UPDATED",
            "Progress updated to " + safeProgress + "%", userId);

        if (shouldSendStatusNotification(task, userId, task.getStatus())) {
            notificationService.sendTaskStatusNotification(task.getId(), task.getCreatedBy().getId(),
                task.getTitle(), statusName(oldStatus), statusName(task.getStatus()));
        }

        if (safeProgress >= 100) {
            notifyAdminsTaskAwaitingReview(task);
        }
    }

    private boolean hasProofAttachment(Long taskId) {
        return fileStorageService.getAttachmentsByTask(taskId).stream()
            .anyMatch(a -> a.getAttachmentType() == AttachmentType.PROOF_OF_WORK);
    }

    private String statusName(TaskStatus status) {
        return status != null ? status.name() : "";
    }

    public void markComplete(Long taskId, String notes, Long userId) {
        Task task = getTaskOrThrow(taskId);
        
        validateTaskTransition(task.getStatus(), TaskStatus.AWAITING_REVIEW, false);
        task.setStatus(TaskStatus.AWAITING_REVIEW);
        task.setProgress(100);
        task.setUpdatedAt(LocalDateTime.now());
        
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }
        
        taskRepository.save(task);
        
        createTaskActivity(task, "TASK_COMPLETED", 
            "Task marked as complete (awaiting review)", userId);
        notifyAdminsTaskAwaitingReview(task);
    }

    public void requestReview(Long taskId, String notes, Long userId) {
        Task task = getTaskOrThrow(taskId);
        
        validateTaskTransition(task.getStatus(), TaskStatus.AWAITING_REVIEW, false);
        task.setStatus(TaskStatus.AWAITING_REVIEW);
        task.setUpdatedAt(LocalDateTime.now());
        
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }
        
        taskRepository.save(task);
        
        createTaskActivity(task, "REVIEW_REQUESTED", 
            "Review requested for task", userId);
        notifyAdminsTaskAwaitingReview(task);
    }

    public void approveTaskCompletion(Long taskId, Long adminUserId) {
        Task task = getTaskOrThrow(taskId);

        if (task.getStatus() != TaskStatus.AWAITING_REVIEW) {
            throw new RuntimeException("Task is not awaiting review");
        }
        if (!hasProofAttachment(taskId)) {
            throw new RuntimeException("No proof attachments uploaded");
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        createTaskActivity(task, "TASK_APPROVED",
            String.format("Task approved by admin (from %s)", oldStatus), adminUserId);
        auditLogService.log(AuditAction.APPROVE, "Task", task.getId(),
            oldStatus != null ? oldStatus.name() : null,
            TaskStatus.COMPLETED.name(),
            "Task approved by admin",
            task.getCompany());

        if (task.getWorkOrder() != null) {
            notificationService.sendClientTaskApprovedNotification(task.getWorkOrder(), task.getTitle());
        }

        java.util.Set<Long> recipientIds = new java.util.LinkedHashSet<>();
        if (task.getAssignedTo() != null) {
            recipientIds.add(task.getAssignedTo().getId());
        }
        if (task.getCreatedBy() != null) {
            recipientIds.add(task.getCreatedBy().getId());
        }
        for (Long recipientId : recipientIds) {
            notificationService.sendTaskReviewDecisionNotification(
                recipientId,
                "Task Approved",
                "Task \"" + task.getTitle() + "\" was approved.",
                task.getId()
            );
        }
    }

    public void rejectTaskCompletion(Long taskId, Long adminUserId, String reason) {
        Task task = getTaskOrThrow(taskId);

        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Reject reason is required");
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setCompletedAt(null);
        task.setUpdatedAt(LocalDateTime.now());

        String noteText = "Review rejected: " + reason.trim();
        task.setNotes(task.getNotes() == null ? noteText : task.getNotes() + "\n" + noteText);

        taskRepository.save(task);

        createTaskActivity(task, "TASK_REJECTED",
            String.format("Task rejected by admin (from %s). Reason: %s", oldStatus, reason.trim()), adminUserId);
        auditLogService.log(AuditAction.REJECT, "Task", task.getId(),
            oldStatus != null ? oldStatus.name() : null,
            TaskStatus.IN_PROGRESS.name(),
            "Task rejected by admin: " + reason.trim(),
            task.getCompany());

        java.util.Set<Long> recipientIds = new java.util.LinkedHashSet<>();
        if (task.getAssignedTo() != null) {
            recipientIds.add(task.getAssignedTo().getId());
        }
        if (task.getCreatedBy() != null) {
            recipientIds.add(task.getCreatedBy().getId());
        }
        for (Long recipientId : recipientIds) {
            notificationService.sendTaskReviewDecisionNotification(
                recipientId,
                "Task Rejected",
                "Task \"" + task.getTitle() + "\" was rejected. Reason: " + reason.trim(),
                task.getId()
            );
        }
    }

    // =============== TASK ASSIGNMENT ===============
    
    public boolean isTaskAssignedToUser(Long taskId, Long userId) {
        return taskRepository.existsByIdAndAssignedToId(taskId, userId);
    }

    public boolean canUserAccessTask(Long taskId, Long userId) {
        Task task = taskRepository.findByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId()).orElse(null);
        if (task == null) {
            return false;
        }
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElse(null);
        if (user == null) {
            return false;
        }
        if (task.getCompany() != null && user.getCompany() != null
            && !task.getCompany().getId().equals(user.getCompany().getId())) {
            return false;
        }
        if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId)) {
            return true;
        }
        if (task.getWorkOrder() != null && task.getWorkOrder().getAssignedTo() != null
            && task.getWorkOrder().getAssignedTo().getId().equals(userId)) {
            return true;
        }
        return false;
    }

    public boolean isTaskAvailable(Long taskId) {
        Task task = taskRepository.findByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId()).orElse(null);
        return task != null && task.getAssignedTo() == null && 
               task.getStatus() == TaskStatus.PENDING;
    }

    public void assignTaskToWorker(Long taskId, Long userId) {
        Task task = getTaskOrThrow(taskId);
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        Long previousAssignedId = task.getAssignedTo() != null ? task.getAssignedTo().getId() : null;
        
        task.setAssignedTo(user);
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAssignedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        createTaskActivity(task, "TASK_ASSIGNED", 
            String.format("Task assigned to %s", user.getFullName()), userId);
        auditLogService.log(AuditAction.UPDATE, "Task", task.getId(),
            previousAssignedId != null ? "assignedToId:" + previousAssignedId : null,
            "assignedToId:" + user.getId(),
            "Task assigned",
            task.getCompany());
        notificationService.sendTaskAssignedNotification(task.getId(), user.getId(), task.getTitle());
    }

    // =============== AVAILABLE TASKS ===============
    
    public Page<TaskDTO> getAvailableTasks(Long userId, String priority, String skill, Pageable pageable) {
        userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElseThrow();
        Long companyId = tenantGuard.isSuperAdmin() ? null : tenantGuard.requireCompanyId();
        
        // Query for unassigned tasks
        if (priority != null && !priority.isEmpty()) {
            try {
                TaskPriority taskPriority = TaskPriority.valueOf(priority.toUpperCase());
                if (tenantGuard.isSuperAdmin()) {
                    return taskRepository.findAvailableTasksByPriorityAll(taskPriority, pageable)
                        .map(this::convertToTaskDTO);
                }
                return taskRepository.findAvailableTasksByPriority(companyId, taskPriority, pageable)
                    .map(this::convertToTaskDTO);
            } catch (IllegalArgumentException e) {
                // Invalid priority, return all available tasks
                if (tenantGuard.isSuperAdmin()) {
                    return taskRepository.findAvailableTasksAll(pageable).map(this::convertToTaskDTO);
                }
                return taskRepository.findAvailableTasks(companyId, pageable).map(this::convertToTaskDTO);
            }
        }
        
        if (tenantGuard.isSuperAdmin()) {
            return taskRepository.findAvailableTasksAll(pageable).map(this::convertToTaskDTO);
        }
        return taskRepository.findAvailableTasks(companyId, pageable).map(this::convertToTaskDTO);
    }

    public Page<TaskDTO> getTasksByStatusForAdmin(TaskStatus status, Pageable pageable) {
        Page<Task> tasks;
        if (tenantGuard.isSuperAdmin()) {
            tasks = taskRepository.findByStatus(status, pageable);
        } else {
            tasks = taskRepository.findByStatusAndCompany_Id(status, tenantGuard.requireCompanyId(), pageable);
        }
        return tasks.map(this::convertToTaskDTO);
    }

    public Page<TaskDTO> getTasksForAdmin(Pageable pageable) {
        Page<Task> tasks;
        if (tenantGuard.isSuperAdmin()) {
            tasks = taskRepository.findAll(pageable);
        } else {
            tasks = taskRepository.findByCompany_Id(tenantGuard.requireCompanyId(), pageable);
        }
        return tasks.map(this::convertToTaskDTO);
    }

    public Page<TaskDTO> getOpenTasksForAdmin(Pageable pageable) {
        List<TaskStatus> openStatuses = List.of(
            TaskStatus.NEW,
            TaskStatus.ASSIGNED,
            TaskStatus.PENDING,
            TaskStatus.IN_PROGRESS,
            TaskStatus.WAITING_APPROVAL,
            TaskStatus.BLOCKED,
            TaskStatus.AWAITING_REVIEW,
            TaskStatus.UNDER_REVIEW
        );
        Page<Task> tasks;
        if (tenantGuard.isSuperAdmin()) {
            tasks = taskRepository.findByStatusIn(openStatuses, pageable);
        } else {
            tasks = taskRepository.findByStatusInAndCompany_Id(openStatuses, tenantGuard.requireCompanyId(), pageable);
        }
        return tasks.map(this::convertToTaskDTO);
    }

    public TaskDTO getTaskForEdit(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        return convertToTaskDTO(task);
    }

    public void updateTaskFromAdmin(Long taskId, TaskDTO dto, Long assignedToId) {
        Task task = getTaskOrThrow(taskId);

        String oldTitle = task.getTitle();
        String oldDescription = task.getDescription();
        LocalDateTime oldDueDate = task.getDueDate();
        TaskPriority oldPriority = task.getPriority();
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());
        task.setEstimatedHours(dto.getEstimatedHours());

        if (dto.getPriority() != null && !dto.getPriority().isBlank()) {
            task.setPriority(TaskPriority.valueOf(dto.getPriority().toUpperCase()));
        }

        Long previousAssignedId = task.getAssignedTo() != null ? task.getAssignedTo().getId() : null;
        if (assignedToId != null) {
            User assigned = userRepository.findByIdAndCompany_Id(assignedToId, tenantGuard.requireCompanyId())
                .orElseThrow(() -> new RuntimeException("User not found"));
            task.setAssignedTo(assigned);
            if (task.getStatus() == TaskStatus.PENDING) {
                task.setStatus(TaskStatus.ASSIGNED);
            }
        } else {
            task.setAssignedTo(null);
            if (task.getStatus() == TaskStatus.ASSIGNED) {
                task.setStatus(TaskStatus.PENDING);
            }
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        if (oldTitle != null && !oldTitle.equals(task.getTitle())) {
            auditLogService.log(AuditAction.UPDATE, "Task", task.getId(),
                oldTitle, task.getTitle(), "Task title updated", task.getCompany());
        }
        if (oldDescription != null && !oldDescription.equals(task.getDescription())) {
            auditLogService.log(AuditAction.UPDATE, "Task", task.getId(),
                oldDescription, task.getDescription(), "Task description updated", task.getCompany());
        }
        if (oldDueDate != null && task.getDueDate() != null && !oldDueDate.equals(task.getDueDate())) {
            auditLogService.log(AuditAction.UPDATE, "Task", task.getId(),
                oldDueDate.toString(), task.getDueDate().toString(), "Task due date updated", task.getCompany());
        }
        if (oldPriority != null && task.getPriority() != null && !oldPriority.equals(task.getPriority())) {
            auditLogService.log(AuditAction.UPDATE, "Task", task.getId(),
                oldPriority.name(), task.getPriority().name(), "Task priority updated", task.getCompany());
        }

        if (assignedToId != null && (previousAssignedId == null || !assignedToId.equals(previousAssignedId))) {
            notificationService.sendTaskAssignedNotification(task.getId(), assignedToId, task.getTitle());
            auditLogService.log(AuditAction.UPDATE, "Task", task.getId(),
                previousAssignedId != null ? "assignedToId:" + previousAssignedId : null,
                "assignedToId:" + assignedToId,
                "Task assignment updated",
                task.getCompany());
        }
    }

    public TaskDTO createTask(TaskDTO dto, Long assignedToId, Long workOrderId, Long createdById) {
        if (dto == null) {
            throw new IllegalArgumentException("Task payload is required");
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        Task task = new Task();
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());
        task.setEstimatedHours(dto.getEstimatedHours());
        task.setProgress(0);

        TaskPriority priority = TaskPriority.MEDIUM;
        if (dto.getPriority() != null && !dto.getPriority().isBlank()) {
            try {
                priority = TaskPriority.valueOf(dto.getPriority().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid task priority");
            }
        }
        task.setPriority(priority);

        if (workOrderId != null) {
            WorkOrder order = getWorkOrderOrThrow(workOrderId);
            task.setWorkOrder(order);
            task.setCompany(order.getCompany());
        } else {
            if (tenantGuard.isSuperAdmin()) {
                throw new RuntimeException("Work order is required for super admin task creation");
            }
            task.setCompany(tenantGuard.getCurrentCompany());
        }

        if (assignedToId != null) {
            User assigned = userRepository.findByIdAndCompany_Id(assignedToId, tenantGuard.requireCompanyId())
                .orElseThrow(() -> new RuntimeException("User not found"));
            task.setAssignedTo(assigned);
            task.setStatus(TaskStatus.ASSIGNED);
            task.setAssignedAt(LocalDateTime.now());
        } else {
            task.setStatus(TaskStatus.PENDING);
        }

        if (task.getStatus() == null) {
            throw new IllegalArgumentException("Task status is required");
        }

        if (createdById != null) {
            User createdBy = userRepository.findByIdAndCompany_Id(createdById, tenantGuard.requireCompanyId()).orElse(null);
            task.setCreatedBy(createdBy);
        }

        Task saved = taskRepository.save(task);

        if (assignedToId != null) {
            notificationService.sendTaskAssignedNotification(saved.getId(), assignedToId, saved.getTitle());
        }

        return convertToTaskDTO(saved);
    }

    // =============== TIME TRACKING ===============
    
    public TimeSummaryDTO getTimeSummary(Long userId, String date) {
        List<TimeEntry> entries;
        
        if (date != null && !date.isEmpty()) {
            LocalDate localDate = LocalDate.parse(date);
            LocalDateTime startDate = localDate.atStartOfDay();
            LocalDateTime endDate = localDate.plusDays(1).atStartOfDay();
            entries = timeEntryRepository.findByUserIdAndDateBetween(userId, startDate, endDate);
        } else {
            entries = timeEntryRepository.findByUserId(userId);
        }
        
        double totalHours = entries.stream()
            .mapToDouble(e -> e.getHours() + (e.getMinutes() / 60.0))
            .sum();

        LocalDateTime weekStart = LocalDate.now()
            .with(java.time.DayOfWeek.MONDAY)
            .atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);
        Long weekMinutes = timeEntryRepository.sumMinutesByUserIdAndDateBetween(userId, weekStart, weekEnd);
        double weekHours = (weekMinutes != null ? weekMinutes : 0) / 60.0;
        
        TimeSummaryDTO summary = new TimeSummaryDTO();
        summary.setTotalHours(totalHours);
        summary.setTotalEntries(entries.size());
        summary.setTodayHours(entries.stream()
            .filter(e -> e.getDate().toLocalDate().equals(LocalDate.now()))
            .mapToDouble(e -> e.getHours() + (e.getMinutes() / 60.0))
            .sum());
        summary.setWeekHours(weekHours);
        
        return summary;
    }

    public void logTime(Long taskId, int hours, int minutes, String description, Long userId) {
        Task task = getTaskOrThrow(taskId);

        int totalMinutes = (hours * 60) + minutes;
        if (totalMinutes <= 0) {
            throw new RuntimeException("Time entry must be greater than 0 minutes");
        }
        // Create time entry
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setTask(task);
        timeEntry.setUser(userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElseThrow());
        timeEntry.setHours(hours);
        timeEntry.setMinutes(minutes);
        timeEntry.setDescription(description);
        timeEntry.setDate(LocalDateTime.now());
        timeEntryRepository.save(timeEntry);
        
        // Update task hours
        double loggedHours = hours + (minutes / 60.0);
        task.setActualHours((task.getActualHours() != null ? task.getActualHours() : 0) + loggedHours);
        if (totalMinutes > 0) {
            Long currentMinutes = task.getActualMinutes();
            task.setActualMinutes((currentMinutes != null ? currentMinutes : 0) + totalMinutes);
        }
        if (task.getStatus() == TaskStatus.NEW || task.getStatus() == TaskStatus.ASSIGNED || task.getStatus() == TaskStatus.PENDING) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        // Log activity
        createTaskActivity(task, "TIME_LOGGED", 
            String.format("Logged %.2f hours: %s", loggedHours, description), userId);

        if (task.getCreatedBy() != null && !task.getCreatedBy().getId().equals(userId)) {
            notificationService.sendTaskTimeLoggedNotification(task.getId(), task.getCreatedBy().getId(), task.getTitle(), loggedHours);
        }
    }

    public Page<TimeEntryDTO> getTimeEntriesByWorker(Long userId, String date, Pageable pageable) {
        if (date != null && !date.isEmpty()) {
            LocalDate localDate = LocalDate.parse(date);
            LocalDateTime startDate = localDate.atStartOfDay();
            LocalDateTime endDate = localDate.plusDays(1).atStartOfDay();
            return timeEntryRepository.findByUserIdAndDateBetween(userId, startDate, endDate, pageable)
                .map(this::convertToTimeEntryDTO);
        }
        return timeEntryRepository.findByUserId(userId, pageable).map(this::convertToTimeEntryDTO);
    }

    // =============== TIMER METHODS ===============
    
    public void startTimer(Long taskId, Long userId) {
        Task task = getTaskOrThrow(taskId);

        if (task.getTimerStartedAt() != null) {
            throw new RuntimeException("Timer already running");
        }
        if (task.getStatus() == TaskStatus.COMPLETED
            || task.getStatus() == TaskStatus.CANCELLED
            || task.getStatus() == TaskStatus.AWAITING_REVIEW) {
            throw new RuntimeException("Cannot start timer for completed, cancelled, or review tasks");
        }
        List<Task> activeTimers = taskRepository.findActiveTimersByUserId(userId);
        for (Task active : activeTimers) {
            if (!active.getId().equals(taskId)) {
                stopTimer(active.getId(), userId);
            }
        }
        
        task.setTimerStartedAt(LocalDateTime.now());
        task.setStatus(TaskStatus.IN_PROGRESS);
        if (task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        createTaskActivity(task, "TIMER_STARTED", "Timer started", userId);
    }

    public void stopTimer(Long taskId, Long userId) {
        Task task = getTaskOrThrow(taskId);
        
        if (task.getTimerStartedAt() != null) {
            long minutes = java.time.Duration.between(
                task.getTimerStartedAt(), LocalDateTime.now()).toMinutes();
            if (minutes < 0) {
                minutes = 0;
            }
            
            Long currentMinutes = task.getActualMinutes();
            long nextMinutes = (currentMinutes != null ? currentMinutes : 0) + minutes;
            task.setActualMinutes(nextMinutes);
            task.setTimerStartedAt(null);
            if (minutes > 0) {
                double loggedHours = minutes / 60.0;
                task.setActualHours((task.getActualHours() != null ? task.getActualHours() : 0) + loggedHours);
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            
            if (minutes > 0) {
                createTimeEntry(task, userId, (int) minutes, "Time tracked via timer");
            }
            
            createTaskActivity(task, "TIMER_STOPPED", 
                String.format("Timer stopped after %d minutes", minutes), userId);
        } else {
            throw new RuntimeException("Timer is not running");
        }
    }

    public void pauseTimer(Long taskId, Long userId) {
        Task task = getTaskOrThrow(taskId);
        if (task.getTimerStartedAt() != null) {
            long minutes = java.time.Duration.between(
                task.getTimerStartedAt(), LocalDateTime.now()).toMinutes();
            if (minutes < 0) {
                minutes = 0;
            }
            Long currentMinutes = task.getActualMinutes();
            long nextMinutes = (currentMinutes != null ? currentMinutes : 0) + minutes;
            task.setActualMinutes(nextMinutes);
            task.setTimerStartedAt(null);
            if (minutes > 0) {
                double loggedHours = minutes / 60.0;
                task.setActualHours((task.getActualHours() != null ? task.getActualHours() : 0) + loggedHours);
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            if (minutes > 0) {
                createTimeEntry(task, userId, (int) minutes, "Time tracked via timer (pause)");
            }
            createTaskActivity(task, "TIMER_PAUSED",
                String.format("Timer paused at %d minutes", minutes), userId);
        } else {
            throw new RuntimeException("Timer is not running");
        }
    }

    // =============== COMMENTS ===============

    public List<CommentDTO> getTaskComments(Long taskId) {
        return commentRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
            .map(this::convertToCommentDTO)
            .peek(dto -> {
                if (dto.getId() != null) {
                    dto.setAttachments(fileStorageService.getAttachmentsByComment(dto.getId()));
                }
            })
            .collect(Collectors.toList());
    }
    
    public Comment addComment(Long taskId, String comment, Long userId) {
        if (comment == null || comment.isBlank()) {
            throw new RuntimeException("Comment cannot be empty");
        }
        String trimmed = comment.trim();
        if (trimmed.length() > commentMaxLength) {
            throw new RuntimeException("Comment is too long. Max length is " + commentMaxLength + " characters");
        }
        Task task = getTaskOrThrow(taskId);
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Comment newComment = new Comment();
        newComment.setTask(task);
        newComment.setUser(user);
        newComment.setContent(trimmed);
        newComment.setCreatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(newComment);
        
        createTaskActivity(task, "COMMENT_ADDED", 
            String.format("Comment added by %s", user.getFullName()), userId);

        if (task.getCreatedBy() != null) {
            notificationService.sendTaskCommentNotification(task.getId(), userId, task.getCreatedBy().getId(), task.getTitle());
        }

        Set<String> mentions = extractMentions(comment);
        if (!mentions.isEmpty()) {
            List<User> mentionedUsers;
            if (task.getCompany() != null) {
                mentionedUsers = userRepository.findByCompany_IdAndUsernameInAndActiveTrue(
                    task.getCompany().getId(), new ArrayList<>(mentions));
            } else {
                mentionedUsers = userRepository.findByUsernameInAndActiveTrue(new ArrayList<>(mentions));
            }
            List<Long> mentionIds = mentionedUsers.stream()
                .filter(u -> !u.getId().equals(userId))
                .map(User::getId)
                .distinct()
                .toList();
            if (!mentionIds.isEmpty()) {
                String message = "You were mentioned in a task comment: " + task.getTitle();
                notificationService.createNotificationForUsers(
                    mentionIds, "Task mention", message, "TASK_MENTION", "/worker/task/" + task.getId());
            }
        }

        return saved;
    }

    public boolean isCommentAuthor(Long commentId, Long userId) {
        return commentRepository.existsByIdAndUserId(commentId, userId);
    }

    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findByIdAndCompanyId(commentId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        commentRepository.delete(comment);
    }

    public void deleteCommentForUser(Long taskId, Long commentId, Long userId) {
        Comment comment = commentRepository
            .findByIdAndTask_IdAndTask_Company_Id(commentId, taskId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (comment.getUser() == null || !comment.getUser().getId().equals(userId)) {
            throw new RuntimeException("You can delete only your own comment");
        }
        commentRepository.delete(comment);
    }

    public void deleteTask(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        List<AttachmentDTO> attachments = fileStorageService.getAttachmentsByTask(taskId);
        for (AttachmentDTO attachment : attachments) {
            fileStorageService.deleteAttachment(attachment.getId());
        }
        commentRepository.deleteByTaskId(taskId);
        taskActivityRepository.deleteByTaskId(taskId);
        timeEntryRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
    }

    // =============== REPORTS ===============
    
    public DailyWorkReportDTO generateDailyReport(Long userId, String date) {
        DailyWorkReportDTO report = new DailyWorkReportDTO();
        LocalDate reportDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
        
        // Get tasks for the date
        List<Task> tasks = taskRepository.findByAssignedToIdAndDate(userId, reportDate);
        
        report.setDate(reportDate);
        report.setTotalTasks(tasks.size());
        report.setCompletedTasks((int) tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
            .count());
        
        // Calculate total hours
        double totalHours = tasks.stream()
            .mapToDouble(t -> t.getActualHours() != null ? t.getActualHours() : 0)
            .sum();
        report.setTotalHours(totalHours);
        
        return report;
    }

    public WeeklyWorkReportDTO generateWeeklyReport(Long userId, String week) {
        WeeklyWorkReportDTO report = new WeeklyWorkReportDTO();
        // Implementation for weekly report
        return report;
    }

    public Object getMonthlyWorkerStats(Long userId) {
        // Return monthly statistics
        return null;
    }

    // =============== PRIVATE HELPER METHODS ===============
    
    private void createTaskActivity(Task task, String action, String description, Long userId) {
        TaskActivity activity = new TaskActivity();
        activity.setTask(task);
        activity.setUser(userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElseThrow());
        activity.setAction(action);
        activity.setDescription(description);
        activity.setCreatedAt(LocalDateTime.now());
        taskActivityRepository.save(activity);
    }

    private void createTimeEntry(Task task, Long userId, int minutes, String description) {
        if (minutes <= 0) {
            return;
        }
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setTask(task);
        timeEntry.setUser(userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElseThrow());
        timeEntry.setHours(minutes / 60);
        timeEntry.setMinutes(minutes % 60);
        timeEntry.setDescription(description);
        timeEntry.setDate(LocalDateTime.now());
        timeEntryRepository.save(timeEntry);
    }

    private Pageable applyWorkerSort(Pageable pageable, String sort, String dir) {
        if (sort == null || sort.isBlank()) {
            return pageable;
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = switch (sort) {
            case "dueDate" -> "dueDate";
            case "priority" -> "priority";
            case "createdAt" -> "createdAt";
            case "status" -> "status";
            default -> null;
        };
        if (sortField == null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(direction, sortField));
    }

    private Specification<Task> buildWorkerTaskSpec(Long userId, TaskStatus status, String filter, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            query.distinct(true);

            predicates.add(cb.equal(root.get("assignedTo").get("id"), userId));
            if (!tenantGuard.isSuperAdmin()) {
                predicates.add(cb.equal(root.get("company").get("id"), tenantGuard.requireCompanyId()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (filter != null && !filter.isBlank()) {
                LocalDate today = LocalDate.now();
                LocalDateTime startOfDay = today.atStartOfDay();
                LocalDateTime endOfDay = today.atTime(23, 59, 59);
                switch (filter) {
                    case "today" -> predicates.add(cb.between(root.get("dueDate"), startOfDay, endOfDay));
                    case "this_week" -> {
                        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
                        LocalDate weekEnd = weekStart.plusDays(6);
                        LocalDateTime start = weekStart.atStartOfDay();
                        LocalDateTime end = weekEnd.atTime(23, 59, 59);
                        predicates.add(cb.between(root.get("dueDate"), start, end));
                    }
                    case "overdue" -> {
                        predicates.add(cb.isNotNull(root.get("dueDate")));
                        predicates.add(cb.lessThan(root.get("dueDate"), LocalDateTime.now()));
                        predicates.add(cb.notEqual(root.get("status"), TaskStatus.COMPLETED));
                    }
                    case "high_priority" -> predicates.add(cb.equal(root.get("priority"), TaskPriority.HIGH));
                    default -> {
                    }
                }
            }

            if (search != null && !search.isBlank()) {
                String q = "%" + search.toLowerCase() + "%";
                Predicate textMatch = cb.or(
                    cb.like(cb.lower(root.get("title")), q),
                    cb.like(cb.lower(root.get("description")), q),
                    cb.like(cb.lower(root.get("notes")), q)
                );
                Join<Task, WorkOrder> workOrderJoin = root.join("workOrder", JoinType.LEFT);
                Predicate orderMatch = cb.like(cb.lower(workOrderJoin.get("orderNumber")), q);
                predicates.add(cb.or(textMatch, orderMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Set<String> extractMentions(String comment) {
        if (comment == null || comment.isBlank()) {
            return Set.of();
        }
        Pattern pattern = Pattern.compile("@([A-Za-z0-9._-]{2,30})");
        Matcher matcher = pattern.matcher(comment);
        Set<String> result = new java.util.HashSet<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private void notifyAdminsTaskAwaitingReview(Task task) {
        if (task.getCompany() == null) {
            return;
        }
        List<User.Role> roles = List.of(User.Role.ADMIN, User.Role.MANAGER);
        List<User> admins = userRepository.findByCompany_IdAndRoleInAndActiveTrue(task.getCompany().getId(), roles);
        notificationService.sendTaskAwaitingReviewNotification(admins, task.getId(), task.getTitle());
    }

    private boolean shouldSendStatusNotification(Task task, Long actorId, TaskStatus nextStatus) {
        if (task.getCreatedBy() == null || nextStatus == null) {
            return false;
        }
        Long creatorId = task.getCreatedBy().getId();
        if (creatorId == null) {
            return false;
        }
        if (creatorId.equals(actorId)) {
            return false;
        }
        if (nextStatus == TaskStatus.AWAITING_REVIEW && isAdminRole(task.getCreatedBy().getRole())) {
            return false;
        }
        return true;
    }

    private boolean isAdminRole(User.Role role) {
        return role == User.Role.SUPER_ADMIN || role == User.Role.ADMIN || role == User.Role.MANAGER;
    }

    private TaskDTO convertToTaskDTO(Task task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(statusName(task.getStatus()));
        dto.setPriority(task.getPriority() != null ? task.getPriority().name() : "MEDIUM");
        dto.setProgress(task.getProgress() != null ? task.getProgress() : 0);
        dto.setDueDate(task.getDueDate());
        dto.setEstimatedHours(task.getEstimatedHours());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setOverdue(task.isOverdue());
        
        if (task.getWorkOrder() != null) {
            dto.setOrderNumber(task.getWorkOrder().getOrderNumber());
            dto.setOrderId(task.getWorkOrder().getId());  // DODATO
            dto.setWorkOrderId(task.getWorkOrder().getId());
        }
        
        if (task.getAssignedTo() != null) {
            dto.setAssignedToId(task.getAssignedTo().getId());
            dto.setAssignedToName(task.getAssignedTo().getFullName());
        }
        
        // Opciono: Postavite clientName ako imate pristup
        // if (task.getWorkOrder() != null && task.getWorkOrder().getClient() != null) {
        //     dto.setClientName(task.getWorkOrder().getClient().getCompanyName());
        // }
        
        // Opciono: Izračunajte broj attachmenta
        // dto.setAttachmentsCount(task.getAttachments() != null ? task.getAttachments().size() : 0);
        
        return dto;}
        
    public TaskDetailsDTO getTaskDetails(Long taskId) {
            Task task = getTaskWithDetailsOrThrow(taskId);
            
            return convertToDetailsDTO(task);
        }

    public TaskDetailsDTO getTaskDetailsForWorker(Long taskId, Long userId) {
        Task task = taskRepository.findWithDetailsByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (task.getCompany() != null && user.getCompany() != null
            && !task.getCompany().getId().equals(user.getCompany().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Task does not belong to your company.");
        }
        return convertToDetailsDTO(task);
    }

    private Task getTaskOrThrow(Long taskId) {
        Task task = taskRepository.findByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
        return task;
    }

    private Task getTaskWithDetailsOrThrow(Long taskId) {
        Task task = taskRepository.findWithDetailsByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
        return task;
    }

    private WorkOrder getWorkOrderOrThrow(Long workOrderId) {
        WorkOrder order = workOrderRepository.findByIdAndCompany_Id(workOrderId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Work order not found"));
        return order;
    }

        private TaskDetailsDTO convertToDetailsDTO(Task task) {
            TaskDetailsDTO dto = new TaskDetailsDTO();
            dto.setId(task.getId());
            dto.setTitle(task.getTitle());
            dto.setDescription(task.getDescription());
            dto.setStatus(statusName(task.getStatus()));
            dto.setPriority(task.getPriority() != null ? task.getPriority().name() : "MEDIUM");
            dto.setDueDate(task.getDueDate());
            dto.setCreatedAt(task.getCreatedAt());
            dto.setUpdatedAt(task.getUpdatedAt());
            dto.setStartedAt(task.getStartedAt());
            dto.setCompletedAt(task.getCompletedAt());
            dto.setProgress(task.getProgress());
            dto.setOverdue(task.isOverdue());
            dto.setEstimatedHours(task.getEstimatedHours());
        dto.setActualHours(task.getActualHours());
        dto.setActualMinutes(task.getActualMinutes() != null ? task.getActualMinutes() : 0L);
        dto.setNotes(task.getNotes());
        dto.setTimerStartedAt(task.getTimerStartedAt());
            
            // WorkOrder info
            if (task.getWorkOrder() != null) {
                dto.setWorkOrderId(task.getWorkOrder().getId());
                dto.setOrderNumber(task.getWorkOrder().getOrderNumber());
                dto.setOrderTitle(task.getWorkOrder().getTitle());
                
                // Client info
                if (task.getWorkOrder().getClient() != null) {
                    dto.setClientId(task.getWorkOrder().getClient().getId());
                    dto.setClientName(task.getWorkOrder().getClient().getCompanyName());
                    dto.setClientEmail(task.getWorkOrder().getClient().getEmail());
                    dto.setClientPhone(task.getWorkOrder().getClient().getPhone());
                }
            }
            
            // Assigned user info
            if (task.getAssignedTo() != null) {
                dto.setAssignedToId(task.getAssignedTo().getId());
                dto.setAssignedToName(task.getAssignedTo().getFullName());
                dto.setAssignedToEmail(task.getAssignedTo().getEmail());
            }
            
            // Created by info
            if (task.getCreatedBy() != null) {
                dto.setCreatedById(task.getCreatedBy().getId());
                dto.setCreatedByName(task.getCreatedBy().getFullName());
            }
            
            // Additional fields
            dto.setAssignedAt(task.getAssignedAt());
            dto.setTimerStartedAt(task.getTimerStartedAt());
            dto.setActualMinutes(task.getActualMinutes() != null ? task.getActualMinutes() : 0);
            
            return dto;
        
    }

    private TaskActivityDTO convertToTaskActivityDTO(TaskActivity activity) {
        TaskActivityDTO dto = new TaskActivityDTO();
        dto.setId(activity.getId());
        dto.setAction(activity.getAction());
        dto.setDescription(activity.getDescription());
        dto.setCreatedAt(activity.getCreatedAt());
        
        if (activity.getUser() != null) {
            dto.setUserName(activity.getUser().getFullName());
            dto.setUserId(activity.getUser().getId());
        }
        
        return dto;
    }

    private CommentDTO convertToCommentDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setTaskId(comment.getTask() != null ? comment.getTask().getId() : null);
        if (comment.getUser() != null) {
            dto.setUserId(comment.getUser().getId());
            String fullName = comment.getUser().getFullName();
            if (fullName == null || fullName.isBlank()) {
                String first = comment.getUser().getFirstName() != null ? comment.getUser().getFirstName() : "";
                String last = comment.getUser().getLastName() != null ? comment.getUser().getLastName() : "";
                fullName = (first + " " + last).trim();
            }
            dto.setUserFullName(fullName != null && !fullName.isBlank() ? fullName : "User");
            String initials = "";
            if (comment.getUser().getFirstName() != null && !comment.getUser().getFirstName().isBlank()) {
                initials += comment.getUser().getFirstName().substring(0, 1).toUpperCase();
            }
            if (comment.getUser().getLastName() != null && !comment.getUser().getLastName().isBlank()) {
                initials += comment.getUser().getLastName().substring(0, 1).toUpperCase();
            }
            if (initials.isBlank() && dto.getUserFullName() != null && !dto.getUserFullName().isBlank()) {
                initials = dto.getUserFullName().substring(0, 1).toUpperCase();
            }
            dto.setUserInitials(initials);
        }
        dto.setContent(comment.getContent());
        dto.setCreatedAt(comment.getCreatedAt());
        return dto;
    }

    private TimeEntryDTO convertToTimeEntryDTO(TimeEntry entry) {
        TimeEntryDTO dto = new TimeEntryDTO();
        dto.setId(entry.getId());
        dto.setHours(entry.getHours());
        dto.setMinutes(entry.getMinutes());
        dto.setDescription(entry.getDescription());
        dto.setDate(entry.getDate());
        
        if (entry.getTask() != null) {
            dto.setTaskId(entry.getTask().getId());
            dto.setTaskTitle(entry.getTask().getTitle());
        }
        
        if (entry.getUser() != null) {
            dto.setUserName(entry.getUser().getFullName());
        }
        
        return dto;
    }
}
