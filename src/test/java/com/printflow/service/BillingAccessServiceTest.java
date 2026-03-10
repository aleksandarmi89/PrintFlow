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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
}
