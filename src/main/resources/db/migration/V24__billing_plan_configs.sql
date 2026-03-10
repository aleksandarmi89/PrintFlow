CREATE TABLE IF NOT EXISTS billing_plan_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan VARCHAR(30) NOT NULL UNIQUE,
    stripe_price_id VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO billing_plan_configs (plan, stripe_price_id, active)
SELECT 'FREE', NULL, TRUE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM billing_plan_configs WHERE plan = 'FREE');

INSERT INTO billing_plan_configs (plan, stripe_price_id, active)
SELECT 'PRO', NULL, TRUE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM billing_plan_configs WHERE plan = 'PRO');

INSERT INTO billing_plan_configs (plan, stripe_price_id, active)
SELECT 'TEAM', NULL, TRUE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM billing_plan_configs WHERE plan = 'TEAM');
