-- Drop any existing UNIQUE index on clients.email (global uniqueness)
SET @idx := (
    SELECT INDEX_NAME
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'clients'
      AND COLUMN_NAME = 'email'
      AND NON_UNIQUE = 0
    LIMIT 1
);
SET @sql := IF(@idx IS NOT NULL, CONCAT('ALTER TABLE clients DROP INDEX `', @idx, '`'), 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Ensure uniqueness per tenant
CREATE UNIQUE INDEX ux_clients_tenant_email ON clients (tenant_id, email);
