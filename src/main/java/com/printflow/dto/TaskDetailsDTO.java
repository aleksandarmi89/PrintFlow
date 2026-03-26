package com.printflow.dto;

import java.time.LocalDateTime;
import java.util.Locale;

public class TaskDetailsDTO {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private LocalDateTime dueDate;  // Promenjeno iz deadline u dueDate za kompatibilnost
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int progress;
    private boolean overdue;
    private Double estimatedHours;
    private Double actualHours;  // DODATO
    private String notes;
    
    // WorkOrder info
    private Long workOrderId;
    private String orderNumber;
    private String orderTitle;  // DODATO
    
    // Client info
    private Long clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    
    // Assigned user info
    private Long assignedToId;
    private String assignedToName;
    private String assignedToEmail;
    
    // Created by info
    private Long createdById;
    private String createdByName;
    
    // Dodatna polja za timer i ostalo
    private LocalDateTime assignedAt;  // DODATO
    private LocalDateTime timerStartedAt;  // DODATO (opciono)
    private long actualMinutes;  // DODATO (opciono)
    
    // Konstruktori
    public TaskDetailsDTO() {
    }
    
    public TaskDetailsDTO(Long id, String title, String description, String status, String priority,
                         LocalDateTime dueDate, LocalDateTime createdAt, LocalDateTime updatedAt,
                         LocalDateTime startedAt, LocalDateTime completedAt, int progress, boolean overdue,
                         Double estimatedHours, Double actualHours, String notes, Long workOrderId, 
                         String orderNumber, String orderTitle, Long clientId, String clientName, 
                         String clientEmail, String clientPhone, Long assignedToId, String assignedToName, 
                         String assignedToEmail, Long createdById, String createdByName, 
                         LocalDateTime assignedAt, LocalDateTime timerStartedAt, Integer actualMinutes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.progress = progress;
        this.overdue = overdue;
        this.estimatedHours = estimatedHours;
        this.actualHours = actualHours;
        this.notes = notes;
        this.workOrderId = workOrderId;
        this.orderNumber = orderNumber;
        this.orderTitle = orderTitle;
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.clientPhone = clientPhone;
        this.assignedToId = assignedToId;
        this.assignedToName = assignedToName;
        this.assignedToEmail = assignedToEmail;
        this.createdById = createdById;
        this.createdByName = createdByName;
        this.assignedAt = assignedAt;
        this.timerStartedAt = timerStartedAt;
        this.actualMinutes = actualMinutes != null ? actualMinutes : 0;
    }
    
    // Getters i Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    
    public boolean isOverdue() { return overdue; }
    public void setOverdue(boolean overdue) { this.overdue = overdue; }
    
    public Double getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }
    
    public Double getActualHours() { return actualHours; }  // DODATO
    public void setActualHours(Double actualHours) { this.actualHours = actualHours; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    
    public String getOrderTitle() { return orderTitle; }  // DODATO
    public void setOrderTitle(String orderTitle) { this.orderTitle = orderTitle; }
    
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    
    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }
    
    public String getClientPhone() { return clientPhone; }
    public void setClientPhone(String clientPhone) { this.clientPhone = clientPhone; }
    
    public Long getAssignedToId() { return assignedToId; }
    public void setAssignedToId(Long assignedToId) { this.assignedToId = assignedToId; }
    
    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }
    
    public String getAssignedToEmail() { return assignedToEmail; }
    public void setAssignedToEmail(String assignedToEmail) { this.assignedToEmail = assignedToEmail; }
    
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long createdById) { this.createdById = createdById; }
    
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    
    public LocalDateTime getAssignedAt() { return assignedAt; }  // DODATO
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    
    public LocalDateTime getTimerStartedAt() { return timerStartedAt; }  // DODATO
    public void setTimerStartedAt(LocalDateTime timerStartedAt) { this.timerStartedAt = timerStartedAt; }
    
    public long getActualMinutes() { return actualMinutes; }  // DODATO
    public void setActualMinutes(long actualMinutes) { this.actualMinutes = actualMinutes; }
    
    // Helper metode
    public String getFormattedDeadline() {
        if (dueDate == null) return "Nije postavljen";
        return dueDate.toString(); // Možete koristiti DateTimeFormatter za lepši format
    }
    
    public String getPriorityClass() {
        if (priority == null) return "";
        switch (priority.toUpperCase(Locale.ROOT)) {
            case "HIGH": return "priority-high";
            case "URGENT": return "priority-urgent";
            case "MEDIUM": return "priority-medium";
            case "LOW": return "priority-low";
            default: return "";
        }
    }
    
    public String getStatusClass() {
        if (status == null) return "";
        switch (status.toUpperCase(Locale.ROOT)) {
            case "NEW": return "status-new";
            case "IN_PROGRESS": return "status-in-progress";
            case "COMPLETED": return "status-completed";
            case "CANCELLED": return "status-cancelled";
            default: return "";
        }
    }
    
    // Kompatibilnost sa starim kodom (ako postoji)
    public LocalDateTime getDeadline() { return dueDate; }
    public void setDeadline(LocalDateTime deadline) { this.dueDate = deadline; }
    
    // Dodatne helper metode
    public boolean isTimerRunning() {
        return timerStartedAt != null;
    }
    
    public boolean isAssigned() {
        return assignedToId != null;
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }
    
    public String getTimeSpent() {
        if (actualHours == null) return "0h";
        return String.format("%.1f h", actualHours);
    }
    
    @Override
    public String toString() {
        return "TaskDetailsDTO{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", progress=" + progress +
                ", dueDate=" + dueDate +
                ", assignedToName='" + assignedToName + '\'' +
                '}';
    }
}
