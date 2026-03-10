ALTER TABLE billing_plan_configs
    ADD COLUMN billing_interval VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

UPDATE billing_plan_configs
SET billing_interval = 'MONTHLY'
WHERE billing_interval IS NULL OR billing_interval = '';

ALTER TABLE billing_plan_configs
    DROP INDEX plan;

CREATE UNIQUE INDEX uk_billing_plan_interval
    ON billing_plan_configs(plan, billing_interval);
