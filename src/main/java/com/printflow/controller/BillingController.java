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

@Controller
@RequestMapping("/admin/billing")
public class BillingController extends BaseController {

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
        model.addAttribute("error", error);
        if (error != null && (error.startsWith("billing.") || error.startsWith("plan."))) {
            model.addAttribute("errorKey", error);
        }
        model.addAttribute("success", success);
        if (success != null && success.startsWith("billing.")) {
            model.addAttribute("successKey", success);
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

        var limits = planLimitService.getLimitsForCompany(company);
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

        model.addAttribute("userUsagePercent", percent(activeUsers, maxUsers));
        model.addAttribute("orderUsagePercent", percent(monthOrders, maxMonthlyOrders));
        model.addAttribute("storageUsagePercent", percent(storageUsedBytes, maxStorageBytes));

        model.addAttribute("storageUsedLabel", formatBytes(storageUsedBytes));
        model.addAttribute("storageLimitLabel", maxStorageBytes > 0 ? formatBytes(maxStorageBytes) : "∞");
        var priceIds = billingPlanConfigService.getPriceIdsByInterval();
        String priceIdFreeMonthly = priceIds.get(PlanTier.FREE).get(BillingInterval.MONTHLY);
        String priceIdFreeYearly = priceIds.get(PlanTier.FREE).get(BillingInterval.YEARLY);
        String priceIdProMonthly = priceIds.get(PlanTier.PRO).get(BillingInterval.MONTHLY);
        String priceIdProYearly = priceIds.get(PlanTier.PRO).get(BillingInterval.YEARLY);
        String priceIdTeamMonthly = priceIds.get(PlanTier.TEAM).get(BillingInterval.MONTHLY);
        String priceIdTeamYearly = priceIds.get(PlanTier.TEAM).get(BillingInterval.YEARLY);
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
        return "admin/billing/index";
    }

    @PostMapping("/checkout")
    public RedirectView startCheckout(@RequestParam("priceId") String priceId) {
        if (stripeProperties != null && !stripeProperties.isConfigured()) {
            return new RedirectView("/admin/billing?error=billing.checkout.stripe_not_configured", true);
        }
        if (priceId == null || priceId.isBlank()) {
            return new RedirectView("/admin/billing?error=billing.checkout.missing_price", true);
        }
        String normalizedPriceId = priceId.trim();
        Company company = tenantContextService.getCurrentCompany();
        if (company == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not resolved");
        }
        try {
            String checkoutUrl = stripeBillingService.createSubscriptionCheckout(company, normalizedPriceId);
            var plan = billingPlanConfigService.findPlanForPriceId(normalizedPriceId);
            String description = "Checkout started";
            if (plan != null) {
                description = "Checkout started for plan " + plan;
            }
            auditLogService.log(AuditAction.UPDATE, "BillingCheckout", null, null, normalizedPriceId, description);
            RedirectView redirect = new RedirectView(checkoutUrl);
            redirect.setExposeModelAttributes(false);
            redirect.setHttp10Compatible(false);
            return redirect;
        } catch (StripeException ex) {
            return new RedirectView("/admin/billing?error=billing.checkout.stripe_error", true);
        } catch (RuntimeException ex) {
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
        String trimmed = value.trim().toLowerCase();
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
}
