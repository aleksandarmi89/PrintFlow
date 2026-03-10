ALTER TABLE tasks MODIFY status VARCHAR(50) NOT NULL;

-- Backfill any legacy/invalid values
UPDATE tasks SET status='PENDING' WHERE status='TODO';
UPDATE tasks SET status='PENDING' WHERE status='' OR status IS NULL;
