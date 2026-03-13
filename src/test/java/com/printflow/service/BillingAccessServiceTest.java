package com.printflow.service;

import com.printflow.entity.BillingSubscription;
import com.printflow.repository.CompanyBillingView;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.BillingSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class BillingAccessServiceTest {

    @Test
    void trialActive_allowsBilling() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-02-01T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        Long companyId = 1L;
        when(companyRepository.findBillingViewById(companyId))
            .thenReturn(Optional.of(viewWithTrialEnd(LocalDateTime.of(2026, 2, 5, 0, 0))));

        assertTrue(service.isBillingActive(companyId));
        verify(companyRepository).findBillingViewById(companyId);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void expiredTrial_withActiveSubscription_allowsBilling() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        BillingSubscription sub = new BillingSubscription();
        sub.setStatus("active");
        Long companyId = 1L;
        when(repo.findByCompany_Id(companyId)).thenReturn(Optional.of(sub));
        when(companyRepository.findBillingViewById(companyId))
            .thenReturn(Optional.of(viewWithTrialEnd(LocalDateTime.of(2026, 2, 5, 0, 0))));

        Clock clock = Clock.fixed(Instant.parse("2026-02-10T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        assertTrue(service.isBillingActive(companyId));
        verify(companyRepository).findBillingViewById(companyId);
    }

    @Test
    void expiredTrial_withoutSubscription_blocksPremium() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Long companyId = 1L;
        when(repo.findByCompany_Id(companyId)).thenReturn(Optional.empty());
        when(companyRepository.findBillingViewById(companyId))
            .thenReturn(Optional.of(viewWithTrialEnd(LocalDateTime.of(2026, 2, 5, 0, 0))));

        Clock clock = Clock.fixed(Instant.parse("2026-02-10T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        assertFalse(service.isBillingActive(companyId));
        assertThrows(BillingRequiredException.class, () -> service.assertBillingActiveForPremiumAction(companyId));
        verify(auditLogService).log(Mockito.any(), Mockito.eq("BillingAccess"), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(), Mockito.contains("Blocked premium action"));
        verify(companyRepository).findBillingViewById(companyId);
    }

    @Test
    void billingOverrideActive_allowsBillingWithoutSubscription() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Long companyId = 2L;
        when(companyRepository.findBillingViewById(companyId))
            .thenReturn(Optional.of(viewWithOverride(true, LocalDateTime.of(2026, 3, 20, 0, 0))));

        Clock clock = Clock.fixed(Instant.parse("2026-03-13T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        assertTrue(service.isBillingActive(companyId));
        service.assertBillingActiveForPremiumAction(companyId);
        verify(repo, never()).findByCompany_Id(companyId);
    }

    @Test
    void billingOverrideExpired_fallsBackToSubscriptionAndBlocksWhenMissing() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Long companyId = 3L;
        when(companyRepository.findBillingViewById(companyId))
            .thenReturn(Optional.of(viewWithOverride(true, LocalDateTime.of(2026, 3, 10, 0, 0))));
        when(repo.findByCompany_Id(companyId)).thenReturn(Optional.empty());

        Clock clock = Clock.fixed(Instant.parse("2026-03-13T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        assertFalse(service.isBillingActive(companyId));
        assertThrows(BillingRequiredException.class, () -> service.assertBillingActiveForPremiumAction(companyId));
        verify(repo, times(2)).findByCompany_Id(companyId);
    }

    @Test
    void billingEnforcementDisabled_allowsPremiumWithoutAnyChecks() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-13T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, false);

        assertTrue(service.isBillingActive(5L));
        assertTrue(service.isTrialActive(5L));
        assertDoesNotThrow(() -> service.assertBillingActiveForPremiumAction(5L));
        verifyNoMoreInteractions(repo, companyRepository, auditLogService);
    }

    @Test
    void nullCompanyId_isTreatedAsAllowedForBackgroundPaths() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-13T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        assertTrue(service.isBillingActive(null));
        assertFalse(service.isTrialActive(null));
        assertDoesNotThrow(() -> service.assertBillingActiveForPremiumAction(null));
        verifyNoMoreInteractions(repo, companyRepository, auditLogService);
    }

    @Test
    void invalidateCompanyCacheForcesFreshBillingViewRead() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Long companyId = 9L;
        when(repo.findByCompany_Id(companyId)).thenReturn(Optional.empty());
        AtomicInteger reads = new AtomicInteger();
        when(companyRepository.findBillingViewById(companyId))
            .thenAnswer(invocation -> {
                if (reads.getAndIncrement() == 0) {
                    return Optional.of(viewWithTrialEnd(LocalDateTime.of(2026, 3, 20, 0, 0)));
                }
                return Optional.of(viewWithTrialEnd(LocalDateTime.of(2026, 3, 1, 0, 0)));
            });

        Clock clock = Clock.fixed(Instant.parse("2026-03-13T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        assertTrue(service.isBillingActive(companyId));
        service.invalidateCompanyCache(companyId);
        assertFalse(service.isBillingActive(companyId));
        verify(companyRepository, times(2)).findBillingViewById(companyId);
    }

    @Test
    void billingViewIsCachedWithinTtl() {
        BillingSubscriptionRepository repo = Mockito.mock(BillingSubscriptionRepository.class);
        CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Long companyId = 10L;
        when(companyRepository.findBillingViewById(companyId))
            .thenReturn(Optional.of(viewWithTrialEnd(LocalDateTime.of(2026, 3, 20, 0, 0))));

        Clock clock = Clock.fixed(Instant.parse("2026-03-13T10:00:00Z"), ZoneOffset.UTC);
        BillingAccessService service = new BillingAccessService(repo, companyRepository, auditLogService, clock, true);

        assertTrue(service.isBillingActive(companyId));
        assertTrue(service.isTrialActive(companyId));
        verify(companyRepository, times(1)).findBillingViewById(companyId);
        verifyNoMoreInteractions(repo);
    }

    private CompanyBillingView viewWithTrialEnd(LocalDateTime trialEnd) {
        return new CompanyBillingView() {
            @Override
            public LocalDateTime getTrialEnd() {
                return trialEnd;
            }

            @Override
            public Boolean getBillingOverrideActive() {
                return false;
            }

            @Override
            public LocalDateTime getBillingOverrideUntil() {
                return null;
            }
        };
    }

    private CompanyBillingView viewWithOverride(boolean overrideActive, LocalDateTime until) {
        return new CompanyBillingView() {
            @Override
            public LocalDateTime getTrialEnd() {
                return null;
            }

            @Override
            public Boolean getBillingOverrideActive() {
                return overrideActive;
            }

            @Override
            public LocalDateTime getBillingOverrideUntil() {
                return until;
            }
        };
    }
}
