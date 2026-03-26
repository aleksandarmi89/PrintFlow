ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS delivery_recipient_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delivery_recipient_phone VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delivery_city VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delivery_postal_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS shipment_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS shipment_price DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS shipping_note TEXT;

UPDATE work_orders
SET shipment_status = CASE
    WHEN status = 'SENT' THEN 'SHIPPED'
    WHEN status = 'COMPLETED' THEN 'DELIVERED'
    ELSE 'PREPARING'
END
WHERE shipment_status IS NULL;
