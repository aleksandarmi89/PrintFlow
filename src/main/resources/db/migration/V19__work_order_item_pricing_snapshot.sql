ALTER TABLE work_order_items
    ADD COLUMN profit_amount DECIMAL(12,2) NULL,
    ADD COLUMN currency VARCHAR(10) NULL,
    ADD COLUMN pricing_snapshot_json LONGTEXT NULL;

ALTER TABLE work_order_items
    MODIFY breakdown_json LONGTEXT NOT NULL,
    MODIFY attributes_json LONGTEXT NULL;
