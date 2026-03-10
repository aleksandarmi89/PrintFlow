ALTER TABLE companies
    ADD COLUMN slug VARCHAR(120) NULL;

UPDATE companies
SET slug = LOWER(TRIM(BOTH '-' FROM REPLACE(REPLACE(REPLACE(REPLACE(name, ' ', '-'), '_', '-'), '.', '-'), '/', '-')))
WHERE slug IS NULL OR slug = '';

ALTER TABLE companies
    MODIFY COLUMN slug VARCHAR(120) NOT NULL;

CREATE UNIQUE INDEX uk_companies_slug ON companies(slug);

CREATE TABLE public_order_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    customer_email VARCHAR(190) NOT NULL,
    customer_phone VARCHAR(50) NULL,
    customer_company_name VARCHAR(190) NULL,
    product_type VARCHAR(150) NOT NULL,
    quantity INT NOT NULL,
    dimensions VARCHAR(190) NULL,
    material VARCHAR(150) NULL,
    finishing VARCHAR(150) NULL,
    deadline DATETIME NULL,
    notes TEXT NULL,
    status VARCHAR(40) NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    converted_at DATETIME NULL,
    converted_order_id BIGINT NULL,
    CONSTRAINT fk_public_order_requests_company FOREIGN KEY (tenant_id) REFERENCES companies(id),
    CONSTRAINT fk_public_order_requests_order FOREIGN KEY (converted_order_id) REFERENCES work_orders(id)
);

CREATE TABLE public_order_request_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NULL,
    mime_type VARCHAR(120) NULL,
    file_size BIGINT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_public_order_request_attach_req FOREIGN KEY (request_id) REFERENCES public_order_requests(id),
    CONSTRAINT fk_public_order_request_attach_company FOREIGN KEY (tenant_id) REFERENCES companies(id)
);

CREATE INDEX idx_public_order_requests_company ON public_order_requests(tenant_id);
CREATE INDEX idx_public_order_requests_status ON public_order_requests(status);
CREATE INDEX idx_public_order_requests_created_at ON public_order_requests(created_at);
CREATE INDEX idx_public_order_requests_customer_email ON public_order_requests(customer_email);
CREATE INDEX idx_public_order_req_attach_request ON public_order_request_attachments(request_id);
