CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    unit_type VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    INDEX idx_products_tenant_id (tenant_id)
);

CREATE TABLE IF NOT EXISTS product_variants (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    default_markup_percent DECIMAL(10,2) NOT NULL DEFAULT 20.00,
    min_price DECIMAL(12,2) NULL,
    waste_percent DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_product_variants_tenant_id (tenant_id),
    INDEX idx_product_variants_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS pricing_components (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    amount DECIMAL(12,4) NOT NULL,
    notes TEXT NULL,
    INDEX idx_pricing_components_tenant_id (tenant_id),
    INDEX idx_pricing_components_variant_id (variant_id)
);

CREATE TABLE IF NOT EXISTS work_order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    product_variant_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    width_mm INT NULL,
    height_mm INT NULL,
    attributes_json LONGTEXT NULL,
    calculated_cost DECIMAL(12,2) NOT NULL,
    calculated_price DECIMAL(12,2) NOT NULL,
    margin_percent DECIMAL(6,2) NOT NULL,
    breakdown_json LONGTEXT NOT NULL,
    price_locked BOOLEAN NOT NULL DEFAULT TRUE,
    price_calculated_at DATETIME NOT NULL,
    status VARCHAR(40) NULL,
    notes VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_work_order_items_tenant_id (tenant_id),
    INDEX idx_work_order_items_work_order_id (work_order_id)
);

ALTER TABLE users ADD COLUMN hourly_rate DECIMAL(10,2) NULL;
