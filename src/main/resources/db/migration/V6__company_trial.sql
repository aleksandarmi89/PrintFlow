ALTER TABLE companies
    ADD COLUMN trial_start TIMESTAMP NULL,
    ADD COLUMN trial_end TIMESTAMP NULL;

UPDATE companies
SET trial_start = COALESCE(trial_start, NOW()),
    trial_end = COALESCE(trial_end, DATE_ADD(NOW(), INTERVAL 14 DAY))
WHERE trial_start IS NULL OR trial_end IS NULL;
