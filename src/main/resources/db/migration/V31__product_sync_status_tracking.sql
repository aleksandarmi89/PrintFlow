ALTER TABLE product_sync_settings
    ADD COLUMN last_sync_at TIMESTAMP NULL,
    ADD COLUMN last_sync_status VARCHAR(20) NULL,
    ADD COLUMN last_sync_message VARCHAR(1000) NULL,
    ADD COLUMN last_sync_imported INT NOT NULL DEFAULT 0,
    ADD COLUMN last_sync_updated INT NOT NULL DEFAULT 0,
    ADD COLUMN last_sync_failed INT NOT NULL DEFAULT 0;
