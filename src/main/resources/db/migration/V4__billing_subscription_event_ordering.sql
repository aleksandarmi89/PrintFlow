ALTER TABLE billing_subscriptions
    ADD COLUMN last_stripe_event_created BIGINT NULL;
