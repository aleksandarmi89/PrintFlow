-- Ensure role column accepts new enum names (avoid MySQL ENUM truncation)
SET @users_exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'users'
);
SET @stmt := IF(@users_exists > 0, 'ALTER TABLE users MODIFY role VARCHAR(50)', 'SELECT 1');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

-- Normalize legacy role names if they exist
SET @stmt := IF(@users_exists > 0, "UPDATE users SET role = 'WORKER_DESIGN' WHERE role = 'WORKER_DESIGNER'", 'SELECT 1');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @stmt := IF(@users_exists > 0, "UPDATE users SET role = 'WORKER_PRINT' WHERE role = 'WORKER_PRINTER'", 'SELECT 1');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;
