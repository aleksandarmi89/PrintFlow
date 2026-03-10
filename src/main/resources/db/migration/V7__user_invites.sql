CREATE TABLE IF NOT EXISTS user_invites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(128) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    company_id BIGINT NOT NULL,
    invited_by_user_id BIGINT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_invites_token UNIQUE (token),
    CONSTRAINT fk_user_invites_company FOREIGN KEY (company_id) REFERENCES companies(id)
);
