package com.printflow.pricing.dto;

import java.util.ArrayList;
import java.util.List;

public class ProductImportResult {

    private int totalRows;
    private int importedCount;
    private int updatedCount;
    private int skippedCount;
    private int failedCount;
    private final List<RowError> errors = new ArrayList<>();

    public void addError(int row, String message) {
        this.errors.add(new RowError(row, message));
        this.failedCount++;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<RowError> getErrors() {
        return errors;
    }

    public record RowError(int row, String message) {
    }
}
