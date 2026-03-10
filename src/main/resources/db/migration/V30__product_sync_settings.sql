CREATE TABLE IF NOT EXISTS product_sync_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'0',
    endpoint_url VARCHAR(500) NULL,
    auth_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    auth_header_name VARCHAR(120) NULL,
    auth_token_enc VARCHAR(2048) NULL,
    payload_root VARCHAR(120) NULL,
    connect_timeout_ms INT NOT NULL DEFAULT 8000,
    read_timeout_ms INT NOT NULL DEFAULT 15000,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_sync_settings_company UNIQUE (company_id),
    CONSTRAINT fk_product_sync_settings_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_sync_settings_company ON product_sync_settings(company_id);
