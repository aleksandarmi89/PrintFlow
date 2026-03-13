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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
        when(billingPlanConfigService.findPlanForPriceId("price_x")).thenReturn(PlanTier.PRO);
        when(stripeBillingService.createSubscriptionCheckout(company, "price_x"))
            .thenThrow(new IllegalStateException("downstream failed"));

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("price_x");

        assertEquals("/admin/billing?error=billing.checkout.stripe_error", view.getUrl());
    }

    @Test
    void startCheckoutThrowsForbiddenWhenTenantCompanyMissing() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(billingPlanConfigService.findPlanForPriceId("price_pro_m")).thenReturn(PlanTier.PRO);
        when(tenantContextService.getCurrentCompany()).thenReturn(null);

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.startCheckout("price_pro_m"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(stripeBillingService, auditLogService);
    }

    @Test
    void startCheckoutReturnsMissingPriceWhenPriceIsNotConfigured() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(billingPlanConfigService.findPlanForPriceId("price_unknown")).thenReturn(null);

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("price_unknown");

        assertEquals("/admin/billing?error=billing.checkout.missing_price", view.getUrl());
        verifyNoInteractions(stripeBillingService, tenantContextService, auditLogService);
    }

    @Test
    void startCheckoutRejectsFreePlanPriceId() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(billingPlanConfigService.findPlanForPriceId("price_free_m")).thenReturn(PlanTier.FREE);

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("price_free_m");

        assertEquals("/admin/billing?error=billing.checkout.missing_price", view.getUrl());
        verifyNoInteractions(stripeBillingService, tenantContextService, auditLogService);
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

    @Test
    void startCheckoutTrimsPriceIdBeforeCheckoutAndAudit() throws StripeException {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        when(stripeProperties.isConfigured()).thenReturn(true);

        Company company = new Company();
        company.setId(88L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(stripeBillingService.createSubscriptionCheckout(company, "price_trimmed"))
            .thenReturn("https://checkout.stripe.com/session/trimmed");
        when(billingPlanConfigService.findPlanForPriceId("price_trimmed")).thenReturn(PlanTier.PRO);

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        RedirectView view = controller.startCheckout("  price_trimmed  ");

        assertEquals("https://checkout.stripe.com/session/trimmed", view.getUrl());
        verify(stripeBillingService).createSubscriptionCheckout(company, "price_trimmed");
        verify(billingPlanConfigService).findPlanForPriceId("price_trimmed");
        verify(auditLogService).log(any(), eq("BillingCheckout"), eq(null), eq(null), eq("price_trimmed"),
            eq("Checkout started for plan PRO"));
    }

    @Test
    void updateBillingConfigRejectsNonSuperAdmin() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", "n/a", List.of(new SimpleGrantedAuthority("ADMIN")))
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.updateBillingConfig("f_m", "f_y", "p_m", "p_y", "t_m", "t_y"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(billingPlanConfigService, auditLogService);
    }

    @Test
    void updateBillingConfigRejectsMissingAuthentication() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        SecurityContextHolder.clearContext();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.updateBillingConfig("f_m", "f_y", "p_m", "p_y", "t_m", "t_y"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(billingPlanConfigService, auditLogService);
    }

    @Test
    void updateBillingConfigTrimsValuesAndAuditsForSuperAdmin() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "super",
                "n/a",
                List.of(new SimpleGrantedAuthority("SUPER_ADMIN"))
            )
        );

        RedirectView view = controller.updateBillingConfig(
            " free_m ", " free_y ", " pro_m ", " pro_y ", " team_m ", " team_y "
        );

        assertEquals("/admin/billing?success=billing.config.saved", view.getUrl());
        verify(billingPlanConfigService).upsertPriceId(PlanTier.FREE, BillingInterval.MONTHLY, "free_m");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.FREE, BillingInterval.YEARLY, "free_y");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.PRO, BillingInterval.MONTHLY, "pro_m");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.PRO, BillingInterval.YEARLY, "pro_y");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.TEAM, BillingInterval.MONTHLY, "team_m");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.TEAM, BillingInterval.YEARLY, "team_y");
        verify(auditLogService).log(
            any(),
            eq("BillingPlanConfig"),
            eq(null),
            eq(null),
            eq("FREE_M=free_m, FREE_Y=free_y, PRO_M=pro_m, PRO_Y=pro_y, TEAM_M=team_m, TEAM_Y=team_y"),
            eq("Updated Stripe plan price IDs")
        );
    }

    @Test
    void updateBillingConfigNormalizesNullValuesToEmptyStrings() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, stripeProperties
        );

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "super",
                "n/a",
                List.of(new SimpleGrantedAuthority("SUPER_ADMIN"))
            )
        );

        RedirectView view = controller.updateBillingConfig(null, " ", null, "  ", null, null);

        assertEquals("/admin/billing?success=billing.config.saved", view.getUrl());
        verify(billingPlanConfigService).upsertPriceId(PlanTier.FREE, BillingInterval.MONTHLY, "");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.FREE, BillingInterval.YEARLY, "");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.PRO, BillingInterval.MONTHLY, "");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.PRO, BillingInterval.YEARLY, "");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.TEAM, BillingInterval.MONTHLY, "");
        verify(billingPlanConfigService).upsertPriceId(PlanTier.TEAM, BillingInterval.YEARLY, "");
        verify(auditLogService).log(
            any(),
            eq("BillingPlanConfig"),
            eq(null),
            eq(null),
            eq("FREE_M=, FREE_Y=, PRO_M=, PRO_Y=, TEAM_M=, TEAM_Y="),
            eq("Updated Stripe plan price IDs")
        );
    }
}
