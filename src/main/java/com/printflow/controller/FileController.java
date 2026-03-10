package com.printflow.controller;

import com.printflow.service.FileStorageService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
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

    @GetMapping("/view/{attachmentId}")
    public ResponseEntity<Resource> viewFile(@PathVariable Long attachmentId) {
        try {
            com.printflow.dto.AttachmentDTO attachment = fileStorageService.getAttachmentById(attachmentId);
            byte[] fileContent = fileStorageService.getAttachmentFile(attachmentId);

            ByteArrayResource resource = new ByteArrayResource(fileContent);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .contentLength(attachment.getFileSize())
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/print/{attachmentId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> printFile(@PathVariable Long attachmentId) {
        try {
            com.printflow.dto.AttachmentDTO attachment = fileStorageService.getAttachmentById(attachmentId);
            String fileUrl = "/api/files/view/" + attachmentId;
            String mime = attachment.getMimeType() != null ? attachment.getMimeType() : "";
            String content;
            if (mime.startsWith("image/")) {
                content = "<img src=\"" + fileUrl + "\" style=\"max-width:100%;height:auto;\" />";
            } else {
                content = "<iframe src=\"" + fileUrl + "\" style=\"width:100%;height:100vh;border:0;\"></iframe>";
            }
            String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>Print</title>"
                + "<style>body{margin:0;padding:0;}</style>"
                + "</head><body>"
                + content
                + "<script>window.onload=function(){setTimeout(function(){window.print();},300);};</script>"
                + "</body></html>";
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/thumbnail/{attachmentId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long attachmentId,
                                                 @RequestParam(required = false) String token) {
        try {
            byte[] thumbnailContent = token != null && !token.isBlank()
                ? fileStorageService.getThumbnailPublic(attachmentId, token)
                : fileStorageService.getThumbnail(attachmentId);
            
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
