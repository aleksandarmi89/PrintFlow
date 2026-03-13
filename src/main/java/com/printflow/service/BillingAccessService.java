package com.printflow.service;

import com.printflow.entity.BillingSubscription;
import com.printflow.repository.BillingSubscriptionRepository;
import com.printflow.repository.CompanyBillingView;
import com.printflow.repository.CompanyRepository;
import com.printflow.entity.enums.AuditAction;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Service
public class BillingAccessService {

    private static final Set<String> ACTIVE_STATUSES = Set.of("active", "trialing", "past_due");

    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final boolean billingEnforcementEnabled;
    private final Map<Long, CachedBillingView> billingViewCache = new ConcurrentHashMap<>();
    private static final Duration BILLING_VIEW_TTL = Duration.ofSeconds(30);

    public BillingAccessService(BillingSubscriptionRepository billingSubscriptionRepository,
                                CompanyRepository companyRepository,
                                AuditLogService auditLogService,
                                Clock clock,
                                @org.springframework.beans.factory.annotation.Value("${app.billing.enforce:true}") boolean billingEnforcementEnabled) {
        this.billingSubscriptionRepository = billingSubscriptionRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.billingEnforcementEnabled = billingEnforcementEnabled;
    }

    public boolean isBillingActive(Long companyId) {
        if (!billingEnforcementEnabled) {
            return true;
        }
        if (companyId == null) {
            return true;
        }
        return isBillingActive(companyId, getBillingView(companyId));
    }

    public boolean isTrialActive(Long companyId) {
        if (!billingEnforcementEnabled) {
            return true;
        }
        if (companyId == null) {
            return false;
        }
        return isTrialActive(getBillingView(companyId));
    }

    private boolean isTrialActive(CompanyBillingView view) {
        LocalDateTime trialEnd = view != null ? view.getTrialEnd() : null;
        if (trialEnd == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isAfter(trialEnd);
    }

    private boolean isOverrideActive(CompanyBillingView view) {
        if (view == null) {
            return false;
        }
        Boolean active = view.getBillingOverrideActive();
        if (active == null || !active) {
            return false;
        }
        LocalDateTime until = view.getBillingOverrideUntil();
        if (until == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isAfter(until);
    }

    public boolean hasActiveSubscription(Long companyId) {
        if (companyId == null) {
            return false;
        }
        BillingSubscription subscription = billingSubscriptionRepository
            .findByCompany_Id(companyId)
            .orElse(null);
        if (subscription == null || subscription.getStatus() == null) {
            return false;
        }
        String normalizedStatus = subscription.getStatus().trim().toLowerCase(Locale.ROOT);
        return ACTIVE_STATUSES.contains(normalizedStatus);
    }

    private boolean isBillingActive(Long companyId, CompanyBillingView view) {
        if (isOverrideActive(view)) {
            return true;
        }
        if (isTrialActive(view)) {
            return true;
        }
        return hasActiveSubscription(companyId);
    }

    public void assertBillingActiveForPremiumAction(Long companyId) {
        if (!billingEnforcementEnabled) {
            return;
        }
        if (companyId == null) {
            return;
        }
        CompanyBillingView view = getBillingView(companyId);
        if (isBillingActive(companyId, view)) {
            return;
        }
        LocalDateTime trialEnd = view != null ? view.getTrialEnd() : null;
        String message = "billing.notice.expired";
        if (trialEnd != null) {
            message = "billing.notice.expired_with_date";
        }
        if (auditLogService != null) {
            auditLogService.log(AuditAction.UPDATE, "BillingAccess", null, null, null,
                "Blocked premium action: " + message);
        }
        throw new BillingRequiredException(message);
    }

    public LocalDateTime getTrialEnd(Long companyId) {
        CompanyBillingView view = getBillingView(companyId);
        return view != null ? view.getTrialEnd() : null;
    }

    public void invalidateCompanyCache(Long companyId) {
        if (companyId == null) {
            return;
        }
        billingViewCache.remove(companyId);
    }

    private CompanyBillingView getBillingView(Long companyId) {
        if (companyId == null) {
            return null;
        }
        Instant now = Instant.now(clock);
        CachedBillingView cached = billingViewCache.get(companyId);
        if (cached != null && !cached.isExpired(now)) {
            return cached.view();
        }
        CompanyBillingView view = companyRepository.findBillingViewById(companyId).orElse(null);
        billingViewCache.put(companyId, new CachedBillingView(view, now));
        return view;
    }

    private record CachedBillingView(CompanyBillingView view, Instant fetchedAt) {
        boolean isExpired(Instant now) {
            return fetchedAt == null || now.isAfter(fetchedAt.plus(BILLING_VIEW_TTL));
        }
    }
}
