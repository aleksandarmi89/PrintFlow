package com.printflow.service;

import com.printflow.dto.AttachmentDTO;
import com.printflow.entity.Attachment;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.Company;
import com.printflow.entity.Task; // DODATO
import com.printflow.entity.Comment;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.TaskRepository; // DODATO (ako postoji)
import com.printflow.repository.TaskActivityRepository;
import com.printflow.entity.enums.AuditAction;
import com.printflow.entity.TaskActivity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import com.printflow.storage.FileStorage;
import com.printflow.storage.StoredFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional
public class FileStorageService {
    private final String allowedFileTypes;
    private final String publicAllowedFileTypes;
    private final long publicMaxFileSize;
    private final int publicMaxFilesPerOrder;
    private final long publicMaxTotalSize;
    private final long commentMaxFileSize;
    private final String commentAllowedMimeTypes;
    private final AttachmentRepository attachmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final TenantGuard tenantGuard;
    private final FileStorage fileStorage;
    private final PlanLimitService planLimitService;
    private final BillingAccessService billingAccessService;
    private final PublicTokenService publicTokenService;

    public FileStorageService(
            @Value("${app.allowed-file-types:.ai,.psd,.pdf,.jpg,.jpeg,.png,.svg,.cdr,.eps,.doc,.docx,.xls,.xlsx,.txt}") String allowedFileTypes,
            @Value("${app.upload.public-allowed-file-types:.pdf,.jpg,.jpeg,.png,.svg,.ai,.psd}") String publicAllowedFileTypes,
            @Value("${app.upload.public-max-file-size:10485760}") long publicMaxFileSize,
            @Value("${app.upload.public-max-files-per-order:10}") int publicMaxFilesPerOrder,
            @Value("${app.upload.public-max-total-size:52428800}") long publicMaxTotalSize,
            @Value("${app.upload.comment-max-file-size:10485760}") long commentMaxFileSize,
            @Value("${app.upload.comment-allowed-mime-types:image/jpeg,image/png,image/webp,image/gif,image/svg+xml,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/postscript,application/vnd.adobe.photoshop}") String commentAllowedMimeTypes,
            AttachmentRepository attachmentRepository,
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository,
            TaskRepository taskRepository,
            TaskActivityRepository taskActivityRepository,
            AuditLogService auditLogService,
            NotificationService notificationService,
            TenantGuard tenantGuard,
            FileStorage fileStorage,
            PlanLimitService planLimitService,
            BillingAccessService billingAccessService,
            PublicTokenService publicTokenService) {
        
        this.allowedFileTypes = allowedFileTypes;
        this.publicAllowedFileTypes = publicAllowedFileTypes;
        this.publicMaxFileSize = publicMaxFileSize;
        this.publicMaxFilesPerOrder = publicMaxFilesPerOrder;
        this.publicMaxTotalSize = publicMaxTotalSize;
        this.commentMaxFileSize = commentMaxFileSize;
        this.commentAllowedMimeTypes = commentAllowedMimeTypes;
        this.attachmentRepository = attachmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.taskActivityRepository = taskActivityRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.tenantGuard = tenantGuard;
        this.fileStorage = fileStorage;
        this.planLimitService = planLimitService;
        this.billingAccessService = billingAccessService;
        this.publicTokenService = publicTokenService;
    }

