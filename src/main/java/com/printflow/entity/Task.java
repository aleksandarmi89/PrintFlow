package com.printflow.entity;

import com.printflow.entity.enums.TaskStatus;
import com.printflow.entity.enums.TaskPriority;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.NEW;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority = TaskPriority.MEDIUM;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    // DODATO: dueDate (koristi se u TaskService)
    private LocalDateTime dueDate;
    
    // DODATO: timerStartedAt (koristi se u timer metodama)
    private LocalDateTime timerStartedAt;
    
    // DODATO: actualHours (za praćenje utrošenog vremena)
    private Double actualHours;
    
    // DODATO: actualMinutes (za timer)
    
    private Long actualMinutes;
    // DODATO: assignedAt (kada je task dodeljen radniku)
    private LocalDateTime assignedAt;
    
    // DODATO: requiredSkills (za available tasks)
    private String requiredSkills;
    
    private Integer progress = 0;
    
    private Double estimatedHours;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;
    
    // Konstruktori
    public Task() {}
    
    public Task(Long id, String title, String description, TaskStatus status, TaskPriority priority, 
                String notes, LocalDateTime dueDate, LocalDateTime timerStartedAt, Double actualHours,
                long actualMinutes, LocalDateTime assignedAt, String requiredSkills, Integer progress, 
                Double estimatedHours, LocalDateTime createdAt, LocalDateTime updatedAt, 
                LocalDateTime startedAt, LocalDateTime completedAt, WorkOrder workOrder, 
                User assignedTo, User createdBy) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.notes = notes;
        this.dueDate = dueDate;
        this.timerStartedAt = timerStartedAt;
        this.actualHours = actualHours;
        this.actualMinutes = actualMinutes;
        this.assignedAt = assignedAt;
        this.requiredSkills = requiredSkills;
        this.progress = progress;
        this.estimatedHours = estimatedHours;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.workOrder = workOrder;
        this.assignedTo = assignedTo;
        this.createdBy = createdBy;
    }
    
    // Getters i Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    // DODATO: getteri i setteri za nova polja
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    
    public LocalDateTime getTimerStartedAt() { return timerStartedAt; }
    public void setTimerStartedAt(LocalDateTime timerStartedAt) { this.timerStartedAt = timerStartedAt; }
    
    public Double getActualHours() { return actualHours; }
    public void setActualHours(Double actualHours) { this.actualHours = actualHours; }
    
    public long getActualMinutes() { return actualMinutes; }
    public void setActualMinutes(long actualMinutes) { this.actualMinutes = actualMinutes; }
    
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    
    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    
    public Double getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public WorkOrder getWorkOrder() { return workOrder; }
    public void setWorkOrder(WorkOrder workOrder) { this.workOrder = workOrder; }
    
    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }
    
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    
    // Helper metode
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isOverdue() {
        if (dueDate == null) return false;
        return dueDate.isBefore(LocalDateTime.now()) && status != TaskStatus.COMPLETED;
    }
    
    // Dodajte getter za deadline ako se koristi u nekom kodu
    public LocalDateTime getDeadline() {
        return dueDate;
    }
    
    public void setDeadline(LocalDateTime deadline) {
        this.dueDate = deadline;
    }
    
    public String getPriorityColor() {
        switch (priority) {
            case HIGH: return "red";
            case URGENT: return "red";
            case MEDIUM: return "orange";
            case LOW: return "green";
            default: return "gray";
        }
    }
    
    public String getStatusColor() {
        switch (status) {
            case NEW: return "blue";
            case IN_PROGRESS: return "yellow";
            case COMPLETED: return "green";
            case CANCELLED: return "gray";
            default: return "gray";
        }
    }
}