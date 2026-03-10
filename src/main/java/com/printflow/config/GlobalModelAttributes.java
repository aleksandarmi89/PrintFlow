package com.printflow.config;

import com.printflow.repository.MailSettingsRepository;
import com.printflow.service.NotificationService;
import com.printflow.service.TenantContextService;
import com.printflow.service.BillingAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalModelAttributes {

    private final TenantContextService tenantContextService;
    private final NotificationService notificationService;
    private final BillingAccessService billingAccessService;
    private final MailSettingsRepository mailSettingsRepository;

    public GlobalModelAttributes(TenantContextService tenantContextService,
                                 NotificationService notificationService,
                                 BillingAccessService billingAccessService,
                                 MailSettingsRepository mailSettingsRepository) {
        this.tenantContextService = tenantContextService;
        this.notificationService = notificationService;
        this.billingAccessService = billingAccessService;
        this.mailSettingsRepository = mailSettingsRepository;
    }

    @ModelAttribute
    public void addNotificationAttributes(Model model, HttpServletRequest request) {
        if (request != null) {
            model.addAttribute("requestUri", request.getRequestURI());
        }
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
            model.addAttribute("companyPlan", company.getPlan());
        }
        boolean smtpConfigured = mailSettingsRepository.findByCompany_Id(companyId)
            .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
            .filter(s -> s.getSmtpHost() != null && !s.getSmtpHost().isBlank())
            .filter(s -> s.getSmtpPort() != null)
            .filter(s -> s.getSmtpUsername() != null && !s.getSmtpUsername().isBlank())
            .filter(s -> s.getSmtpPasswordEnc() != null && !s.getSmtpPasswordEnc().isBlank())
            .isPresent();
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
}
