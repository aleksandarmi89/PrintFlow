-- Extend trial to 30 days for free-plan companies without active subscription
UPDATE companies
SET trial_start = COALESCE(trial_start, NOW()),
    trial_end = NOW() + INTERVAL 30 DAY
WHERE plan = 'FREE'
  AND (trial_end IS NULL OR trial_end < NOW());
