package com.printflow.dto;

import java.time.LocalDateTime;

public class TaskActivityDTO {
    private Long id;  // DODATO
    private String action;  // Promenjeno iz activityType u action
    private String description;
    private LocalDateTime createdAt;  // Promenjeno iz timestamp u createdAt
    private String userName;  // Promenjeno iz performedBy u userName
    private Long userId;  // DODATO
    private Long taskId;  // DODATO (opciono, ako želite da znate kom tasku pripada)
    
    // Konstruktori
    public TaskActivityDTO() {}
    
    public TaskActivityDTO(Long id, String action, String description, LocalDateTime createdAt, 
                          String userName, Long userId, Long taskId) {
        this.id = id;
        this.action = action;
        this.description = description;
        this.createdAt = createdAt;
        this.userName = userName;
        this.userId = userId;
        this.taskId = taskId;
    }
    
    // Getters i Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    
    // Helper metode za kompatibilnost sa starim kodom (ako postoji)
    public String getActivityType() { return action; }
    public void setActivityType(String activityType) { this.action = activityType; }
    
    public LocalDateTime getTimestamp() { return createdAt; }
    public void setTimestamp(LocalDateTime timestamp) { this.createdAt = timestamp; }
    
    public String getPerformedBy() { return userName; }
    public void setPerformedBy(String performedBy) { this.userName = performedBy; }
    
    @Override
    public String toString() {
        return "TaskActivityDTO{" +
                "id=" + id +
                ", action='" + action + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", userName='" + userName + '\'' +
                ", userId=" + userId +
                ", taskId=" + taskId +
                '}';
    }
}