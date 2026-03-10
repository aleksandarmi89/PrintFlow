CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_password_reset_token_company FOREIGN KEY (tenant_id) REFERENCES companies(id)
);

CREATE INDEX idx_password_reset_token_tenant ON password_reset_tokens(tenant_id);
