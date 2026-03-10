package com.printflow.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class NotificationDTO {
    private Long id;
    private String title;
    private String message;
    private String type;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private Long userId;
    private Long relatedTaskId;
    private Long relatedOrderId;
    private String link;
    
    public NotificationDTO() {
        // Default constructor
    }
    
    public NotificationDTO(Long id, String title, String message, String type, boolean read, 
                          LocalDateTime createdAt, LocalDateTime readAt, Long userId, 
                          Long relatedTaskId, Long relatedOrderId, String link) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.read = read;
        this.createdAt = createdAt;
        this.readAt = readAt;
        this.userId = userId;
        this.relatedTaskId = relatedTaskId;
        this.relatedOrderId = relatedOrderId;
        this.link = link;
    }
    
    // Lombok @Data generiše gettere i settere, ali ako želite eksplicitno, evo ih:
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public boolean isRead() {
        return read;
    }
    
    public void setRead(boolean read) {
        this.read = read;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getReadAt() {
        return readAt;
    }
    
    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Long getRelatedTaskId() {
        return relatedTaskId;
    }
    
    public void setRelatedTaskId(Long relatedTaskId) {
        this.relatedTaskId = relatedTaskId;
    }
    
    public Long getRelatedOrderId() {
        return relatedOrderId;
    }
    
    public void setRelatedOrderId(Long relatedOrderId) {
        this.relatedOrderId = relatedOrderId;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
    
    // Helper metode
    
    public boolean hasRelatedTask() {
        return relatedTaskId != null;
    }
    
    public boolean hasRelatedOrder() {
        return relatedOrderId != null;
    }
    
    public boolean isUnread() {
        return !read;
    }
    
    public String getStatus() {
        return read ? "Read" : "Unread";
    }
    
    @Override
    public String toString() {
        return "NotificationDTO{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", read=" + read +
                ", createdAt=" + createdAt +
                ", userId=" + userId +
                '}';
    }
}
