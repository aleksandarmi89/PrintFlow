package com.printflow.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CommentDTO {
    private Long id;
    private Long taskId;
    private Long userId;
    private String userFullName;
    private String userInitials;
    private String content;
    private LocalDateTime createdAt;
    private List<AttachmentDTO> attachments;

    public CommentDTO() {}

    public CommentDTO(Long id, Long taskId, Long userId, String userFullName, String userInitials,
                      String content, LocalDateTime createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.userId = userId;
        this.userFullName = userFullName;
        this.userInitials = userInitials;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }

    public String getUserInitials() { return userInitials; }
    public void setUserInitials(String userInitials) { this.userInitials = userInitials; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<AttachmentDTO> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentDTO> attachments) { this.attachments = attachments; }
}
