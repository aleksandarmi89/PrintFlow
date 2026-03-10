package com.printflow.pricing.entity;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSyncAuthType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_sync_settings",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_sync_settings_company", columnNames = "company_id"))
public class ProductSyncSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 30)
    private ProductSyncAuthType authType = ProductSyncAuthType.NONE;

    @Column(name = "auth_header_name", length = 120)
    private String authHeaderName;

    @Column(name = "auth_token_enc", length = 2048)
    private String authTokenEnc;

    @Column(name = "payload_root", length = 120)
    private String payloadRoot;

    @Column(name = "connect_timeout_ms", nullable = false)
    private Integer connectTimeoutMs = 8000;

    @Column(name = "read_timeout_ms", nullable = false)
    private Integer readTimeoutMs = 15000;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;

    @Column(name = "last_sync_message", length = 1000)
    private String lastSyncMessage;

    @Column(name = "last_sync_imported", nullable = false)
    private int lastSyncImported = 0;

    @Column(name = "last_sync_updated", nullable = false)
    private int lastSyncUpdated = 0;

    @Column(name = "last_sync_failed", nullable = false)
    private int lastSyncFailed = 0;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        if (authType == null) {
            authType = ProductSyncAuthType.NONE;
        }
        if (connectTimeoutMs == null || connectTimeoutMs <= 0) {
            connectTimeoutMs = 8000;
        }
        if (readTimeoutMs == null || readTimeoutMs <= 0) {
            readTimeoutMs = 15000;
        }
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

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

    public String getAuthTokenEnc() {
        return authTokenEnc;
    }

    public void setAuthTokenEnc(String authTokenEnc) {
        this.authTokenEnc = authTokenEnc;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
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
