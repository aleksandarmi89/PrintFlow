package com.printflow.controller;

import com.printflow.service.StripeBillingService;
import com.stripe.exception.SignatureVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import com.printflow.config.StripeProperties;

@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeBillingService stripeBillingService;
    private final StripeProperties stripeProperties;

    public StripeWebhookController(StripeBillingService stripeBillingService,
                                   StripeProperties stripeProperties) {
        this.stripeBillingService = stripeBillingService;
        this.stripeProperties = stripeProperties;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
        }
        if (stripeProperties == null || !stripeProperties.isConfigured()) {
            return ResponseEntity.status(503).body("stripe not configured");
        }
        try {
            stripeBillingService.handleWebhook(payload, signature);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe signature verification failed", ex);
            return ResponseEntity.status(400).body("invalid signature");
        } catch (Exception ex) {
            log.error("Stripe webhook processing error", ex);
            return ResponseEntity.status(500).body("error");
        }
    }
}
