package com.printflow.pricing.dto;

import java.time.LocalDateTime;

public class ProductSyncStatusView {
    private boolean configured;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String lastSyncMessage;
    private int lastSyncImported;
    private int lastSyncUpdated;
    private int lastSyncFailed;

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public void setLastSyncStatus(String lastSyncStatus) {
        this.lastSyncStatus = lastSyncStatus;
    }

    public String getLastSyncMessage() {
        return lastSyncMessage;
    }

    public void setLastSyncMessage(String lastSyncMessage) {
        this.lastSyncMessage = lastSyncMessage;
    }

    public int getLastSyncImported() {
        return lastSyncImported;
    }

    public void setLastSyncImported(int lastSyncImported) {
        this.lastSyncImported = lastSyncImported;
    }

    public int getLastSyncUpdated() {
        return lastSyncUpdated;
    }

    public void setLastSyncUpdated(int lastSyncUpdated) {
        this.lastSyncUpdated = lastSyncUpdated;
    }

    public int getLastSyncFailed() {
        return lastSyncFailed;
    }

    public void setLastSyncFailed(int lastSyncFailed) {
        this.lastSyncFailed = lastSyncFailed;
    }
}
