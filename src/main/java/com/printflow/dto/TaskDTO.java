package com.printflow.dto;

import java.time.LocalDateTime;
import java.util.Locale;

public class TaskDTO {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private LocalDateTime dueDate;  // Promenjeno iz deadline u dueDate za konzistentnost
    private LocalDateTime createdAt;
    private Integer progress;  // Promenjeno iz int u Integer
    private boolean overdue;
    private Double estimatedHours;
    private Long workOrderId;
    private String orderNumber;
    private Long orderId;  // DODATO
    private String clientName;
    private Long assignedToId;
    private String assignedToName;
    private int attachmentsCount;
    
    // Dodatna polja za konzistentnost
    private LocalDateTime updatedAt;  // DODATO
    
    // Konstruktori
    public TaskDTO() {}
    
    public TaskDTO(Long id, String title, String description, String status, String priority, 
                   LocalDateTime dueDate, LocalDateTime createdAt, LocalDateTime updatedAt,
                   Integer progress, boolean overdue, Double estimatedHours, Long workOrderId, 
                   String orderNumber, Long orderId, String clientName, Long assignedToId, 
                   String assignedToName, int attachmentsCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.progress = progress;
        this.overdue = overdue;
        this.estimatedHours = estimatedHours;
        this.workOrderId = workOrderId;
        this.orderNumber = orderNumber;
        this.orderId = orderId;
        this.clientName = clientName;
        this.assignedToId = assignedToId;
        this.assignedToName = assignedToName;
        this.attachmentsCount = attachmentsCount;
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
    
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    
    public boolean isOverdue() { return overdue; }
    public void setOverdue(boolean overdue) { this.overdue = overdue; }
    
    public Double getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }
    
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    
    public Long getOrderId() { return orderId; }  // DODATO
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    
    public Long getAssignedToId() { return assignedToId; }
    public void setAssignedToId(Long assignedToId) { this.assignedToId = assignedToId; }
    
    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }
    
    public int getAttachmentsCount() { return attachmentsCount; }
    public void setAttachmentsCount(int attachmentsCount) { this.attachmentsCount = attachmentsCount; }
    
    // Kompatibilnost sa starim kodom
    public LocalDateTime getDeadline() { return dueDate; }
    public void setDeadline(LocalDateTime deadline) { this.dueDate = deadline; }
    
    // Ovaj setter je bio pogrešan - treba da prima Double, ne int
    public void setEstimatedHours(int hours) { 
        this.estimatedHours = (double) hours; 
    }
    
    // Helper metode
    public boolean isHighPriority() {
        return "HIGH".equalsIgnoreCase(priority) || "URGENT".equalsIgnoreCase(priority);
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }
    
    public boolean isInProgress() {
        return "IN_PROGRESS".equalsIgnoreCase(status);
    }
    
    public String getPriorityColor() {
        if (priority == null) return "gray";
        switch (priority.toUpperCase(Locale.ROOT)) {
            case "HIGH": return "red";
            case "URGENT": return "red";
            case "MEDIUM": return "orange";
            case "LOW": return "green";
            default: return "gray";
        }
    }
    
    public String getStatusColor() {
        if (status == null) return "gray";
        switch (status.toUpperCase(Locale.ROOT)) {
            case "NEW": return "blue";
            case "IN_PROGRESS": return "yellow";
            case "COMPLETED": return "green";
            case "CANCELLED": return "gray";
            default: return "gray";
        }
    }
    
    @Override
    public String toString() {
        return "TaskDTO{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", progress=" + progress +
                ", dueDate=" + dueDate +
                ", assignedToName='" + assignedToName + '\'' +
                '}';
    }
}
