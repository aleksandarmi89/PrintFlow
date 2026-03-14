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
import org.springframework.ui.ExtendedModelMap;
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
    void startCheckoutReturnsConfigErrorWhenStripePropertiesMissing() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        BillingController controller = createController(
            stripeBillingService, tenantContextService, billingPlanConfigService, auditLogService, null
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
    void startCheckoutTrimsPriceIdBeforeConfiguredPlanLookup() {
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

        RedirectView view = controller.startCheckout("  price_unknown  ");

        assertEquals("/admin/billing?error=billing.checkout.missing_price", view.getUrl());
        verify(billingPlanConfigService).findPlanForPriceId("price_unknown");
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

    @Test
    void billingHomeHandlesNullPlanLimitsGracefully() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(101L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(101L)).thenReturn(true);
        when(billingAccessService.isTrialActive(101L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(101L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(101L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_Id(101L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(101L), any())).thenReturn(0L);
        when(attachmentRepository.sumFileSizeByCompanyId(101L)).thenReturn(0L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(null);
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of());
        when(stripeProperties.isConfigured()).thenReturn(false);
        when(stripeProperties.getMode()).thenReturn("test");

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.billingHome(model, null, null);

        assertEquals("admin/billing/index", view);
        assertEquals(0, model.getAttribute("maxUsers"));
        assertEquals(0, model.getAttribute("maxMonthlyOrders"));
        assertEquals(0L, model.getAttribute("maxStorageBytes"));
    }

    @Test
    void billingHomeHandlesPartialPriceConfigMapGracefully() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(102L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(102L)).thenReturn(true);
        when(billingAccessService.isTrialActive(102L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(102L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(102L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_Id(102L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(102L), any())).thenReturn(0L);
        when(attachmentRepository.sumFileSizeByCompanyId(102L)).thenReturn(0L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(new com.printflow.config.PlanLimitsProperties.PlanLimits());

        Map<PlanTier, Map<BillingInterval, String>> partial = new java.util.EnumMap<>(PlanTier.class);
        partial.put(PlanTier.PRO, Map.of(BillingInterval.MONTHLY, "price_pro_m"));
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(partial);
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(stripeProperties.getMode()).thenReturn("live");

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.billingHome(model, null, null);

        assertEquals("admin/billing/index", view);
        assertEquals("", model.getAttribute("priceIdFreeMonthly"));
        assertEquals("", model.getAttribute("priceIdFreeYearly"));
        assertEquals("price_pro_m", model.getAttribute("priceIdProMonthly"));
        assertEquals("", model.getAttribute("priceIdProYearly"));
        assertEquals("", model.getAttribute("priceIdTeamMonthly"));
        assertEquals("", model.getAttribute("priceIdTeamYearly"));
        assertEquals(true, model.getAttribute("priceConfigMissing"));
    }

    @Test
    void billingHomeCapsUsagePercentagesAtHundred() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(103L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(103L)).thenReturn(true);
        when(billingAccessService.isTrialActive(103L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(103L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(103L)).thenReturn(15L);
        when(workOrderRepository.countByCompany_Id(103L)).thenReturn(120L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(103L), any())).thenReturn(70L);
        when(attachmentRepository.sumFileSizeByCompanyId(103L)).thenReturn(5_000L);
        com.printflow.config.PlanLimitsProperties.PlanLimits limits = new com.printflow.config.PlanLimitsProperties.PlanLimits();
        limits.setMaxUsers(3);
        limits.setMaxMonthlyOrders(20);
        limits.setMaxStorageBytes(100L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(limits);
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of());
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(stripeProperties.getMode()).thenReturn("live");

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.billingHome(model, null, null);

        assertEquals("admin/billing/index", view);
        assertEquals(100, model.getAttribute("userUsagePercent"));
        assertEquals(100, model.getAttribute("orderUsagePercent"));
        assertEquals(100, model.getAttribute("storageUsagePercent"));
    }

    @Test
    void billingHomeShowsUnlimitedStorageLabelWhenStorageLimitIsZero() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(104L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(104L)).thenReturn(true);
        when(billingAccessService.isTrialActive(104L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(104L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(104L)).thenReturn(1L);
        when(workOrderRepository.countByCompany_Id(104L)).thenReturn(2L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(104L), any())).thenReturn(1L);
        when(attachmentRepository.sumFileSizeByCompanyId(104L)).thenReturn(2048L);
        com.printflow.config.PlanLimitsProperties.PlanLimits limits = new com.printflow.config.PlanLimitsProperties.PlanLimits();
        limits.setMaxUsers(10);
        limits.setMaxMonthlyOrders(50);
        limits.setMaxStorageBytes(0L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(limits);
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of());
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(stripeProperties.getMode()).thenReturn("live");

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.billingHome(model, null, null);

        assertEquals("admin/billing/index", view);
        assertEquals("∞", model.getAttribute("storageLimitLabel"));
        assertEquals(0, model.getAttribute("storageUsagePercent"));
    }

    @Test
    void billingHomeMarksPriceConfigAsCompleteWhenAllPaidPriceIdsExist() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(105L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(105L)).thenReturn(true);
        when(billingAccessService.isTrialActive(105L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(105L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(105L)).thenReturn(1L);
        when(workOrderRepository.countByCompany_Id(105L)).thenReturn(2L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(105L), any())).thenReturn(1L);
        when(attachmentRepository.sumFileSizeByCompanyId(105L)).thenReturn(1024L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(new com.printflow.config.PlanLimitsProperties.PlanLimits());
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of(
            PlanTier.FREE, Map.of(BillingInterval.MONTHLY, "price_free_m", BillingInterval.YEARLY, "price_free_y"),
            PlanTier.PRO, Map.of(BillingInterval.MONTHLY, "price_pro_m", BillingInterval.YEARLY, "price_pro_y"),
            PlanTier.TEAM, Map.of(BillingInterval.MONTHLY, "price_team_m", BillingInterval.YEARLY, "price_team_y")
        ));
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(stripeProperties.getMode()).thenReturn("live");

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.billingHome(model, null, null);

        assertEquals("admin/billing/index", view);
        assertEquals(false, model.getAttribute("priceConfigMissing"));
    }

    @Test
    void billingHomeDetectsTestPriceIdsCaseInsensitively() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(106L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(106L)).thenReturn(true);
        when(billingAccessService.isTrialActive(106L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(106L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(106L)).thenReturn(1L);
        when(workOrderRepository.countByCompany_Id(106L)).thenReturn(1L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(106L), any())).thenReturn(1L);
        when(attachmentRepository.sumFileSizeByCompanyId(106L)).thenReturn(1024L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(new com.printflow.config.PlanLimitsProperties.PlanLimits());
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of(
            PlanTier.FREE, Map.of(BillingInterval.MONTHLY, "price_free_m", BillingInterval.YEARLY, "price_free_y"),
            PlanTier.PRO, Map.of(BillingInterval.MONTHLY, "  PRICE_TEST_pro_m  ", BillingInterval.YEARLY, "price_pro_y"),
            PlanTier.TEAM, Map.of(BillingInterval.MONTHLY, "price_team_m", BillingInterval.YEARLY, "price_team_y")
        ));
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(stripeProperties.getMode()).thenReturn("test");

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.billingHome(model, null, null);

        assertEquals("admin/billing/index", view);
        assertEquals(true, model.getAttribute("priceTestMode"));
    }

    @Test
    void billingHomeWorksWhenStripePropertiesBeanIsNull() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            null
        );

        Company company = new Company();
        company.setId(107L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(107L)).thenReturn(true);
        when(billingAccessService.isTrialActive(107L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(107L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(107L)).thenReturn(1L);
        when(workOrderRepository.countByCompany_Id(107L)).thenReturn(1L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(107L), any())).thenReturn(1L);
        when(attachmentRepository.sumFileSizeByCompanyId(107L)).thenReturn(512L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(new com.printflow.config.PlanLimitsProperties.PlanLimits());
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.billingHome(model, null, null);

        assertEquals("admin/billing/index", view);
        assertEquals(false, model.getAttribute("stripeConfigured"));
        assertEquals("test", model.getAttribute("stripeMode"));
    }

    @Test
    void billingHomeSetsLocalizedKeysOnlyForBillingAndPlanMessages() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(108L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(108L)).thenReturn(true);
        when(billingAccessService.isTrialActive(108L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(108L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(108L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_Id(108L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(108L), any())).thenReturn(0L);
        when(attachmentRepository.sumFileSizeByCompanyId(108L)).thenReturn(0L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(new com.printflow.config.PlanLimitsProperties.PlanLimits());
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of());
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(stripeProperties.getMode()).thenReturn("live");

        ExtendedModelMap model = new ExtendedModelMap();
        controller.billingHome(model, "plan.limit.users", "billing.config.saved");

        assertEquals("plan.limit.users", model.getAttribute("error"));
        assertEquals("plan.limit.users", model.getAttribute("errorKey"));
        assertEquals("billing.config.saved", model.getAttribute("success"));
        assertEquals("billing.config.saved", model.getAttribute("successKey"));
    }

    @Test
    void billingHomeDoesNotSetLocalizedKeysForPlainMessages() {
        StripeBillingService stripeBillingService = mock(StripeBillingService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        BillingSubscriptionRepository billingSubscriptionRepository = mock(BillingSubscriptionRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        BillingPlanConfigService billingPlanConfigService = mock(BillingPlanConfigService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);

        BillingController controller = new BillingController(
            stripeBillingService,
            tenantContextService,
            billingAccessService,
            billingSubscriptionRepository,
            planLimitService,
            userRepository,
            workOrderRepository,
            attachmentRepository,
            billingPlanConfigService,
            auditLogService,
            stripeProperties
        );

        Company company = new Company();
        company.setId(109L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(billingAccessService.isBillingActive(109L)).thenReturn(true);
        when(billingAccessService.isTrialActive(109L)).thenReturn(false);
        when(billingSubscriptionRepository.findByCompany_Id(109L)).thenReturn(java.util.Optional.empty());
        when(userRepository.countByCompany_IdAndActiveTrue(109L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_Id(109L)).thenReturn(0L);
        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(eq(109L), any())).thenReturn(0L);
        when(attachmentRepository.sumFileSizeByCompanyId(109L)).thenReturn(0L);
        when(planLimitService.getLimitsForCompany(company)).thenReturn(new com.printflow.config.PlanLimitsProperties.PlanLimits());
        when(billingPlanConfigService.getPriceIdsByInterval()).thenReturn(Map.of());
        when(stripeProperties.isConfigured()).thenReturn(true);
        when(stripeProperties.getMode()).thenReturn("live");

        ExtendedModelMap model = new ExtendedModelMap();
        controller.billingHome(model, "Something broke", "Saved");

        assertEquals("Something broke", model.getAttribute("error"));
        assertEquals("Saved", model.getAttribute("success"));
        assertEquals(null, model.getAttribute("errorKey"));
        assertEquals(null, model.getAttribute("successKey"));
    }
}
