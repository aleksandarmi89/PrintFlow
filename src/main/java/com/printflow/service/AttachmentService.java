package com.printflow.service;

import com.printflow.dto.AttachmentDTO;
import com.printflow.entity.Attachment;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttachmentService {
    
    private final AttachmentRepository attachmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;
    
    private final String UPLOAD_DIR = "uploads";
    
    public AttachmentService(AttachmentRepository attachmentRepository, 
                            WorkOrderRepository workOrderRepository,
                            UserRepository userRepository) {
        this.attachmentRepository = attachmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
    }
    
    @Transactional
    public AttachmentDTO uploadFile(MultipartFile file, 
                                   Long workOrderId, 
                                   AttachmentType attachmentType,
                                   Long uploadedById,
                                   String description) throws IOException {
        
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
            .orElseThrow(() -> new RuntimeException("Work order not found with id: " + workOrderId));
        
        User uploadedBy = userRepository.findById(uploadedById)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + uploadedById));
        
        String originalFileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFileName);
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        String storedFileName = uniqueFileName;
        
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath);
        
        Attachment attachment = new Attachment();
        attachment.setFileName(storedFileName); // Ovo je storedFileName
        attachment.setOriginalFileName(originalFileName);
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachment.setAttachmentType(attachmentType);
        attachment.setWorkOrder(workOrder);
        attachment.setUploadedBy(uploadedBy);
        attachment.setDescription(description);
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setFilePath(filePath.toString());
        attachment.setActive(true);
        
        Attachment savedAttachment = attachmentRepository.save(attachment);
        
        return convertToDTO(savedAttachment);
    }
    
    public byte[] getAttachmentFile(Long attachmentId) throws IOException {
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        
        Path filePath = Paths.get(attachment.getFilePath());
        return Files.readAllBytes(filePath);
    }
    
    public AttachmentDTO getAttachmentById(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        
        return convertToDTO(attachment);
    }
    
    public List<AttachmentDTO> getAttachmentsByWorkOrder(Long workOrderId) {
        List<Attachment> attachments = attachmentRepository.findByWorkOrderIdAndActiveTrueOrderByUploadedAtDesc(workOrderId);
        return attachments.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<AttachmentDTO> getAttachmentsByWorkOrderAndType(Long workOrderId, AttachmentType type) {
        List<Attachment> attachments = attachmentRepository.findByWorkOrderIdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(
            workOrderId, type);
        return attachments.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteAttachment(Long attachmentId) throws IOException {
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        
        Path filePath = Paths.get(attachment.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
        
        attachment.setActive(false);
        attachmentRepository.save(attachment);
    }
    
    public AttachmentStatsDTO getAttachmentStats(Long workOrderId) {
        List<Attachment> attachments = attachmentRepository.findByWorkOrderIdAndActiveTrue(workOrderId);
        
        long totalFiles = attachments.size();
        long designFiles = attachments.stream()
            .filter(a -> a.getAttachmentType() == AttachmentType.DESIGN_SOURCE)
            .count();
        long previewFiles = attachments.stream()
            .filter(a -> a.getAttachmentType() == AttachmentType.DESIGN_PREVIEW)
            .count();
        long instructionFiles = attachments.stream()
            .filter(a -> a.getAttachmentType() == AttachmentType.INSTRUCTION)
            .count();
        
        long totalSize = attachments.stream()
            .mapToLong(Attachment::getFileSize)
            .sum();
        
        return new AttachmentStatsDTO(totalFiles, designFiles, previewFiles, instructionFiles, totalSize);
    }
    
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }
    
    private AttachmentDTO convertToDTO(Attachment attachment) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setId(attachment.getId());
        dto.setFileName(attachment.getFileName()); // Ovo je storedFileName
        dto.setStoredFileName(attachment.getFileName());
        dto.setOriginalFileName(attachment.getOriginalFileName());
        dto.setFileSize(attachment.getFileSize());
        dto.setMimeType(attachment.getMimeType());
        dto.setAttachmentType(attachment.getAttachmentType());
        dto.setDescription(attachment.getDescription());
        dto.setWorkOrderId(attachment.getWorkOrder().getId());
        
        if (attachment.getUploadedBy() != null) {
            dto.setUploadedById(attachment.getUploadedBy().getId());
            dto.setUploadedByFullName(attachment.getUploadedBy().getFullName());
        }
        
        dto.setUploadedAt(attachment.getUploadedAt());
        dto.setThumbnailPath(attachment.getThumbnailPath());
        dto.setActive(attachment.isActive());
        dto.setFormattedSize(formatFileSize(attachment.getFileSize()));
        dto.setIsImage(attachment.getMimeType() != null && attachment.getMimeType().startsWith("image/"));
        
        return dto;
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    public static class AttachmentStatsDTO {
        private final long totalFiles;
        private final long designFiles;
        private final long previewFiles;
        private final long instructionFiles;
        private final long totalSize;
        
        public AttachmentStatsDTO(long totalFiles, long designFiles, long previewFiles, 
                                 long instructionFiles, long totalSize) {
            this.totalFiles = totalFiles;
            this.designFiles = designFiles;
            this.previewFiles = previewFiles;
            this.instructionFiles = instructionFiles;
            this.totalSize = totalSize;
        }
        
        public long getTotalFiles() { return totalFiles; }
        public long getDesignFiles() { return designFiles; }
        public long getPreviewFiles() { return previewFiles; }
        public long getInstructionFiles() { return instructionFiles; }
        public long getTotalSize() { return totalSize; }
    }
}