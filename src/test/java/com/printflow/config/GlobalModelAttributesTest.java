package com.printflow.config;

import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.repository.MailSettingsRepository;
import com.printflow.service.BillingAccessService;
import com.printflow.service.MailSettingsService;
import com.printflow.service.NotificationService;
import com.printflow.service.TenantContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalModelAttributesTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void smtpConfiguredIsTrueWhenMailSettingsConfigured() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        NotificationService notificationService = mock(NotificationService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        MailSettingsRepository mailSettingsRepository = mock(MailSettingsRepository.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService
        );

        Company company = new Company();
        company.setId(10L);
        MailSettings settings = new MailSettings();
        settings.setCompany(company);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass")
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(1L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(10L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(1L)).thenReturn(0);
        when(notificationService.getRecentNotifications(1L, 5)).thenReturn(Collections.emptyList());
        when(mailSettingsRepository.findByCompany_Id(10L)).thenReturn(Optional.of(settings));
        when(mailSettingsService.isConfiguredWithLegacyFallback(company, settings)).thenReturn(true);
        when(billingAccessService.isBillingActive(10L)).thenReturn(true);

        Model model = new ExtendedModelMap();
        attributes.addNotificationAttributes(model, new MockHttpServletRequest());

        assertEquals(true, model.getAttribute("smtpConfigured"));
    }

    @Test
    void smtpConfiguredFallsBackToLegacyCompanySmtpFields() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        NotificationService notificationService = mock(NotificationService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        MailSettingsRepository mailSettingsRepository = mock(MailSettingsRepository.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService
        );

        Company company = new Company();
        company.setId(11L);
        company.setSmtpHost("smtp.example.com");
        company.setSmtpPort(587);
        company.setSmtpUser("noreply@example.com");
        company.setSmtpPassword("legacy-pass");

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass")
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(2L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(11L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(2L)).thenReturn(0);
        when(notificationService.getRecentNotifications(2L, 5)).thenReturn(Collections.emptyList());
        when(mailSettingsRepository.findByCompany_Id(11L)).thenReturn(Optional.empty());
        when(mailSettingsService.isConfiguredWithLegacyFallback(company, null)).thenReturn(true);
        when(billingAccessService.isBillingActive(11L)).thenReturn(true);

        Model model = new ExtendedModelMap();
        attributes.addNotificationAttributes(model, new MockHttpServletRequest());

        assertEquals(true, model.getAttribute("smtpConfigured"));
    }

    @Test
    void smtpConfiguredIsFalseWhenNeitherMailSettingsNorLegacyAreConfigured() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        NotificationService notificationService = mock(NotificationService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        MailSettingsRepository mailSettingsRepository = mock(MailSettingsRepository.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService
        );

        Company company = new Company();
        company.setId(12L);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass")
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(3L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(12L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(3L)).thenReturn(0);
        when(notificationService.getRecentNotifications(3L, 5)).thenReturn(Collections.emptyList());
        when(mailSettingsRepository.findByCompany_Id(12L)).thenReturn(Optional.empty());
        when(mailSettingsService.isConfiguredWithLegacyFallback(company, null)).thenReturn(false);
        when(billingAccessService.isBillingActive(12L)).thenReturn(true);

        Model model = new ExtendedModelMap();
        attributes.addNotificationAttributes(model, new MockHttpServletRequest());

        assertEquals(false, model.getAttribute("smtpConfigured"));
    }
}
