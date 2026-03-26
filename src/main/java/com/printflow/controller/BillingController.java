package com.printflow.controller;

import com.printflow.entity.BillingSubscription;
import com.printflow.entity.Company;
import com.printflow.entity.enums.PlanTier;
import com.printflow.entity.enums.BillingInterval;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.BillingSubscriptionRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.service.BillingAccessService;
import com.printflow.service.BillingPlanConfigService;
import com.printflow.service.AuditLogService;
import com.printflow.entity.enums.AuditAction;
import com.printflow.service.PlanLimitService;
import com.printflow.service.StripeBillingService;
import com.printflow.service.TenantContextService;
import com.printflow.config.StripeProperties;
import com.printflow.config.PlanLimitsProperties;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/admin/billing")
public class BillingController extends BaseController {
    private static final java.util.regex.Pattern LOCALIZED_MESSAGE_KEY_PATTERN =
        java.util.regex.Pattern.compile("^[a-z0-9._-]{1,120}$");

    private final StripeBillingService stripeBillingService;
    private final TenantContextService tenantContextService;
    private final BillingAccessService billingAccessService;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final PlanLimitService planLimitService;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AttachmentRepository attachmentRepository;
    private final BillingPlanConfigService billingPlanConfigService;
    private final AuditLogService auditLogService;
    private final StripeProperties stripeProperties;

    public BillingController(StripeBillingService stripeBillingService,
                             TenantContextService tenantContextService,
                             BillingAccessService billingAccessService,
                             BillingSubscriptionRepository billingSubscriptionRepository,
                             PlanLimitService planLimitService,
                             UserRepository userRepository,
                             WorkOrderRepository workOrderRepository,
                             AttachmentRepository attachmentRepository,
                             BillingPlanConfigService billingPlanConfigService,
                             AuditLogService auditLogService,
                             StripeProperties stripeProperties) {
        this.stripeBillingService = stripeBillingService;
        this.tenantContextService = tenantContextService;
        this.billingAccessService = billingAccessService;
        this.billingSubscriptionRepository = billingSubscriptionRepository;
        this.planLimitService = planLimitService;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.attachmentRepository = attachmentRepository;
        this.billingPlanConfigService = billingPlanConfigService;
        this.auditLogService = auditLogService;
        this.stripeProperties = stripeProperties;
    }

