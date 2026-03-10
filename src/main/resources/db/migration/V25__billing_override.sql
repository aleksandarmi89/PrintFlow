ALTER TABLE companies
    ADD COLUMN billing_override_active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN billing_override_until DATETIME NULL;
