package com.printflow.storage;

public class StoredFile {
    private final String filePath;
    private final String fileName;
    private final String thumbnailPath;

    public StoredFile(String filePath, String fileName, String thumbnailPath) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.thumbnailPath = thumbnailPath;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }
}
