package com.printflow.pricing.dto;

import com.printflow.entity.enums.ProductSyncAuthType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProductSyncSettingsForm {

    private boolean enabled;

    @Size(max = 500)
    @Pattern(regexp = "^$|https?://.+|demo://.+", message = "Endpoint URL must start with http://, https://, or demo://")
    private String endpointUrl;

    private ProductSyncAuthType authType = ProductSyncAuthType.NONE;

    @Size(max = 120)
    private String authHeaderName;

    @Size(max = 512)
    private String authToken;

    @Size(max = 120)
    private String payloadRoot;

    @Min(1000)
    @Max(120000)
    private Integer connectTimeoutMs = 8000;

    @Min(1000)
    @Max(180000)
    private Integer readTimeoutMs = 15000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public ProductSyncAuthType getAuthType() {
        return authType;
    }

    public void setAuthType(ProductSyncAuthType authType) {
        this.authType = authType;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getPayloadRoot() {
        return payloadRoot;
    }

    public void setPayloadRoot(String payloadRoot) {
        this.payloadRoot = payloadRoot;
    }

    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(Integer connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