    @GetMapping
    public String billingHome(Model model,
                              @RequestParam(required = false) String error,
                              @RequestParam(required = false) String success) {
        String normalizedError = normalizeOptional(error);
        String normalizedSuccess = normalizeOptional(success);
        model.addAttribute("error", normalizedError);
        if (isLocalizedMessageKey(normalizedError, "billing.", "plan.")) {
            model.addAttribute("errorKey", normalizedError);
        }
        model.addAttribute("success", normalizedSuccess);
        if (isLocalizedMessageKey(normalizedSuccess, "billing.")) {
            model.addAttribute("successKey", normalizedSuccess);
        }
        Company company = tenantContextService.getCurrentCompany();
        if (company == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not resolved");
        }
        PlanTier plan = company.getPlan() != null ? company.getPlan() : PlanTier.FREE;
        LocalDateTime trialEnd = company.getTrialEnd();
        LocalDateTime trialStart = company.getTrialStart();
        boolean billingActive = billingAccessService.isBillingActive(company.getId());
        boolean trialActive = billingAccessService.isTrialActive(company.getId());
        boolean trialExpired = trialEnd != null && LocalDateTime.now().isAfter(trialEnd);
        Long trialDaysRemaining = null;
        Long trialDaysOverdue = null;
        if (trialEnd != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), trialEnd.toLocalDate());
            trialDaysRemaining = Math.max(days, 0);
            if (days < 0) {
                trialDaysOverdue = Math.abs(days);
            }
        }

        BillingSubscription subscription = billingSubscriptionRepository
            .findByCompany_Id(company.getId())
            .orElse(null);

        long activeUsers = userRepository.countByCompany_IdAndActiveTrue(company.getId());
        long totalOrders = workOrderRepository.countByCompany_Id(company.getId());
        LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIDNIGHT);
        long monthOrders = workOrderRepository.countByCompany_IdAndCreatedAtAfter(company.getId(), monthStart);
        Long storageUsedRaw = attachmentRepository.sumFileSizeByCompanyId(company.getId());
        long storageUsedBytes = storageUsedRaw != null ? storageUsedRaw : 0L;

        PlanLimitsProperties.PlanLimits limits = planLimitService.getLimitsForCompany(company);
        if (limits == null) {
            limits = new PlanLimitsProperties.PlanLimits();
        }
        int maxUsers = limits.getMaxUsers();
        int maxMonthlyOrders = limits.getMaxMonthlyOrders();
        long maxStorageBytes = limits.getMaxStorageBytes();

        model.addAttribute("company", company);
        model.addAttribute("plan", plan);
        model.addAttribute("trialStart", trialStart);
        model.addAttribute("trialEnd", trialEnd);
        model.addAttribute("billingActive", billingActive);
        model.addAttribute("trialActive", trialActive);
        model.addAttribute("trialExpired", trialExpired);
        model.addAttribute("trialDaysRemaining", trialDaysRemaining);
        model.addAttribute("trialDaysOverdue", trialDaysOverdue);
        model.addAttribute("subscription", subscription);

        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("monthOrders", monthOrders);
        model.addAttribute("storageUsedBytes", storageUsedBytes);

        model.addAttribute("maxUsers", maxUsers);
        model.addAttribute("maxMonthlyOrders", maxMonthlyOrders);
        model.addAttribute("maxStorageBytes", maxStorageBytes);

        int userUsagePercent = percent(activeUsers, maxUsers);
        int orderUsagePercent = percent(monthOrders, maxMonthlyOrders);
        int storageUsagePercent = percent(storageUsedBytes, maxStorageBytes);

        boolean userLimitReached = maxUsers > 0 && activeUsers >= maxUsers;
        boolean orderLimitReached = maxMonthlyOrders > 0 && monthOrders >= maxMonthlyOrders;
        boolean storageLimitReached = maxStorageBytes > 0 && storageUsedBytes >= maxStorageBytes;
        boolean anyLimitReached = userLimitReached || orderLimitReached || storageLimitReached;
        boolean nearAnyLimit = userUsagePercent >= 90 || orderUsagePercent >= 90 || storageUsagePercent >= 90;

        model.addAttribute("userUsagePercent", userUsagePercent);
        model.addAttribute("orderUsagePercent", orderUsagePercent);
        model.addAttribute("storageUsagePercent", storageUsagePercent);
        model.addAttribute("userLimitReached", userLimitReached);
        model.addAttribute("orderLimitReached", orderLimitReached);
        model.addAttribute("storageLimitReached", storageLimitReached);
        model.addAttribute("anyLimitReached", anyLimitReached);
        model.addAttribute("nearAnyLimit", nearAnyLimit);
        model.addAttribute("userUsageBarClass", usageBarClass(userUsagePercent));
        model.addAttribute("orderUsageBarClass", usageBarClass(orderUsagePercent));
        model.addAttribute("storageUsageBarClass", usageBarClass(storageUsagePercent));
        model.addAttribute("billingState", resolveBillingState(billingActive, trialActive, trialExpired, subscription));

        model.addAttribute("storageUsedLabel", formatBytes(storageUsedBytes));
        model.addAttribute("storageLimitLabel", maxStorageBytes > 0 ? formatBytes(maxStorageBytes) : "∞");
        var priceIds = billingPlanConfigService.getPriceIdsByInterval();
        String priceIdFreeMonthly = resolvePriceId(priceIds, PlanTier.FREE, BillingInterval.MONTHLY);
        String priceIdFreeYearly = resolvePriceId(priceIds, PlanTier.FREE, BillingInterval.YEARLY);
        String priceIdProMonthly = resolvePriceId(priceIds, PlanTier.PRO, BillingInterval.MONTHLY);
        String priceIdProYearly = resolvePriceId(priceIds, PlanTier.PRO, BillingInterval.YEARLY);
        String priceIdTeamMonthly = resolvePriceId(priceIds, PlanTier.TEAM, BillingInterval.MONTHLY);
        String priceIdTeamYearly = resolvePriceId(priceIds, PlanTier.TEAM, BillingInterval.YEARLY);
        model.addAttribute("priceIdFreeMonthly", priceIdFreeMonthly);
        model.addAttribute("priceIdFreeYearly", priceIdFreeYearly);
        model.addAttribute("priceIdProMonthly", priceIdProMonthly);
        model.addAttribute("priceIdProYearly", priceIdProYearly);
        model.addAttribute("priceIdTeamMonthly", priceIdTeamMonthly);
        model.addAttribute("priceIdTeamYearly", priceIdTeamYearly);
        model.addAttribute("selectedPlan", "PRO");
        model.addAttribute("selectedInterval", "MONTHLY");
        boolean missingAnyPaid = isBlank(priceIdProMonthly) || isBlank(priceIdProYearly)
            || isBlank(priceIdTeamMonthly) || isBlank(priceIdTeamYearly);
        model.addAttribute("priceConfigMissing", missingAnyPaid);
        boolean testMode = isTestPrice(priceIdProMonthly) || isTestPrice(priceIdProYearly)
            || isTestPrice(priceIdTeamMonthly) || isTestPrice(priceIdTeamYearly);
        model.addAttribute("priceTestMode", testMode);
        boolean stripeConfigured = stripeProperties != null && stripeProperties.isConfigured();
        model.addAttribute("stripeConfigured", stripeConfigured);
        model.addAttribute("stripeMode", stripeProperties != null ? stripeProperties.getMode() : "test");
        boolean subscriptionActive = subscription != null
            && subscription.getStatus() != null
            && subscription.getStatus().equalsIgnoreCase("active");
        Map<String, String> billingNextAction = resolveBillingNextAction(
            stripeConfigured,
            missingAnyPaid,
            anyLimitReached,
            nearAnyLimit,
            trialExpired,
            trialDaysRemaining,
            subscriptionActive,
            isSuperAdmin()
        );
        model.addAttribute("billingNextActionMessageKey", billingNextAction.get("messageKey"));
        model.addAttribute("billingNextActionCtaKey", billingNextAction.get("ctaKey"));
        model.addAttribute("billingNextActionHref", billingNextAction.get("href"));
        model.addAttribute("billingTimeline", buildBillingTimeline(trialStart, trialEnd, trialExpired, subscription));
        return "admin/billing/index";
    }

    @PostMapping("/checkout")
    public RedirectView startCheckout(@RequestParam("priceId") String priceId) {
        if (stripeProperties == null || !stripeProperties.isConfigured()) {
            return new RedirectView("/admin/billing?error=billing.checkout.stripe_not_configured", true);
        }
        if (priceId == null || priceId.isBlank()) {
            return new RedirectView("/admin/billing?error=billing.checkout.missing_price", true);
        }
        String normalizedPriceId = priceId.trim();
        PlanTier plan = billingPlanConfigService.findPlanForPriceId(normalizedPriceId);
        if (plan == null) {
            return new RedirectView("/admin/billing?error=billing.checkout.missing_price", true);
        }
        if (plan == PlanTier.FREE) {
            return new RedirectView("/admin/billing?error=billing.checkout.missing_price", true);
        }
        Company company = tenantContextService.getCurrentCompany();
        if (company == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not resolved");
        }
        try {
            String checkoutUrl = stripeBillingService.createSubscriptionCheckout(company, normalizedPriceId);
            String description = "Checkout started for plan " + plan;
            auditLogService.log(AuditAction.UPDATE, "BillingCheckout", null, null, normalizedPriceId, description);
            RedirectView redirect = new RedirectView(checkoutUrl);
            redirect.setExposeModelAttributes(false);
            redirect.setHttp10Compatible(false);
            return redirect;
        } catch (StripeException | RuntimeException ex) {
            return new RedirectView("/admin/billing?error=billing.checkout.stripe_error", true);
        }
    }

    @PostMapping("/config")
    public RedirectView updateBillingConfig(@RequestParam(required = false) String priceIdFreeMonthly,
                                            @RequestParam(required = false) String priceIdFreeYearly,
                                            @RequestParam(required = false) String priceIdProMonthly,
                                            @RequestParam(required = false) String priceIdProYearly,
                                            @RequestParam(required = false) String priceIdTeamMonthly,
                                            @RequestParam(required = false) String priceIdTeamYearly) {
        if (!isSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        String freeMonthly = safe(priceIdFreeMonthly);
        String freeYearly = safe(priceIdFreeYearly);
        String proMonthly = safe(priceIdProMonthly);
        String proYearly = safe(priceIdProYearly);
        String teamMonthly = safe(priceIdTeamMonthly);
        String teamYearly = safe(priceIdTeamYearly);

        billingPlanConfigService.upsertPriceId(PlanTier.FREE, BillingInterval.MONTHLY, freeMonthly);
        billingPlanConfigService.upsertPriceId(PlanTier.FREE, BillingInterval.YEARLY, freeYearly);
        billingPlanConfigService.upsertPriceId(PlanTier.PRO, BillingInterval.MONTHLY, proMonthly);
        billingPlanConfigService.upsertPriceId(PlanTier.PRO, BillingInterval.YEARLY, proYearly);
        billingPlanConfigService.upsertPriceId(PlanTier.TEAM, BillingInterval.MONTHLY, teamMonthly);
        billingPlanConfigService.upsertPriceId(PlanTier.TEAM, BillingInterval.YEARLY, teamYearly);
        String newValue = "FREE_M=" + freeMonthly + ", FREE_Y=" + freeYearly
            + ", PRO_M=" + proMonthly + ", PRO_Y=" + proYearly
            + ", TEAM_M=" + teamMonthly + ", TEAM_Y=" + teamYearly;
        auditLogService.log(AuditAction.UPDATE, "BillingPlanConfig", null, null, newValue,
            "Updated Stripe plan price IDs");
        return new RedirectView("/admin/billing?success=billing.config.saved", true);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolvePriceId(java.util.Map<PlanTier, java.util.Map<BillingInterval, String>> priceIds,
                                  PlanTier planTier,
                                  BillingInterval interval) {
        if (priceIds == null || planTier == null || interval == null) {
            return "";
        }
        java.util.Map<BillingInterval, String> byInterval = priceIds.get(planTier);
        if (byInterval == null) {
            return "";
        }
        return safe(byInterval.get(interval));
    }

    private boolean isSuperAdmin() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
            .anyMatch(a -> "SUPER_ADMIN".equals(a.getAuthority()));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isTestPrice(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("price_test_");
    }

    private int percent(long used, long max) {
        if (max <= 0) {
            return 0;
        }
        double pct = (double) used / (double) max * 100.0;
        if (pct < 0) {
            return 0;
        }
        if (pct > 100) {
            return 100;
        }
        return (int) Math.round(pct);
    }

    private String usageBarClass(int percent) {
        if (percent >= 100) {
            return "bg-red-600";
        }
        if (percent >= 90) {
            return "bg-amber-500";
        }
        return "bg-blue-500";
    }

    private String resolveBillingState(boolean billingActive,
                                       boolean trialActive,
                                       boolean trialExpired,
                                       BillingSubscription subscription) {
        if (!billingActive && trialExpired) {
            return "INACTIVE";
        }
        if (trialActive) {
            return "TRIAL";
        }
        if (subscription != null && subscription.getStatus() != null && subscription.getStatus().equalsIgnoreCase("active")) {
            return "ACTIVE";
        }
        return billingActive ? "ATTENTION" : "INACTIVE";
    }

    private Map<String, String> resolveBillingNextAction(boolean stripeConfigured,
                                                         boolean priceConfigMissing,
                                                         boolean anyLimitReached,
                                                         boolean nearAnyLimit,
                                                         boolean trialExpired,
                                                         Long trialDaysRemaining,
                                                         boolean subscriptionActive,
                                                         boolean superAdmin) {
        if (!stripeConfigured) {
            return Map.of(
                "messageKey", "billing.next_action.stripe_missing",
                "ctaKey", superAdmin ? "billing.next_action.cta.open_billing" : "billing.next_action.cta.open_company",
                "href", superAdmin ? "/admin/billing" : "/admin/company"
            );
        }
        if (priceConfigMissing) {
            return Map.of(
                "messageKey", superAdmin ? "billing.next_action.price_missing_superadmin" : "billing.next_action.price_missing_admin",
                "ctaKey", superAdmin ? "billing.next_action.cta.open_billing" : "billing.next_action.cta.open_company",
                "href", superAdmin ? "/admin/billing" : "/admin/company"
            );
        }
        if (anyLimitReached || (trialExpired && !subscriptionActive)) {
            return Map.of(
                "messageKey", "billing.next_action.expired_or_limit",
                "ctaKey", "billing.next_action.cta.open_checkout",
                "href", "/admin/billing#checkout"
            );
        }
        if (nearAnyLimit) {
            return Map.of(
                "messageKey", "billing.next_action.near_limit",
                "ctaKey", "billing.next_action.cta.open_checkout",
                "href", "/admin/billing#checkout"
            );
        }
        if (trialDaysRemaining != null && trialDaysRemaining > 0 && trialDaysRemaining <= 5 && !subscriptionActive) {
            return Map.of(
                "messageKey", "billing.next_action.trial_ending",
                "ctaKey", "billing.next_action.cta.open_checkout",
                "href", "/admin/billing#checkout"
            );
        }
        return Map.of(
            "messageKey", "billing.next_action.healthy",
            "ctaKey", "billing.next_action.cta.open_dashboard",
            "href", "/admin/dashboard"
        );
    }

    private List<Map<String, Object>> buildBillingTimeline(LocalDateTime trialStart,
                                                           LocalDateTime trialEnd,
                                                           boolean trialExpired,
                                                           BillingSubscription subscription) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        if (trialStart != null) {
            timeline.add(Map.of("at", trialStart, "messageKey", "billing.timeline.trial_started", "detail", ""));
        }
        if (trialEnd != null) {
            timeline.add(Map.of(
                "at", trialEnd,
                "messageKey", trialExpired ? "billing.timeline.trial_expired" : "billing.timeline.trial_ends",
                "detail", ""
            ));
        }
        if (subscription != null) {
            if (subscription.getCreatedAt() != null) {
                timeline.add(Map.of(
                    "at", subscription.getCreatedAt(),
                    "messageKey", "billing.timeline.subscription_created",
                    "detail", subscription.getStatus() != null ? subscription.getStatus() : ""
                ));
            }
            if (subscription.getUpdatedAt() != null) {
                timeline.add(Map.of(
                    "at", subscription.getUpdatedAt(),
                    "messageKey", "billing.timeline.subscription_updated",
                    "detail", subscription.getStatus() != null ? subscription.getStatus() : ""
                ));
            }
            if (subscription.getCurrentPeriodEnd() != null) {
                timeline.add(Map.of(
                    "at", subscription.getCurrentPeriodEnd(),
                    "messageKey", "billing.timeline.period_end",
                    "detail", subscription.getStatus() != null ? subscription.getStatus() : ""
                ));
            }
        }
        timeline.sort(Comparator.comparing(item -> (LocalDateTime) item.get("at"), Comparator.reverseOrder()));
        if (timeline.size() > 8) {
            return timeline.subList(0, 8);
        }
        return timeline;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isLocalizedMessageKey(String value, String... allowedPrefixes) {
        if (value == null || !LOCALIZED_MESSAGE_KEY_PATTERN.matcher(value).matches()) {
            return false;
        }
        for (String allowedPrefix : allowedPrefixes) {
            if (value.startsWith(allowedPrefix)) {
                return true;
            }
        }
        return false;
    }
}
