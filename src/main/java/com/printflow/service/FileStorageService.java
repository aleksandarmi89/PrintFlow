package com.printflow.service;

import com.printflow.dto.AttachmentDTO;
import com.printflow.entity.Attachment;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.Task; // DODATO
import com.printflow.entity.User;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.TaskRepository; // DODATO (ako postoji)
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final String uploadDir;
    private final String allowedFileTypes;
    private final AttachmentRepository attachmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;

    public FileStorageService(
            @Value("${app.upload.dir:./uploads}") String uploadDir,
            @Value("${app.allowed-file-types:.ai,.psd,.pdf,.jpg,.jpeg,.png,.svg,.cdr,.eps,.doc,.docx,.xls,.xlsx,.txt}") String allowedFileTypes,
            AttachmentRepository attachmentRepository,
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository) {
        
        this.uploadDir = uploadDir;
        this.allowedFileTypes = allowedFileTypes;
        this.attachmentRepository = attachmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
        
        createUploadDirectory();
    }

    // --- UPLOAD ZA WORK ORDER ---
    public AttachmentDTO uploadFile(MultipartFile file, Long workOrderId, AttachmentType attachmentType, 
                                   Long userId, String description) throws IOException {
        
        validateFile(file);
        
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
            .orElseThrow(() -> new RuntimeException("Work order not found with id: " + workOrderId));
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        String subDir = "/order_" + workOrderId;
        return saveAttachmentProcess(file, workOrder, null, user, attachmentType, description, subDir);
    }

    // --- POMOĆNA METODA ZA PROCES SNIMANJA (DRY princip) ---
    private AttachmentDTO saveAttachmentProcess(MultipartFile file, WorkOrder workOrder, Task task, User user, 
                                               AttachmentType type, String desc, String subDir) throws IOException {
        
        String fullPathDir = uploadDir + subDir;
        createDirectoryIfNotExists(fullPathDir);
        
        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = generateUniqueFileName(originalFileName);
        String filePath = fullPathDir + "/" + uniqueFileName;
        
        Path targetLocation = Paths.get(filePath);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        
        String thumbnailPath = isImageFile(originalFileName) ? generateThumbnail(file, fullPathDir, uniqueFileName) : null;
        
        Attachment attachment = new Attachment();
        attachment.setFileName(uniqueFileName);
        attachment.setOriginalFileName(originalFileName);
        attachment.setFilePath(filePath);
        attachment.setFileType(getFileExtension(originalFileName));
        attachment.setMimeType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setAttachmentType(type);
        attachment.setDescription(desc);
        attachment.setWorkOrder(workOrder);
        attachment.setTask(task); // Postavljamo task ako postoji
        attachment.setUploadedBy(user);
        attachment.setThumbnailPath(thumbnailPath);
        attachment.setActive(true);
        attachment.setUploadedAt(LocalDateTime.now());
        
        Attachment savedAttachment = attachmentRepository.save(attachment);
        return convertToDTO(savedAttachment);
    }

    public List<AttachmentDTO> getAttachmentsByWorkOrder(Long workOrderId) {
        return attachmentRepository.findByWorkOrderIdAndActiveTrue(workOrderId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public List<AttachmentDTO> getAttachmentsByUser(Long userId) {
        // Pozivamo ispravljenu metodu iz Repository-ja
        return attachmentRepository.findByUploadedByIdAndActiveTrue(userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public AttachmentDTO getAttachmentById(Long attachmentId) {
        // Mora biti attachmentRepository, a ne workOrderRepository!
        Attachment attachment = attachmentRepository.findByIdAndActiveTrue(attachmentId)
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        return convertToDTO(attachment);
    }

    public void deleteAttachment(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        attachment.setActive(false);
        attachmentRepository.save(attachment);
    }

    // --- HELPER METODE ---
    
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

        dto.setUploadedAt(attachment.getUploadedAt());
        dto.setActive(attachment.isActive());
        return dto;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new RuntimeException("File is empty");
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        if (!allowedFileTypes.contains(extension)) {
            throw new RuntimeException("Type not allowed: " + extension);
        }
    }

    private String getFileExtension(String fileName) {
        return (fileName == null || !fileName.contains(".")) ? "" : fileName.substring(fileName.lastIndexOf("."));
    }

    private void createUploadDirectory() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload dir", e);
        }
    }

    private void createDirectoryIfNotExists(String directoryPath) {
        try {
            Files.createDirectories(Paths.get(directoryPath));
        } catch (IOException e) {
            throw new RuntimeException("Could not create directory: " + directoryPath, e);
        }
    }

    private String generateUniqueFileName(String originalFileName) {
        return UUID.randomUUID().toString() + getFileExtension(originalFileName);
    }

    private boolean isImageFile(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        return List.of(".jpg", ".jpeg", ".png", ".gif", ".svg").contains(ext);
    }

    private String generateThumbnail(MultipartFile file, String orderDir, String uniqueFileName) {
        try {
            String thumbnailName = "thumb_" + uniqueFileName;
            String thumbnailPath = orderDir + "/" + thumbnailName;
            
            // Koristimo Thumbnailator za promenu veličine na 200x200 piksela
            net.coobird.thumbnailator.Thumbnails.of(file.getInputStream())
                .size(200, 200)
                .outputFormat("jpg") // Thumbnails su uvek JPG radi uštede prostora
                .toFile(new java.io.File(thumbnailPath));
                
            log.info("Thumbnail generated: {}", thumbnailPath);
            return thumbnailPath;
        } catch (Exception e) {
            log.warn("Could not generate thumbnail for {}, using original instead. Error: {}", 
                     uniqueFileName, e.getMessage());
            return null; // Ako ne uspe, polje u bazi ostaje null
        }
    }

    public byte[] getAttachmentFile(Long attachmentId) throws IOException {
        // 1. Pronađi attachment u bazi
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        
        // 2. Uzmi putanju i proveri postojanje fajla
        Path filePath = Paths.get(attachment.getFilePath());
        if (!Files.exists(filePath)) {
            log.error("File not found on disk at path: {}", attachment.getFilePath());
            throw new IOException("Fajl ne postoji na serveru.");
        }
        
        // 3. Pročitaj sve bajtove fajla
        return Files.readAllBytes(filePath);
    }
    
    public byte[] getThumbnail(Long attachmentId) throws IOException {
        // 1. Pronađi attachment
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));
        
        // 2. Proveri da li postoji putanja do thumbnail-a
        String pathStr = attachment.getThumbnailPath();
        
        // Ako nema thumbnail-a, pokušaj da vratiš originalni fajl (ako je slika)
        if (pathStr == null || pathStr.isEmpty()) {
            pathStr = attachment.getFilePath();
        }
        
        Path path = Paths.get(pathStr);
        
        if (!Files.exists(path)) {
            throw new IOException("Thumbnail file not found on disk.");
        }
        
        return Files.readAllBytes(path);
    }
}