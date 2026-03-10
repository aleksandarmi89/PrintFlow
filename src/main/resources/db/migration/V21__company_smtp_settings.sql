ALTER TABLE companies
    ADD COLUMN smtp_host VARCHAR(255) NULL,
    ADD COLUMN smtp_port INT NULL,
    ADD COLUMN smtp_user VARCHAR(255) NULL,
    ADD COLUMN smtp_password VARCHAR(255) NULL,
    ADD COLUMN smtp_tls TINYINT(1) NULL;
