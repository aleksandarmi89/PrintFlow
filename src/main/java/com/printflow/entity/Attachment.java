package com.printflow.entity;

import com.printflow.entity.enums.AttachmentType;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
@Filter(name = "tenantFilter", condition = "tenant_id = :companyId")
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "original_file_name")
    private String originalFileName;
    
    @Column(name = "file_type")
    private String fileType;
    
    @Column(name = "mime_type")
    private String mimeType;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false)
    private AttachmentType attachmentType = AttachmentType.OTHER;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;
    
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();
    
    @Column(name = "thumbnail_path")
    private String thumbnailPath;
    
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "approved", nullable = false)
    private boolean approved = false;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approval_ip")
    private String approvalIp;

    @Column(name = "approval_token", unique = true)
    private String approvalToken;
    
    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id") // Može biti nullable ako attachment ide na WorkOrder, a ne na Task
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Company company;

    // I dodajte getter i setter:
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public Comment getComment() { return comment; }
    public void setComment(Comment comment) { this.comment = comment; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Attachment() {
    }

    public Attachment(Long id, String fileName, String filePath, String originalFileName, String fileType,
                     String mimeType, Long fileSize, AttachmentType attachmentType, String description, 
                     WorkOrder workOrder, User uploadedBy, LocalDateTime uploadedAt, String thumbnailPath, 
                     boolean active,Task task, Comment comment) {
        this.id = id;
        this.fileName = fileName;
        this.filePath = filePath;
        this.originalFileName = originalFileName;
        this.fileType = fileType;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.attachmentType = attachmentType;
        this.description = description;
        this.workOrder = workOrder;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.thumbnailPath = thumbnailPath;
        this.active = active;
        this.task=task;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public AttachmentType getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(AttachmentType attachmentType) {
        this.attachmentType = attachmentType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public void setWorkOrder(WorkOrder workOrder) {
        this.workOrder = workOrder;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getApprovalIp() { return approvalIp; }
    public void setApprovalIp(String approvalIp) { this.approvalIp = approvalIp; }
    public String getApprovalToken() { return approvalToken; }
    public void setApprovalToken(String approvalToken) { this.approvalToken = approvalToken; }

    // Dodajte ove metode za kompatibilnost sa AttachmentService
    public String getStoredFileName() {
        return this.fileName; // fileName je storedFileName
    }
    
    public void setStoredFileName(String storedFileName) {
        this.fileName = storedFileName; // fileName je storedFileName
    }
    
    // Dodatne helper metode
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
    
    public String getFileExtension() {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf("."));
    }
    
    public String getFormattedFileSize() {
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
    
    // Dodajte ovu metodu za dobijanje punog imena uploadera
    public String getUploaderFullName() {
        if (uploadedBy != null) {
            return uploadedBy.getFullName();
        }
        return null;
    }
    
    // Override toString za debugging
    @Override
    public String toString() {
        return "Attachment{" +
               "id=" + id +
               ", fileName='" + fileName + '\'' +
               ", originalFileName='" + originalFileName + '\'' +
               ", attachmentType=" + attachmentType +
               ", workOrderId=" + (workOrder != null ? workOrder.getId() : null) +
               ", active=" + active +
               '}';
    }
}
