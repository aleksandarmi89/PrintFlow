package com.printflow.service;

public interface StripeSettings {
    String getApiKey();
    String getWebhookSecret();
    String getSuccessUrl();
    String getCancelUrl();
}
