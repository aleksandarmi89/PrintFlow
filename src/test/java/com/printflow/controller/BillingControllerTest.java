package com.printflow.controller;

import com.printflow.config.StripeProperties;
import com.printflow.entity.Company;
import com.printflow.entity.enums.BillingInterval;
import com.printflow.entity.enums.PlanTier;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.BillingSubscriptionRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.service.AuditLogService;
import com.printflow.service.BillingAccessService;
import com.printflow.service.BillingPlanConfigService;
import com.printflow.service.PlanLimitService;
import com.printflow.service.StripeBillingService;
import com.printflow.service.TenantContextService;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingControllerTest {

    private BillingController createController(StripeBillingService stripeBillingService,
                                               TenantContextService tenantContextService,
                                               BillingPlanConfigService billingPlanConfigService,
                                               AuditLogService auditLogService,
                                               StripeProperties stripeProperties) {
        return new BillingController(
            stripeBillingService,
            tenantContextService,
            mock(BillingAccessService.class),
            mock(BillingSubscriptionRepository.class),
            mock(PlanLimitService.class),
            mock(UserRepository.class),
            mock(WorkOrderRepository.class),
            mock(AttachmentRepository.class),
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );
    }

    @Test
    void startCheckoutReturnsConfigErrorWhenStripeNotConfigured() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(false);

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("price_pro_monthly");

        assertEquals("/admin/billing?error=billing.checkout.stripe_not_configured", view.getUrl());
        verifyNoInteractions(stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService);
    }

    @Test
    void startCheckoutReturnsMissingPriceWhenBlank() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(true);

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("   ");

        assertEquals("/admin/billing?error=billing.checkout.missing_price", view.getUrl());
        verifyNoInteractions(stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService);
    }

    @Test
    void startCheckoutReturnsStripeErrorOnRuntimeException() throws StripeException {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(true);

        Company company = new Company();
        company.setId(55L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(stripeBillingService.createSubscriptionCheckout(company, "price_x"))
            .thenThrow(new IllegalStateException("downstream failed"));

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("price_x");

        assertEquals("/admin/billing?error=billing.checkout.stripe_error", view.getUrl());
    }

    @Test
    void startCheckoutRedirectsToStripeUrlOnSuccess() throws StripeException {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(true);

        Company company = new Company();
        company.setId(77L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(stripeBillingService.createSubscriptionCheckout(company, "price_pro_m"))
            .thenReturn("https://checkout.stripe.com/session/test");
        when(billingPlanConfigService.findPlanForPriceId("price_pro_m")).thenReturn(PlanTier.PRO);
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of(
            PlanTier.FREE, Map.of(BillingInterval.MONTHLY, "free_m", BillingInterval.YEARLY, "free_y"),
            PlanTier.PRO, Map.of(BillingInterval.MONTHLY, "pro_m", BillingInterval.YEARLY, "pro_y"),
            PlanTier.TEAM, Map.of(BillingInterval.MONTHLY, "team_m", BillingInterval.YEARLY, "team_y")
        ));

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("price_pro_m");

        assertEquals("https://checkout.stripe.com/session/test", view.getUrl());
        verify(auditLogService).log(any(), eq("BillingCheckout"), eq(null), eq(null), eq("price_pro_m"),
            eq("Checkout started for plan PRO"));
    }
}
