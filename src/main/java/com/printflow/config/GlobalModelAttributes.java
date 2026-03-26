package com.printflow.config;

import com.printflow.repository.MailSettingsRepository;
import com.printflow.repository.UserRepository;
import com.printflow.service.MailSettingsService;
import com.printflow.service.NotificationService;
import com.printflow.service.TenantContextService;
import com.printflow.service.BillingAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@ControllerAdvice
public class GlobalModelAttributes {

    private final TenantContextService tenantContextService;
    private final NotificationService notificationService;
    private final BillingAccessService billingAccessService;
    private final MailSettingsRepository mailSettingsRepository;
    private final MailSettingsService mailSettingsService;
    private final UserRepository userRepository;

    public GlobalModelAttributes(TenantContextService tenantContextService,
                                 NotificationService notificationService,
                                 BillingAccessService billingAccessService,
                                 MailSettingsRepository mailSettingsRepository,
                                 MailSettingsService mailSettingsService,
                                 UserRepository userRepository) {
        this.tenantContextService = tenantContextService;
        this.notificationService = notificationService;
        this.billingAccessService = billingAccessService;
        this.mailSettingsRepository = mailSettingsRepository;
        this.mailSettingsService = mailSettingsService;
        this.userRepository = userRepository;
    }

    @ModelAttribute
    public void addNotificationAttributes(Model model, HttpServletRequest request) {
        if (request != null) {
            model.addAttribute("requestUri", request.getRequestURI());
            model.addAttribute("requestQuery", request.getQueryString());
        }
        addPlatformFooterDefaults(model);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }
        Long userId = tenantContextService.getCurrentUserId();
        if (userId == null) {
            return;
        }
        model.addAttribute("notificationCount", notificationService.getUnreadNotificationCount(userId));
        model.addAttribute("recentNotifications", notificationService.getRecentNotifications(userId, 5));

        Long companyId = tenantContextService.getCurrentCompanyId();
        if (companyId == null) {
            return;
        }
        var company = tenantContextService.getCurrentCompany();
        if (company != null) {
            model.addAttribute("companyPlan", safeValue(company::getPlan));
            model.addAttribute("footerCompanyName", safeValue(company::getName));
            model.addAttribute("footerCompanyEmail", safeValue(company::getEmail));
            model.addAttribute("footerCompanyPhone", safeValue(company::getPhone));
            model.addAttribute("footerCompanyAddress", safeValue(company::getAddress));
            model.addAttribute("footerCompanyWebsite", safeValue(company::getWebsite));
        }
        var mailSettings = mailSettingsRepository.findByCompany_Id(companyId).orElse(null);
        boolean smtpConfigured = safeBoolean(() -> mailSettingsService.isConfiguredWithLegacyFallback(company, mailSettings), false);
        model.addAttribute("smtpConfigured", smtpConfigured);
        if (tenantContextService.isSuperAdmin()) {
            model.addAttribute("billingActive", true);
            return;
        }
        boolean billingActive = billingAccessService.isBillingActive(companyId);
        model.addAttribute("billingActive", billingActive);
        var trialEnd = billingAccessService.getTrialEnd(companyId);
        Long trialDaysOverdue = null;
        if (trialEnd != null) {
            long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.now(),
                trialEnd.toLocalDate());
            model.addAttribute("trialDaysRemaining", Math.max(daysRemaining, 0));
            if (daysRemaining < 0) {
                trialDaysOverdue = Math.abs(daysRemaining);
            }
        }
        if (!billingActive) {
            model.addAttribute("billingExpired", true);
            model.addAttribute("trialEnd", trialEnd);
            model.addAttribute("trialDaysOverdue", trialDaysOverdue);
            model.addAttribute("billingLockMessageKey", "billing.lock.expired");
        } else if (trialEnd != null && billingAccessService.isTrialActive(companyId)) {
            model.addAttribute("billingTrialActive", true);
            model.addAttribute("trialEnd", trialEnd);
            model.addAttribute("billingLockMessageKey", "billing.lock.trial");
        }
    }

    private void addPlatformFooterDefaults(Model model) {
        userRepository.findFirstActiveSuperAdmin().ifPresent(superAdmin -> {
            if (superAdmin.getFullName() != null && !superAdmin.getFullName().isBlank()) {
                model.addAttribute("footerCompanyName", superAdmin.getFullName());
            }
            if (superAdmin.getEmail() != null && !superAdmin.getEmail().isBlank()) {
                model.addAttribute("footerCompanyEmail", superAdmin.getEmail());
            }
            if (superAdmin.getPhone() != null && !superAdmin.getPhone().isBlank()) {
                model.addAttribute("footerCompanyPhone", superAdmin.getPhone());
            }
            var superAdminCompany = safeValue(superAdmin::getCompany);
            if (superAdminCompany != null) {
                String address = safeValue(superAdminCompany::getAddress);
                if (address != null && !address.isBlank()) {
                    model.addAttribute("footerCompanyAddress", address);
                }
                String website = safeValue(superAdminCompany::getWebsite);
                if (website != null && !website.isBlank()) {
                    model.addAttribute("footerCompanyWebsite", website);
                }
            }
        });
    }

    private <T> T safeValue(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            // Defensive guard for detached proxies in global model population.
            return null;
        }
    }

    private boolean safeBoolean(BooleanSupplier supplier, boolean fallback) {
        try {
            return supplier.getAsBoolean();
        } catch (RuntimeException ex) {
            // Defensive guard for detached proxies in global model population.
            return fallback;
        }
    }
}
