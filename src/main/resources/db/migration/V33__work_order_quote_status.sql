ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS quote_status VARCHAR(30) DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS quote_sent_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS quote_valid_until TIMESTAMP NULL;

UPDATE work_orders
SET quote_status = 'NONE'
WHERE quote_status IS NULL;
