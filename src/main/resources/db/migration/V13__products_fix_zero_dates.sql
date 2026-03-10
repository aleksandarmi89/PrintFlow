UPDATE products
SET created_at = NOW()
WHERE created_at IS NULL OR created_at = '0000-00-00 00:00:00';

UPDATE products
SET updated_at = NULL
WHERE updated_at = '0000-00-00 00:00:00';

ALTER TABLE products
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6);

UPDATE work_order_items
SET created_at = NOW()
WHERE created_at IS NULL OR created_at = '0000-00-00 00:00:00';

UPDATE work_order_items
SET price_calculated_at = created_at
WHERE price_calculated_at IS NULL OR price_calculated_at = '0000-00-00 00:00:00';

ALTER TABLE work_order_items
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY price_calculated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
