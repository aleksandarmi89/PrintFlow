package com.printflow.dto;

import com.printflow.entity.enums.AttachmentType;
import java.time.LocalDateTime;

public class AttachmentDTO {
    private Long id;
    private String fileName;
    private String storedFileName; // Dodaj ovo
    private String originalFileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private AttachmentType attachmentType;
    private String description;
    
    private Long workOrderId;
    private String workOrderTitle;
    private Long uploadedById;
    private String uploadedByFullName; // Promenjeno iz uploadedByName
    
    private LocalDateTime uploadedAt;
    private String thumbnailPath;
    private boolean active;
    
    // Dodaj ova nova polja
    private String formattedSize;
    private boolean isImage;
    
    // Konstruktori
    public AttachmentDTO() {}
    
    public AttachmentDTO(Long id, String fileName, String storedFileName, String originalFileName, 
                        String fileType, String mimeType, Long fileSize, AttachmentType attachmentType, 
                        String description, Long workOrderId, String workOrderTitle, Long uploadedById, 
                        String uploadedByFullName, LocalDateTime uploadedAt, String thumbnailPath, 
                        boolean active) {
        this.id = id;
        this.fileName = fileName;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.fileType = fileType;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.attachmentType = attachmentType;
        this.description = description;
        this.workOrderId = workOrderId;
        this.workOrderTitle = workOrderTitle;
        this.uploadedById = uploadedById;
        this.uploadedByFullName = uploadedByFullName;
        this.uploadedAt = uploadedAt;
        this.thumbnailPath = thumbnailPath;
        this.active = active;
    }
    
    // Getters i Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public AttachmentType getAttachmentType() { return attachmentType; }
    public void setAttachmentType(AttachmentType attachmentType) { this.attachmentType = attachmentType; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    
    public String getWorkOrderTitle() { return workOrderTitle; }
    public void setWorkOrderTitle(String workOrderTitle) { this.workOrderTitle = workOrderTitle; }
    
    public Long getUploadedById() { return uploadedById; }
    public void setUploadedById(Long uploadedById) { this.uploadedById = uploadedById; }
    
    public String getUploadedByFullName() { return uploadedByFullName; }
    public void setUploadedByFullName(String uploadedByFullName) { this.uploadedByFullName = uploadedByFullName; }
    
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    
    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public String getFormattedSize() { 
        if (fileSize == null) return "0 B";
        
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    public void setFormattedSize(String formattedSize) {
        this.formattedSize = formattedSize;
    }
    
    public boolean getIsImage() { 
        return mimeType != null && mimeType.startsWith("image/");
    }
    
    public void setIsImage(boolean isImage) {
        this.isImage = isImage;
    }
    
    public String getUploadedByName() {
        return uploadedByFullName; // Alias za backward compatibility
    }
    
    public void setUploadedByName(String uploadedByName) {
        this.uploadedByFullName = uploadedByName;
    }
}