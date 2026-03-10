ALTER TABLE work_orders
    ADD COLUMN public_token_created_at DATETIME NULL,
    ADD COLUMN public_token_expires_at DATETIME NULL;

UPDATE work_orders
SET public_token_created_at = COALESCE(public_token_created_at, created_at, NOW()),
    public_token_expires_at = COALESCE(public_token_expires_at, DATE_ADD(COALESCE(public_token_created_at, NOW()), INTERVAL 30 DAY));
