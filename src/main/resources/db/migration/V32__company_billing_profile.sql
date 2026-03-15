ALTER TABLE companies
    ADD COLUMN legal_name VARCHAR(255) NULL,
    ADD COLUMN tax_id VARCHAR(50) NULL,
    ADD COLUMN registration_number VARCHAR(50) NULL,
    ADD COLUMN bank_account VARCHAR(100) NULL,
    ADD COLUMN bank_name VARCHAR(120) NULL,
    ADD COLUMN billing_email VARCHAR(255) NULL;
