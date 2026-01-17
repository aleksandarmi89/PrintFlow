package com.printflow.controller;

import com.printflow.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileStorageService fileStorageService;
    
    
    
    public FileController(FileStorageService fileStorageService) {
		super();
		this.fileStorageService = fileStorageService;
	}

	@GetMapping("/download/{attachmentId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long attachmentId) {
        try {
            com.printflow.dto.AttachmentDTO attachment = fileStorageService.getAttachmentById(attachmentId);
            byte[] fileContent = fileStorageService.getAttachmentFile(attachmentId);
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                       "attachment; filename=\"" + attachment.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .contentLength(attachment.getFileSize())
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/thumbnail/{attachmentId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long attachmentId) {
        try {
            com.printflow.dto.AttachmentDTO attachment = fileStorageService.getAttachmentById(attachmentId);
            byte[] thumbnailContent = fileStorageService.getThumbnail(attachmentId);
            
            ByteArrayResource resource = new ByteArrayResource(thumbnailContent);
            
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG) // Pretpostavka da su thumbnail-i JPEG
                .contentLength(thumbnailContent.length)
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<?> deleteFile(@PathVariable Long attachmentId) {
        try {
            fileStorageService.deleteAttachment(attachmentId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting file: " + e.getMessage());
        }
    }
}