package com.printflow.pricing.dto;

public class ProductSyncProbeResult {
    private final boolean ok;
    private final int discoveredRows;
    private final String message;

    public ProductSyncProbeResult(boolean ok, int discoveredRows, String message) {
        this.ok = ok;
        this.discoveredRows = discoveredRows;
        this.message = message;
    }

    public boolean isOk() {
        return ok;
    }

    public int getDiscoveredRows() {
        return discoveredRows;
    }

    public String getMessage() {
        return message;
    }
}
