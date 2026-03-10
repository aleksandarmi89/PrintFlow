CREATE TABLE IF NOT EXISTS billing_customers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    stripe_customer_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT uq_billing_customers_company UNIQUE (company_id),
    CONSTRAINT uq_billing_customers_stripe UNIQUE (stripe_customer_id),
    CONSTRAINT fk_billing_customers_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE IF NOT EXISTS billing_subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    stripe_subscription_id VARCHAR(255) NULL,
    stripe_price_id VARCHAR(255) NULL,
    status VARCHAR(50) NULL,
    current_period_end TIMESTAMP NULL,
    cancel_at_period_end BOOLEAN NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT uq_billing_subscriptions_company UNIQUE (company_id),
    CONSTRAINT uq_billing_subscriptions_stripe UNIQUE (stripe_subscription_id),
    CONSTRAINT fk_billing_subscriptions_company FOREIGN KEY (company_id) REFERENCES companies(id)
);
