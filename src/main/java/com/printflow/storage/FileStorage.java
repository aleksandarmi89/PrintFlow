package com.printflow.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorage {

    StoredFile store(MultipartFile file, String subDir, boolean generateThumbnail) throws IOException;

    byte[] load(String absolutePath) throws IOException;

    byte[] loadThumbnail(String thumbnailPath, String fallbackPath) throws IOException;

    void deleteIfExists(String absolutePath);
}
