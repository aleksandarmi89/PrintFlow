package com.printflow.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class FileUtils {
    
    public String generateUniqueFileName(String originalFileName) {
        String extension = getFileExtension(originalFileName);
        String uuid = UUID.randomUUID().toString();
        return uuid + extension;
    }
    
    public String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }
    
    public String getMimeType(String fileName) {
        try {
            Path path = Paths.get(fileName);
            return Files.probeContentType(path);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
    
    public boolean isImageFile(String fileName) {
        String mimeType = getMimeType(fileName);
        return mimeType != null && mimeType.startsWith("image/");
    }
    
    public boolean isVectorFile(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return extension.equals(".ai") || extension.equals(".eps") || 
               extension.equals(".svg") || extension.equals(".cdr");
    }
    
    public boolean isDesignFile(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return extension.equals(".psd") || extension.equals(".ai") || 
               extension.equals(".indd") || extension.equals(".cdr");
    }
    
    public long getFileSizeInMB(MultipartFile file) {
        return file.getSize() / (1024 * 1024);
    }
    
    public void createDirectoryIfNotExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
    
    public boolean isSafeFileName(String fileName) {
        // Provera za maliciozne imena fajlova
        return fileName != null && 
               !fileName.contains("..") && 
               !fileName.contains("/") && 
               !fileName.contains("\\") &&
               fileName.matches("[a-zA-Z0-9._\\-\\[\\]() ]+");
    }
}
