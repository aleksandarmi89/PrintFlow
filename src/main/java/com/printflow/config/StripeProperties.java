package com.printflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {
    private String mode = "test";
    private String apiKey;
    private String liveApiKey;
    private String testApiKey;
    private String webhookSecret;
    private String liveWebhookSecret;
    private String testWebhookSecret;
    private String successUrl;
    private String cancelUrl;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        return isLiveMode() ? liveApiKey : testApiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getLiveApiKey() {
        return liveApiKey;
    }

    public void setLiveApiKey(String liveApiKey) {
        this.liveApiKey = liveApiKey;
    }

    public String getTestApiKey() {
        return testApiKey;
    }

    public void setTestApiKey(String testApiKey) {
        this.testApiKey = testApiKey;
    }

    public String getWebhookSecret() {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            return webhookSecret;
        }
        return isLiveMode() ? liveWebhookSecret : testWebhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getLiveWebhookSecret() {
        return liveWebhookSecret;
    }

    public void setLiveWebhookSecret(String liveWebhookSecret) {
        this.liveWebhookSecret = liveWebhookSecret;
    }

    public String getTestWebhookSecret() {
        return testWebhookSecret;
    }

    public void setTestWebhookSecret(String testWebhookSecret) {
        this.testWebhookSecret = testWebhookSecret;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public boolean isLiveMode() {
        return mode != null && mode.equalsIgnoreCase("live");
    }

    public boolean isConfigured() {
        String key = getApiKey();
        String secret = getWebhookSecret();
        return key != null && !key.isBlank() && secret != null && !secret.isBlank();
    }
}
