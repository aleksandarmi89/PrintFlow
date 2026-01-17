package com.printflow.service;

import com.printflow.dto.*;
import com.printflow.entity.*;
import com.printflow.entity.enums.*;
import com.printflow.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final CommentRepository commentRepository;
	private FileStorageService fileStorageService;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository,
                       WorkOrderRepository workOrderRepository,
                       TaskActivityRepository taskActivityRepository,
                       TimeEntryRepository timeEntryRepository,
                       CommentRepository commentRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.taskActivityRepository = taskActivityRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.commentRepository = commentRepository;
    }

    // =============== BASIC TASK METHODS ===============
    
    public Page<TaskDTO> getTasksByWorker(Long userId, Pageable pageable) {
        return taskRepository.findByAssignedToId(userId, pageable).map(this::convertToTaskDTO);
    }

    public Page<TaskDTO> getTasksByWorkerAndStatus(Long userId, TaskStatus status, Pageable pageable) {
        return taskRepository.findByAssignedToIdAndStatus(userId, status, pageable).map(this::convertToTaskDTO);
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
    public List<AttachmentDTO> getAttachmentsByTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
        
        if (task.getWorkOrder() == null) {
            return new ArrayList<>();
        }
        
        // Pozovite FileStorageService da dobijete attachment-e
        return fileStorageService.getAttachmentsByWorkOrder(task.getWorkOrder().getId());
    }
    // =============== STATISTICS ===============
    
    public TaskStatisticsDTO getWorkerTaskStatistics(Long userId) {
        List<Task> tasks = taskRepository.findByAssignedToId(userId);
        TaskStatisticsDTO stats = new TaskStatisticsDTO();
        stats.setTotalTasks(tasks.size());
        stats.setInProgressTasks((int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count());
        stats.setCompletedTasks((int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count());
        stats.setPendingTasks((int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count());
        stats.setOverdueTasks((int) tasks.stream()
            .filter(t -> t.getDueDate() != null && 
                       t.getDueDate().isBefore(LocalDateTime.now()) && 
                       t.getStatus() != TaskStatus.COMPLETED)
            .count());
        
        if (!tasks.isEmpty()) {
            double completionRate = (double) stats.getCompletedTasks() / stats.getTotalTasks() * 100;
            stats.setCompletionRate(Math.round(completionRate * 100.0) / 100.0);
        }
        
        return stats;
    }

    // =============== TASK STATUS MANAGEMENT ===============
    
    public void updateTaskStatus(Long taskId, TaskStatus status, String notes, Long userId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        
        TaskStatus oldStatus = task.getStatus();
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }
        
        taskRepository.save(task);
        
        // Log activity
        createTaskActivity(task, "STATUS_CHANGED", 
            String.format("Status changed from %s to %s", oldStatus, status), userId);
    }

    public void markComplete(Long taskId, String notes, Long userId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        
        task.setStatus(TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }
        
        taskRepository.save(task);
        
        createTaskActivity(task, "TASK_COMPLETED", 
            "Task marked as complete", userId);
    }

    public void requestReview(Long taskId, String notes, Long userId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        
        task.setStatus(TaskStatus.AWAITING_REVIEW);
        task.setUpdatedAt(LocalDateTime.now());
        
        if (notes != null && !notes.trim().isEmpty()) {
            task.setNotes(task.getNotes() == null ? notes : task.getNotes() + "\n" + notes);
        }
        
        taskRepository.save(task);
        
        createTaskActivity(task, "REVIEW_REQUESTED", 
            "Review requested for task", userId);
    }

    // =============== TASK ASSIGNMENT ===============
    
    public boolean isTaskAssignedToUser(Long taskId, Long userId) {
        return taskRepository.existsByIdAndAssignedToId(taskId, userId);
    }

    public boolean isTaskAvailable(Long taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        return task != null && task.getAssignedTo() == null && 
               task.getStatus() == TaskStatus.PENDING;
    }

    public void assignTaskToWorker(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        task.setAssignedTo(user);
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAssignedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        createTaskActivity(task, "TASK_ASSIGNED", 
            String.format("Task assigned to %s", user.getFullName()), userId);
    }

    // =============== AVAILABLE TASKS ===============
    
    public Page<TaskDTO> getAvailableTasks(Long userId, String priority, String skill, Pageable pageable) {
        // Get user to check skills
        User user = userRepository.findById(userId).orElseThrow();
        
        // Query for unassigned tasks
        if (priority != null && !priority.isEmpty()) {
            try {
                TaskPriority taskPriority = TaskPriority.valueOf(priority.toUpperCase());
                return taskRepository.findAvailableTasksByPriority(taskPriority, pageable)
                    .map(this::convertToTaskDTO);
            } catch (IllegalArgumentException e) {
                // Invalid priority, return all available tasks
                return taskRepository.findAvailableTasks(pageable).map(this::convertToTaskDTO);
            }
        }
        
        return taskRepository.findAvailableTasks(pageable).map(this::convertToTaskDTO);
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
        
        TimeSummaryDTO summary = new TimeSummaryDTO();
        summary.setTotalHours(totalHours);
        summary.setTotalEntries(entries.size());
        summary.setTodayHours(entries.stream()
            .filter(e -> e.getDate().toLocalDate().equals(LocalDate.now()))
            .mapToDouble(e -> e.getHours() + (e.getMinutes() / 60.0))
            .sum());
        
        return summary;
    }

    public void logTime(Long taskId, int hours, int minutes, String description, Long userId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        
        // Create time entry
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setTask(task);
        timeEntry.setUser(userRepository.findById(userId).orElseThrow());
        timeEntry.setHours(hours);
        timeEntry.setMinutes(minutes);
        timeEntry.setDescription(description);
        timeEntry.setDate(LocalDateTime.now());
        timeEntryRepository.save(timeEntry);
        
        // Update task hours
        double loggedHours = hours + (minutes / 60.0);
        task.setActualHours((task.getActualHours() != null ? task.getActualHours() : 0) + loggedHours);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        // Log activity
        createTaskActivity(task, "TIME_LOGGED", 
            String.format("Logged %.2f hours: %s", loggedHours, description), userId);
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
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        
        task.setTimerStartedAt(LocalDateTime.now());
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        createTaskActivity(task, "TIMER_STARTED", "Timer started", userId);
    }

    public void stopTimer(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        
        if (task.getTimerStartedAt() != null) {
            long minutes = java.time.Duration.between(
                task.getTimerStartedAt(), LocalDateTime.now()).toMinutes();
            
            task.setActualMinutes((task.getActualMinutes() != 0 ? task.getActualMinutes() : 0) + minutes);
            task.setTimerStartedAt(null);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            
            // Create time entry from timer
            TimeEntry timeEntry = new TimeEntry();
            timeEntry.setTask(task);
            timeEntry.setUser(userRepository.findById(userId).orElseThrow());
            timeEntry.setHours((int) (minutes / 60));
            timeEntry.setMinutes((int) (minutes % 60));
            timeEntry.setDescription("Time tracked via timer");
            timeEntry.setDate(LocalDateTime.now());
            timeEntryRepository.save(timeEntry);
            
            createTaskActivity(task, "TIMER_STOPPED", 
                String.format("Timer stopped after %d minutes", minutes), userId);
        }
    }

    // =============== COMMENTS ===============
    
    public void addComment(Long taskId, String comment, Long userId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Comment newComment = new Comment();
        newComment.setTask(task);
        newComment.setUser(user);
        newComment.setContent(comment);
        newComment.setCreatedAt(LocalDateTime.now());
        commentRepository.save(newComment);
        
        createTaskActivity(task, "COMMENT_ADDED", 
            String.format("Comment added by %s", user.getFullName()), userId);
    }

    public boolean isCommentAuthor(Long commentId, Long userId) {
        return commentRepository.existsByIdAndUserId(commentId, userId);
    }

    public void deleteComment(Long commentId) {
        commentRepository.deleteById(commentId);
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
        activity.setUser(userRepository.findById(userId).orElseThrow());
        activity.setAction(action);
        activity.setDescription(description);
        activity.setCreatedAt(LocalDateTime.now());
        taskActivityRepository.save(activity);
    }

    private TaskDTO convertToTaskDTO(Task task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus().name());
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
            Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
            
            return convertToDetailsDTO(task);
        }

        private TaskDetailsDTO convertToDetailsDTO(Task task) {
            TaskDetailsDTO dto = new TaskDetailsDTO();
            dto.setId(task.getId());
            dto.setTitle(task.getTitle());
            dto.setDescription(task.getDescription());
            dto.setStatus(task.getStatus().name());
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
            dto.setNotes(task.getNotes());
            
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
            dto.setActualMinutes(task.getActualMinutes());
            
            return dto;
        
    }

    private TaskDetailsDTO convertToTaskDetailsDTO(Task task) {
        TaskDetailsDTO dto = new TaskDetailsDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus().name());
        dto.setNotes(task.getNotes());
        dto.setPriority(task.getPriority() != null ? task.getPriority().name() : "MEDIUM");
        dto.setProgress(task.getProgress() != null ? task.getProgress() : 0);
        dto.setDueDate(task.getDueDate());
        dto.setEstimatedHours(task.getEstimatedHours());
        dto.setActualHours(task.getActualHours());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setStartedAt(task.getStartedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setOverdue(task.isOverdue());
        dto.setAssignedAt(task.getAssignedAt());
        dto.setTimerStartedAt(task.getTimerStartedAt());
        dto.setActualMinutes(task.getActualMinutes());
        
        if (task.getWorkOrder() != null) {
            dto.setWorkOrderId(task.getWorkOrder().getId());
            dto.setOrderNumber(task.getWorkOrder().getOrderNumber());
            dto.setOrderTitle(task.getWorkOrder().getTitle());
        }
        
        if (task.getAssignedTo() != null) {
            dto.setAssignedToId(task.getAssignedTo().getId());
            dto.setAssignedToName(task.getAssignedTo().getFullName());
            dto.setAssignedToEmail(task.getAssignedTo().getEmail());
        }
        
        if (task.getCreatedBy() != null) {
            dto.setCreatedById(task.getCreatedBy().getId());
            dto.setCreatedByName(task.getCreatedBy().getFullName());
        }
        
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