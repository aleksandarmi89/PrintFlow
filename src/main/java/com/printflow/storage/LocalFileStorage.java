package com.printflow.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final String uploadDir;
    private final boolean virusScanEnabled;
    private final String virusScanCommand;

    public LocalFileStorage(
            @Value("${app.upload.dir:./uploads}") String uploadDir,
            @Value("${app.upload.virus-scan.enabled:false}") boolean virusScanEnabled,
            @Value("${app.upload.virus-scan.command:}") String virusScanCommand) {
        this.uploadDir = uploadDir;
        this.virusScanEnabled = virusScanEnabled;
        this.virusScanCommand = virusScanCommand;
        createUploadDirectory();
    }

    @Override
    public StoredFile store(MultipartFile file, String subDir, boolean generateThumbnail) throws IOException {
        String fullPathDir = uploadDir + subDir;
        createDirectoryIfNotExists(fullPathDir);

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = generateUniqueFileName(originalFileName);
        String filePath = fullPathDir + "/" + uniqueFileName;

        Path targetLocation = Paths.get(filePath);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        runVirusScanIfEnabled(targetLocation);

        String thumbnailPath = null;
        if (generateThumbnail && isImageFile(originalFileName)) {
            thumbnailPath = generateThumbnail(file, fullPathDir, uniqueFileName);
        }

        return new StoredFile(filePath, uniqueFileName, thumbnailPath);
    }

    @Override
    public byte[] load(String absolutePath) throws IOException {
        if (absolutePath == null || absolutePath.isBlank()) {
            throw new IOException("File path is empty.");
        }
        Path path = Paths.get(absolutePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found on disk.");
        }
        return Files.readAllBytes(path);
    }

    @Override
    public byte[] loadThumbnail(String thumbnailPath, String fallbackPath) throws IOException {
        String pathStr = (thumbnailPath != null && !thumbnailPath.isBlank()) ? thumbnailPath : fallbackPath;
        return load(pathStr);
    }

    @Override
    public void deleteIfExists(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(absolutePath));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", absolutePath, e);
        }
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
        String ext = getFileExtension(fileName).toLowerCase(Locale.ROOT);
        return List.of(".jpg", ".jpeg", ".png", ".gif", ".svg").contains(ext);
    }

    private String getFileExtension(String fileName) {
        return (fileName == null || !fileName.contains(".")) ? "" : fileName.substring(fileName.lastIndexOf("."));
    }

    private String generateThumbnail(MultipartFile file, String orderDir, String uniqueFileName) {
        try {
            String thumbnailName = "thumb_" + uniqueFileName;
            String thumbnailPath = orderDir + "/" + thumbnailName;

            net.coobird.thumbnailator.Thumbnails.of(file.getInputStream())
                .size(200, 200)
                .outputFormat("jpg")
                .toFile(new java.io.File(thumbnailPath));

            log.info("Thumbnail generated: {}", thumbnailPath);
            return thumbnailPath;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not generate thumbnail for {}, using original instead. Error: {}",
                uniqueFileName, e.getMessage());
            return null;
        }
    }

    private void runVirusScanIfEnabled(Path filePath) {
        if (!virusScanEnabled) {
            return;
        }
        if (virusScanCommand == null || virusScanCommand.trim().isEmpty()) {
            log.warn("Virus scan enabled but no command configured.");
            return;
        }
        try {
            String command = virusScanCommand.replace("{file}", filePath.toString());
            String[] parts = command.split(" ");
            ProcessBuilder builder = new ProcessBuilder(parts);
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Files.deleteIfExists(filePath);
                throw new RuntimeException("Virus scan failed for file: " + filePath.getFileName());
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignore) {
                // no-op
            }
            throw new RuntimeException("Virus scan error: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignore) {
                // no-op
            }
            throw new RuntimeException("Virus scan interrupted for file: " + filePath.getFileName(), ex);
        }
    }
}
