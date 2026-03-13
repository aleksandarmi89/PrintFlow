package com.printflow.service;

import com.printflow.config.StripeProperties;
import com.printflow.entity.BillingCustomer;
import com.printflow.entity.BillingSubscription;
import com.printflow.entity.Company;
import com.printflow.repository.BillingCustomerRepository;
import com.printflow.repository.BillingSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeBillingServiceTest {

    @Test
    void createSubscriptionCheckoutRejectsBlankPriceId() {
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingCustomerRepository customerRepository = mock(BillingCustomerRepository.class);
        BillingSubscriptionRepository subscriptionRepository = mock(BillingSubscriptionRepository.class);
        StripeBillingService service = new StripeBillingService(
            stripeProperties, customerRepository, subscriptionRepository
        );

        Company company = new Company();
        company.setId(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.createSubscriptionCheckout(company, "  "));
        assertEquals("priceId is required", ex.getMessage());
    }

    @Test
    void handleWebhookFailsWhenWebhookSecretMissing() {
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingCustomerRepository customerRepository = mock(BillingCustomerRepository.class);
        BillingSubscriptionRepository subscriptionRepository = mock(BillingSubscriptionRepository.class);
        StripeBillingService service = new StripeBillingService(
            stripeProperties, customerRepository, subscriptionRepository
        );

        when(stripeProperties.getApiKey()).thenReturn("sk_test_123");
        when(stripeProperties.getWebhookSecret()).thenReturn(" ");

        assertThrows(com.stripe.exception.SignatureVerificationException.class,
            () -> service.handleWebhook("{}", "sig"));
    }

    @Test
    void upsertSubscriptionInvalidatesBillingCache() throws Exception {
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingCustomerRepository customerRepository = mock(BillingCustomerRepository.class);
        BillingSubscriptionRepository subscriptionRepository = mock(BillingSubscriptionRepository.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);

        StripeBillingService service = new StripeBillingService(
            stripeProperties, customerRepository, subscriptionRepository
        );
        var billingField = StripeBillingService.class.getDeclaredField("billingAccessService");
        billingField.setAccessible(true);
        billingField.set(service, billingAccessService);

        Company company = new Company();
        company.setId(44L);
        BillingCustomer billingCustomer = new BillingCustomer();
        billingCustomer.setCompany(company);
        billingCustomer.setStripeCustomerId("cus_123");
        when(customerRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(billingCustomer));
        when(subscriptionRepository.findByCompany_Id(44L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(BillingSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        com.stripe.model.Subscription subscription = mock(com.stripe.model.Subscription.class);
        when(subscription.getCustomer()).thenReturn("cus_123");
        when(subscription.getId()).thenReturn("sub_123");
        when(subscription.getStatus()).thenReturn("active");
        when(subscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(subscription.getCurrentPeriodEnd()).thenReturn(1_700_000_000L);
        when(subscription.getItems()).thenReturn(null);

        Method method = StripeBillingService.class
            .getDeclaredMethod("upsertSubscription", com.stripe.model.Subscription.class, Long.class);
        method.setAccessible(true);
        method.invoke(service, subscription, 1_700_000_000L);

        verify(billingAccessService).invalidateCompanyCache(44L);
    }

    @Test
    void outOfOrderStripeEventDoesNotInvalidateCache() throws Exception {
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingCustomerRepository customerRepository = mock(BillingCustomerRepository.class);
        BillingSubscriptionRepository subscriptionRepository = mock(BillingSubscriptionRepository.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);

        StripeBillingService service = new StripeBillingService(
            stripeProperties, customerRepository, subscriptionRepository
        );
        var billingField = StripeBillingService.class.getDeclaredField("billingAccessService");
        billingField.setAccessible(true);
        billingField.set(service, billingAccessService);

        Company company = new Company();
        company.setId(45L);
        BillingCustomer billingCustomer = new BillingCustomer();
        billingCustomer.setCompany(company);
        billingCustomer.setStripeCustomerId("cus_old");
        when(customerRepository.findByStripeCustomerId("cus_old")).thenReturn(Optional.of(billingCustomer));

        BillingSubscription existing = new BillingSubscription();
        existing.setLastStripeEventCreated(2_000_000_000L);
        existing.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));
        when(subscriptionRepository.findByCompany_Id(45L)).thenReturn(Optional.of(existing));

        com.stripe.model.Subscription subscription = mock(com.stripe.model.Subscription.class);
        when(subscription.getCustomer()).thenReturn("cus_old");
        when(subscription.getId()).thenReturn("sub_old");
        when(subscription.getStatus()).thenReturn("active");
        when(subscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(subscription.getCurrentPeriodEnd()).thenReturn(1_700_000_000L);
        when(subscription.getItems()).thenReturn(null);

        Method method = StripeBillingService.class
            .getDeclaredMethod("upsertSubscription", com.stripe.model.Subscription.class, Long.class);
        method.setAccessible(true);
        method.invoke(service, subscription, 1_700_000_000L);

        verify(subscriptionRepository, never()).save(any(BillingSubscription.class));
        verify(billingAccessService, never()).invalidateCompanyCache(eq(45L));
    }
}