    // --- UPLOAD ZA WORK ORDER ---
    public AttachmentDTO uploadFile(MultipartFile file, Long workOrderId, AttachmentType attachmentType, 
                                   Long userId, String description) throws IOException {
        
        validateFile(file);
        
        WorkOrder workOrder = workOrderRepository.findByIdAndCompany_Id(workOrderId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Work order not found with id: " + workOrderId));
        
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        String subDir = "/order_" + workOrderId;
        return saveAttachmentProcess(file, workOrder, null, null, user, attachmentType, description, subDir);
    }

    // --- UPLOAD ZA TASK (PROOF OF WORK) ---
    public AttachmentDTO uploadTaskFile(MultipartFile file, Long taskId, AttachmentType attachmentType,
                                       Long userId, String description) throws IOException {
        validateFile(file);

        Task task = taskRepository.findByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
        WorkOrder workOrder = task.getWorkOrder();
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        String subDir = workOrder != null
            ? ("/order_" + workOrder.getId() + "/task_" + taskId)
            : ("/task_" + taskId);
        AttachmentDTO dto = saveAttachmentProcess(file, workOrder, task, null, user, attachmentType, description, subDir);
        Company company = workOrder != null ? workOrder.getCompany() : task.getCompany();
        auditLogService.log(AuditAction.UPLOAD, "Task", taskId,
            null, dto.getOriginalFileName(), "Worker uploaded proof of work", company);
        TaskActivity activity = new TaskActivity();
        activity.setTask(task);
        activity.setUser(user);
        activity.setAction("FILE_UPLOAD");
        activity.setDescription("File uploaded: " + dto.getOriginalFileName());
        activity.setCreatedAt(LocalDateTime.now());
        taskActivityRepository.save(activity);
        if (company != null) {
            List<User> admins = userRepository.findByCompany_IdAndRoleInAndActiveTrue(
                company.getId(), List.of(Role.ADMIN, Role.MANAGER));
            List<Long> adminIds = admins.stream().map(User::getId).toList();
            if (!adminIds.isEmpty()) {
                String message = "Worker uploaded proof for task: " + task.getTitle()
                    + ". Image: /api/files/download/" + dto.getId();
                notificationService.createNotificationForUsers(
                    adminIds,
                    "Proof uploaded",
                    message,
                    "TASK_PROOF",
                    "/admin/tasks/" + taskId
                );
            }
        }
        return dto;
    }

    // --- PUBLIC UPLOAD (CLIENT) ---
    public AttachmentDTO uploadPublicFile(MultipartFile file, Long workOrderId, String orderToken, AttachmentType attachmentType,
                                         String description) throws IOException {
        validatePublicFile(file, workOrderId);

        Long companyId = workOrderRepository.findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(
                orderToken,
                publicTokenService.now()
            )
            .orElseThrow(() -> new RuntimeException("Invalid order token"));
        WorkOrder workOrder = workOrderRepository.findByIdAndCompany_Id(workOrderId, companyId)
            .orElseThrow(() -> new RuntimeException("Work order not found with id: " + workOrderId));
        if (tenantGuard.isAuthenticated()) {
            tenantGuard.assertSameTenant(workOrder.getCompany(), "Work order");
        }

        String subDir = "/order_" + workOrderId;
        AttachmentDTO dto = saveAttachmentProcess(file, workOrder, null, null, null, attachmentType, description, subDir);
        auditLogService.log(AuditAction.UPLOAD, "WorkOrder", workOrderId,
            null, dto.getOriginalFileName(), "Client uploaded reference file",
            workOrder.getCompany());
        return dto;
    }

    public AttachmentDTO uploadCommentFile(MultipartFile file, Task task, Comment comment, Long userId) throws IOException {
        if (task == null || task.getWorkOrder() == null) {
            throw new RuntimeException("Task is not linked to a work order");
        }
        validateCommentFile(file);
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        String subDir = "/order_" + task.getWorkOrder().getId() + "/task_" + task.getId() + "/comment_" + comment.getId();
        AttachmentDTO dto = saveAttachmentProcess(file, task.getWorkOrder(), task, comment, user,
            AttachmentType.COMMENT, "Comment attachment", subDir);
        TaskActivity activity = new TaskActivity();
        activity.setTask(task);
        activity.setUser(user);
        activity.setAction("FILE_UPLOAD");
        activity.setDescription("Comment attachment uploaded: " + dto.getOriginalFileName());
        activity.setCreatedAt(LocalDateTime.now());
        taskActivityRepository.save(activity);
        return dto;
    }

    // --- POMOĆNA METODA ZA PROCES SNIMANJA (DRY princip) ---
    private AttachmentDTO saveAttachmentProcess(MultipartFile file, WorkOrder workOrder, Task task, Comment comment, User user, 
                                               AttachmentType type, String desc, String subDir) throws IOException {
        
        String originalFileName = file.getOriginalFilename();
        Company company = workOrder != null ? workOrder.getCompany() : (task != null ? task.getCompany() : null);
        if (company == null) {
            throw new RuntimeException("Company not resolved for attachment");
        }
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        planLimitService.assertStorageLimit(company, file.getSize());
        boolean generateThumb = isImageFile(originalFileName);
        StoredFile stored = fileStorage.store(file, subDir, generateThumb);
        
        Attachment attachment = new Attachment();
        attachment.setFileName(stored.getFileName());
        attachment.setOriginalFileName(originalFileName);
        attachment.setFilePath(stored.getFilePath());
        attachment.setFileType(getFileExtension(originalFileName));
        attachment.setMimeType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setAttachmentType(type);
        attachment.setDescription(desc);
        attachment.setWorkOrder(workOrder);
        attachment.setTask(task); // Postavljamo task ako postoji
        attachment.setComment(comment);
        attachment.setCompany(company);
        attachment.setUploadedBy(user);
        attachment.setThumbnailPath(stored.getThumbnailPath());
        attachment.setActive(true);
        attachment.setUploadedAt(LocalDateTime.now());
        if (attachment.getApprovalToken() == null || attachment.getApprovalToken().isBlank()) {
            attachment.setApprovalToken(java.util.UUID.randomUUID().toString());
        }
        
        Attachment savedAttachment = attachmentRepository.save(attachment);
        return convertToDTO(savedAttachment);
    }

    public List<AttachmentDTO> getAttachmentsByWorkOrder(Long workOrderId) {
        if (shouldEnforceTenant()) {
            Long companyId = tenantGuard.requireCompanyId();
            workOrderRepository.findByIdAndCompany_Id(workOrderId, companyId)
                .orElseThrow(() -> new RuntimeException("Work order not found"));
            return attachmentRepository.findByWorkOrderIdAndCompany_IdAndActiveTrue(workOrderId, companyId).stream()
                .filter(this::canAccessAttachment)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        return attachmentRepository.findByWorkOrderIdAndActiveTrue(workOrderId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public List<AttachmentDTO> getAttachmentsByTask(Long taskId) {
        if (shouldEnforceTenant()) {
            taskRepository.findByIdAndCompany_Id(taskId, tenantGuard.requireCompanyId())
                .orElseThrow(() -> new RuntimeException("Task not found"));
        }
        return attachmentRepository.findByTaskIdAndCompany_IdAndActiveTrueOrderByUploadedAtDesc(taskId, tenantGuard.requireCompanyId()).stream()
            .filter(this::canAccessAttachment)
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public List<AttachmentDTO> getAttachmentsByComment(Long commentId) {
        return attachmentRepository.findByCommentIdAndCompany_IdAndActiveTrueOrderByUploadedAtDesc(commentId, tenantGuard.requireCompanyId()).stream()
            .filter(this::canAccessAttachment)
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public long countClientFiles(Long workOrderId) {
        return attachmentRepository.countByWorkOrderIdAndAttachmentTypeAndActiveTrue(
            workOrderId, AttachmentType.CLIENT_FILE);
    }

    public List<AttachmentDTO> getAttachmentsByUser(Long userId) {
        // Pozivamo ispravljenu metodu iz Repository-ja
        return attachmentRepository.findByUploadedByIdAndActiveTrue(userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public AttachmentDTO getAttachmentById(Long attachmentId) {
        // Mora biti attachmentRepository, a ne workOrderRepository!
        Attachment attachment = attachmentRepository.findByIdAndCompany_IdAndActiveTrue(attachmentId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        assertAttachmentAccess(attachment);
        return convertToDTO(attachment);
    }

    public void deleteAttachment(Long attachmentId) {
        Attachment attachment = attachmentRepository.findByIdAndCompany_Id(attachmentId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        assertAttachmentAccess(attachment);
        // Delete files from disk first
        fileStorage.deleteIfExists(attachment.getFilePath());
        fileStorage.deleteIfExists(attachment.getThumbnailPath());
        attachment.setActive(false);
        attachmentRepository.save(attachment);
    }

    public void deleteCommentAttachment(Long attachmentId, Long commentId, Long userId, boolean allowAdminOverride) {
        Attachment attachment = attachmentRepository
            .findByIdAndComment_IdAndCompany_Id(attachmentId, commentId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        assertAttachmentAccess(attachment);
        if (!allowAdminOverride && (attachment.getUploadedBy() == null || !attachment.getUploadedBy().getId().equals(userId))) {
            throw new RuntimeException("You can delete only your own attachments");
        }
        deleteAttachment(attachmentId);
        if (attachment.getTask() != null && attachment.getUploadedBy() != null) {
            TaskActivity activity = new TaskActivity();
            activity.setTask(attachment.getTask());
            activity.setUser(attachment.getUploadedBy());
            activity.setAction("FILE_DELETED");
            activity.setDescription("Comment attachment deleted: " + attachment.getOriginalFileName());
            activity.setCreatedAt(LocalDateTime.now());
            taskActivityRepository.save(activity);
        }
        if (attachment.getTask() != null) {
            auditLogService.log(AuditAction.DELETE, "Attachment", attachment.getId(),
                attachment.getOriginalFileName(), null, "Comment attachment deleted", attachment.getTask().getCompany());
        }
    }

    public void deleteCommentAttachments(List<Long> attachmentIds, Long commentId, Long userId, boolean allowAdminOverride) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        Long companyId = tenantGuard.requireCompanyId();
        List<Long> uniqueIds = attachmentIds.stream()
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (uniqueIds.isEmpty()) {
            return;
        }
        List<Attachment> attachments = attachmentRepository.findByIdInAndComment_IdAndCompany_Id(uniqueIds, commentId, companyId);
        if (attachments.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more attachments not found");
        }
        for (Attachment attachment : attachments) {
            assertAttachmentAccess(attachment);
            if (!allowAdminOverride && (attachment.getUploadedBy() == null || !attachment.getUploadedBy().getId().equals(userId))) {
                throw new RuntimeException("You can delete only your own attachments");
            }
            deleteAttachment(attachment.getId());
            if (attachment.getTask() != null) {
                auditLogService.log(AuditAction.DELETE, "Attachment", attachment.getId(),
                    attachment.getOriginalFileName(), null, "Comment attachment deleted (bulk)", attachment.getTask().getCompany());
            }
        }
    }

    // --- HELPER METODE ---

    private boolean shouldEnforceTenant() {
        return tenantGuard.isAuthenticated() && !tenantGuard.isSuperAdmin();
    }

    private boolean canAccessAttachment(Attachment attachment) {
        if (!shouldEnforceTenant()) {
            return true;
        }
        Long companyId = tenantGuard.requireCompanyId();
        Long attachmentCompanyId = resolveAttachmentCompanyId(attachment);
        return attachmentCompanyId != null && attachmentCompanyId.equals(companyId);
    }

    private void assertAttachmentAccess(Attachment attachment) {
        if (!canAccessAttachment(attachment)) {
            throw new AccessDeniedException("Attachment does not belong to your company.");
        }
    }

    private Long resolveAttachmentCompanyId(Attachment attachment) {
        if (attachment.getCompany() != null) {
            return attachment.getCompany().getId();
        }
        if (attachment.getWorkOrder() != null && attachment.getWorkOrder().getCompany() != null) {
            return attachment.getWorkOrder().getCompany().getId();
        }
        if (attachment.getTask() != null && attachment.getTask().getCompany() != null) {
            return attachment.getTask().getCompany().getId();
        }
        if (attachment.getComment() != null && attachment.getComment().getTask() != null &&
            attachment.getComment().getTask().getCompany() != null) {
            return attachment.getComment().getTask().getCompany().getId();
        }
        return null;
    }
    
    private AttachmentDTO convertToDTO(Attachment attachment) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setId(attachment.getId());
        dto.setFileName(attachment.getFileName());
        dto.setOriginalFileName(attachment.getOriginalFileName());
        dto.setFileType(attachment.getFileType());
        dto.setMimeType(attachment.getMimeType());
        dto.setFileSize(attachment.getFileSize());
        dto.setAttachmentType(attachment.getAttachmentType());
        dto.setDescription(attachment.getDescription());
        
        if (attachment.getWorkOrder() != null) {
            dto.setWorkOrderId(attachment.getWorkOrder().getId());
            dto.setWorkOrderTitle(attachment.getWorkOrder().getTitle() != null ? 
                attachment.getWorkOrder().getTitle() : "Order #" + attachment.getWorkOrder().getId());
        }

        if (attachment.getUploadedBy() != null) {
            dto.setUploadedById(attachment.getUploadedBy().getId());
            dto.setUploadedByName(attachment.getUploadedBy().getFullName());
        }
        if (attachment.getComment() != null) {
            dto.setCommentId(attachment.getComment().getId());
        }

        dto.setUploadedAt(attachment.getUploadedAt());
        dto.setActive(attachment.isActive());
        return dto;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!isExtensionAllowed(extension, allowedFileTypes)) {
            throw new RuntimeException("Type not allowed: " + extension);
        }
    }

    private void validatePublicFile(MultipartFile file, Long workOrderId) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new RuntimeException("File name is missing");
        }
        if (originalFileName.length() > 255) {
            throw new RuntimeException("File name is too long");
        }
        if (originalFileName.contains("..") || originalFileName.contains("/") || originalFileName.contains("\\")) {
            throw new RuntimeException("Invalid file name");
        }
        for (int i = 0; i < originalFileName.length(); i++) {
            if (Character.isISOControl(originalFileName.charAt(i))) {
                throw new RuntimeException("Invalid file name");
            }
        }
        if (file.getSize() > publicMaxFileSize) {
            throw new RuntimeException("File too large. Max size is " + publicMaxFileSize + " bytes");
        }
        String extension = getFileExtension(originalFileName).toLowerCase(Locale.ROOT);
        if (!isExtensionAllowed(extension, publicAllowedFileTypes)) {
            throw new RuntimeException("Type not allowed: " + extension);
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            if (!"application/octet-stream".equals(normalized)) {
                if (List.of(".jpg", ".jpeg", ".png", ".svg").contains(extension) && !normalized.startsWith("image/")) {
                    throw new RuntimeException("File MIME type does not match image extension");
                }
                if (".pdf".equals(extension) && !"application/pdf".equals(normalized)) {
                    throw new RuntimeException("File MIME type does not match PDF extension");
                }
            }
        }
        long existingCount = attachmentRepository.countByWorkOrderIdAndAttachmentTypeAndActiveTrue(
            workOrderId, AttachmentType.CLIENT_FILE);
        if (existingCount >= publicMaxFilesPerOrder) {
            throw new RuntimeException("Upload limit reached for this order");
        }
        Long existingSize = attachmentRepository.sumFileSizeByWorkOrderId(workOrderId);
        long currentTotal = existingSize != null ? existingSize : 0L;
        if (currentTotal + file.getSize() > publicMaxTotalSize) {
            throw new RuntimeException("Total upload size exceeds limit for this order");
        }
    }

    private void validateCommentFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        if (file.getSize() > commentMaxFileSize) {
            throw new RuntimeException("File too large. Max size is " + commentMaxFileSize + " bytes");
        }
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!isExtensionAllowed(extension, allowedFileTypes)) {
            throw new RuntimeException("Type not allowed: " + extension);
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            if (!isMimeAllowed(normalized, commentAllowedMimeTypes) && !normalized.startsWith("image/")) {
                throw new RuntimeException("MIME type not allowed: " + contentType);
            }
        }
    }

    private String getFileExtension(String fileName) {
        return (fileName == null || !fileName.contains(".")) ? "" : fileName.substring(fileName.lastIndexOf("."));
    }

    private boolean isImageFile(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase(Locale.ROOT);
        return List.of(".jpg", ".jpeg", ".png", ".gif", ".svg").contains(ext);
    }

    private boolean isExtensionAllowed(String extension, String allowedList) {
        if (extension == null || extension.isBlank()) {
            return false;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        for (String token : allowedList.split(",")) {
            String allowed = token.trim().toLowerCase(Locale.ROOT);
            if (!allowed.isEmpty() && allowed.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMimeAllowed(String mime, String allowedList) {
        if (mime == null || mime.isBlank()) {
            return false;
        }
        String normalized = mime.toLowerCase(Locale.ROOT);
        for (String token : allowedList.split(",")) {
            String allowed = token.trim().toLowerCase(Locale.ROOT);
            if (!allowed.isEmpty() && allowed.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public byte[] getAttachmentFile(Long attachmentId) throws IOException {
        // 1. Pronađi attachment u bazi
        Attachment attachment = attachmentRepository.findByIdAndCompany_IdAndActiveTrue(attachmentId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        assertAttachmentAccess(attachment);
        return fileStorage.load(attachment.getFilePath());
    }

    public byte[] getAttachmentFileByApprovalToken(String approvalToken, Long clientId) throws IOException {
        Attachment attachment = attachmentRepository
            .findByApprovalTokenAndWorkOrder_Client_IdAndActiveTrue(approvalToken, clientId)
            .orElseThrow(() -> new RuntimeException("Attachment not found"));
        return fileStorage.load(attachment.getFilePath());
    }
    
    public byte[] getThumbnail(Long attachmentId) throws IOException {
        // 1. Pronađi attachment
        Attachment attachment = attachmentRepository.findByIdAndCompany_IdAndActiveTrue(attachmentId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        assertAttachmentAccess(attachment);
        return fileStorage.loadThumbnail(attachment.getThumbnailPath(), attachment.getFilePath());
    }

    public byte[] getThumbnailPublic(Long attachmentId, String orderToken) throws IOException {
        if (orderToken == null || orderToken.isBlank()) {
            throw new RuntimeException("Missing order token");
        }
        Attachment attachment = attachmentRepository
            .findByIdAndWorkOrder_PublicTokenAndWorkOrder_PublicTokenExpiresAtAfterAndAttachmentTypeAndActiveTrue(
                attachmentId,
                orderToken,
                publicTokenService.now(),
                AttachmentType.DESIGN_PREVIEW
            )
            .orElseThrow(() -> new RuntimeException("Attachment not found"));
        return fileStorage.loadThumbnail(attachment.getThumbnailPath(), attachment.getFilePath());
    }
}
