package com.printflow.service;

import com.printflow.config.StripeProperties;
import com.printflow.entity.BillingCustomer;
import com.printflow.entity.BillingSubscription;
import com.printflow.entity.Company;
import com.printflow.repository.BillingCustomerRepository;
import com.printflow.repository.BillingSubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@Transactional
public class StripeBillingService {

    private static final Logger log = LoggerFactory.getLogger(StripeBillingService.class);

    private final StripeProperties stripeProperties;
    private final BillingCustomerRepository billingCustomerRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;

    public StripeBillingService(StripeProperties stripeProperties,
                                BillingCustomerRepository billingCustomerRepository,
                                BillingSubscriptionRepository billingSubscriptionRepository) {
        this.stripeProperties = stripeProperties;
        this.billingCustomerRepository = billingCustomerRepository;
        this.billingSubscriptionRepository = billingSubscriptionRepository;
    }

    public BillingCustomer ensureCustomer(Company company) throws StripeException {
        BillingCustomer existing = billingCustomerRepository.findByCompany_Id(company.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        ensureApiKey();
        CustomerCreateParams params = CustomerCreateParams.builder()
            .setName(company.getName())
            .build();
        Customer customer = Customer.create(params);

        BillingCustomer billingCustomer = new BillingCustomer();
        billingCustomer.setCompany(company);
        billingCustomer.setStripeCustomerId(customer.getId());
        return billingCustomerRepository.save(billingCustomer);
    }

    public String createSubscriptionCheckout(Company company, String priceId) throws StripeException {
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalArgumentException("priceId is required");
        }
        ensureApiKey();
        BillingCustomer billingCustomer = ensureCustomer(company);

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(billingCustomer.getStripeCustomerId())
            .setSuccessUrl(stripeProperties.getSuccessUrl())
            .setCancelUrl(stripeProperties.getCancelUrl())
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1L)
                .build())
            .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    public void handleWebhook(String payload, String signatureHeader) throws SignatureVerificationException {
        ensureApiKey();
        String secret = stripeProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new SignatureVerificationException("Missing webhook secret", signatureHeader);
        }
        Event event = Webhook.constructEvent(payload, signatureHeader, secret);
        handleEvent(event);
    }

    private void handleEvent(Event event) {
        if (event == null || event.getType() == null) {
            return;
        }
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> handleSubscriptionEvent(event);
            default -> {
                // ignore other events for now
            }
        }
    }

    private void handleCheckoutCompleted(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> obj = dataObjectDeserializer.getObject();
        if (obj.isEmpty() || !(obj.get() instanceof Session session)) {
            log.warn("Unable to deserialize checkout.session.completed event");
            return;
        }
        if (session.getSubscription() == null || session.getCustomer() == null) {
            return;
        }
        try {
            Subscription subscription = Subscription.retrieve(session.getSubscription());
            upsertSubscription(subscription, event.getCreated());
        } catch (StripeException e) {
            log.warn("Failed to retrieve subscription from checkout session {}", session.getId(), e);
        }
    }

    private void handleSubscriptionEvent(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> obj = dataObjectDeserializer.getObject();
        if (obj.isEmpty() || !(obj.get() instanceof Subscription subscription)) {
            log.warn("Unable to deserialize subscription event {}", event.getType());
            return;
        }
        upsertSubscription(subscription, event.getCreated());
    }

    private void upsertSubscription(Subscription subscription, Long eventCreated) {
        if (subscription == null || subscription.getCustomer() == null) {
            return;
        }
        BillingCustomer billingCustomer = billingCustomerRepository
            .findByStripeCustomerId(subscription.getCustomer())
            .orElse(null);
        if (billingCustomer == null) {
            log.warn("No billing customer found for stripe customer {}", subscription.getCustomer());
            return;
        }
        BillingSubscription billingSubscription = billingSubscriptionRepository
            .findByCompany_Id(billingCustomer.getCompany().getId())
            .orElse(new BillingSubscription());

        if (eventCreated != null && billingSubscription.getLastStripeEventCreated() != null
            && eventCreated < billingSubscription.getLastStripeEventCreated()) {
            log.info("Ignoring out-of-order Stripe event for customer {} (eventCreated={}, last={})",
                subscription.getCustomer(), eventCreated, billingSubscription.getLastStripeEventCreated());
            return;
        }

        billingSubscription.setCompany(billingCustomer.getCompany());
        billingSubscription.setStripeSubscriptionId(subscription.getId());
        billingSubscription.setStatus(subscription.getStatus());
        billingSubscription.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
        billingSubscription.setCurrentPeriodEnd(toLocalDateTime(subscription.getCurrentPeriodEnd()));
        billingSubscription.setLastStripeEventCreated(eventCreated);

        String priceId = null;
        if (subscription.getItems() != null
            && subscription.getItems().getData() != null
            && !subscription.getItems().getData().isEmpty()
            && subscription.getItems().getData().get(0).getPrice() != null) {
            priceId = subscription.getItems().getData().get(0).getPrice().getId();
        }
        billingSubscription.setStripePriceId(priceId);

        billingSubscriptionRepository.save(billingSubscription);
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    private void ensureApiKey() {
        String apiKey = stripeProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Stripe API key is not configured");
        }
        Stripe.apiKey = apiKey;
    }
}
