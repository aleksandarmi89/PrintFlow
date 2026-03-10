ALTER TABLE products
    ADD COLUMN IF NOT EXISTS sku VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS description TEXT NULL,
    ADD COLUMN IF NOT EXISTS category_label VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS unit_label VARCHAR(80) NULL,
    ADD COLUMN IF NOT EXISTS base_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'RSD',
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(191) NULL,
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS updated_by_user_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_products_tenant_name ON products(tenant_id, name);
CREATE INDEX IF NOT EXISTS idx_products_tenant_external_id ON products(tenant_id, external_id);

-- MySQL unique index allows multiple NULL SKUs while enforcing uniqueness for non-null values per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_tenant_sku ON products(tenant_id, sku);
