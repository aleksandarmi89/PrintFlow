CREATE TABLE IF NOT EXISTS client_portal_access (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    access_token VARCHAR(64) NOT NULL,
    expires_at DATETIME NULL,
    last_accessed_at DATETIME NULL,
    UNIQUE INDEX idx_client_portal_token (access_token),
    INDEX idx_client_portal_client (client_id),
    INDEX idx_client_portal_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS client_pricing_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    product_variant_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    last_price DECIMAL(12,2) NULL,
    average_price DECIMAL(12,2) NULL,
    discount_percent DECIMAL(6,2) NULL,
    last_ordered_at DATETIME NULL,
    INDEX idx_client_pricing_client (client_id),
    INDEX idx_client_pricing_variant (product_variant_id),
    INDEX idx_client_pricing_tenant (tenant_id)
);

ALTER TABLE attachments ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE attachments ADD COLUMN approved_at DATETIME NULL;
ALTER TABLE attachments ADD COLUMN approved_by VARCHAR(255) NULL;
ALTER TABLE attachments ADD COLUMN approval_ip VARCHAR(64) NULL;
ALTER TABLE attachments ADD COLUMN approval_token VARCHAR(64) NULL;
CREATE UNIQUE INDEX idx_attachment_approval_token ON attachments (approval_token);
