package com.printflow.config;

import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.entity.User;
import com.printflow.repository.MailSettingsRepository;
import com.printflow.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        UserRepository userRepository = mock(UserRepository.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService,
            userRepository
        );

        Company company = new Company();
        company.setId(10L);
        MailSettings settings = new MailSettings();
        settings.setCompany(company);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass", Collections.emptyList())
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(1L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(10L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(1L)).thenReturn(0);
        when(notificationService.getRecentNotifications(1L, 5)).thenReturn(Collections.emptyList());
        when(userRepository.findFirstActiveSuperAdmin()).thenReturn(Optional.empty());
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
        UserRepository userRepository = mock(UserRepository.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService,
            userRepository
        );

        Company company = new Company();
        company.setId(11L);
        company.setSmtpHost("smtp.example.com");
        company.setSmtpPort(587);
        company.setSmtpUser("noreply@example.com");
        company.setSmtpPassword("legacy-pass");

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass", Collections.emptyList())
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(2L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(11L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(2L)).thenReturn(0);
        when(notificationService.getRecentNotifications(2L, 5)).thenReturn(Collections.emptyList());
        when(userRepository.findFirstActiveSuperAdmin()).thenReturn(Optional.empty());
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
        UserRepository userRepository = mock(UserRepository.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService,
            userRepository
        );

        Company company = new Company();
        company.setId(12L);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass", Collections.emptyList())
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(3L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(12L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(3L)).thenReturn(0);
        when(notificationService.getRecentNotifications(3L, 5)).thenReturn(Collections.emptyList());
        when(userRepository.findFirstActiveSuperAdmin()).thenReturn(Optional.empty());
        when(mailSettingsRepository.findByCompany_Id(12L)).thenReturn(Optional.empty());
        when(mailSettingsService.isConfiguredWithLegacyFallback(company, null)).thenReturn(false);
        when(billingAccessService.isBillingActive(12L)).thenReturn(true);

        Model model = new ExtendedModelMap();
        attributes.addNotificationAttributes(model, new MockHttpServletRequest());

        assertEquals(false, model.getAttribute("smtpConfigured"));
    }

    @Test
    void detachedCompanyProxyLikeFailureDoesNotBreakGlobalModelPopulation() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        NotificationService notificationService = mock(NotificationService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        MailSettingsRepository mailSettingsRepository = mock(MailSettingsRepository.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService,
            userRepository
        );

        Company brokenCompany = mock(Company.class);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass", Collections.emptyList())
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(4L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(13L);
        when(tenantContextService.getCurrentCompany()).thenReturn(brokenCompany);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(4L)).thenReturn(0);
        when(notificationService.getRecentNotifications(4L, 5)).thenReturn(Collections.emptyList());
        when(userRepository.findFirstActiveSuperAdmin()).thenReturn(Optional.empty());
        when(mailSettingsRepository.findByCompany_Id(13L)).thenReturn(Optional.empty());
        when(mailSettingsService.isConfiguredWithLegacyFallback(brokenCompany, null)).thenReturn(false);
        when(billingAccessService.isBillingActive(13L)).thenReturn(true);
        when(brokenCompany.getName()).thenThrow(new RuntimeException("detached proxy"));
        when(brokenCompany.getAddress()).thenThrow(new RuntimeException("detached proxy"));

        Model model = new ExtendedModelMap();
        assertDoesNotThrow(() -> attributes.addNotificationAttributes(model, new MockHttpServletRequest()));
        assertEquals(false, model.getAttribute("smtpConfigured"));
    }

    @Test
    void smtpConfiguredFallbacksToFalseWhenMailSettingsCheckThrows() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        NotificationService notificationService = mock(NotificationService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        MailSettingsRepository mailSettingsRepository = mock(MailSettingsRepository.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService,
            userRepository
        );

        Company company = new Company();
        company.setId(14L);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass", Collections.emptyList())
        );
        when(tenantContextService.getCurrentUserId()).thenReturn(5L);
        when(tenantContextService.getCurrentCompanyId()).thenReturn(14L);
        when(tenantContextService.getCurrentCompany()).thenReturn(company);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(notificationService.getUnreadNotificationCount(5L)).thenReturn(0);
        when(notificationService.getRecentNotifications(5L, 5)).thenReturn(Collections.emptyList());
        when(userRepository.findFirstActiveSuperAdmin()).thenReturn(Optional.empty());
        when(mailSettingsRepository.findByCompany_Id(14L)).thenReturn(Optional.empty());
        when(mailSettingsService.isConfiguredWithLegacyFallback(company, null)).thenThrow(new RuntimeException("lazy proxy"));
        when(billingAccessService.isBillingActive(14L)).thenReturn(true);

        Model model = new ExtendedModelMap();
        assertDoesNotThrow(() -> attributes.addNotificationAttributes(model, new MockHttpServletRequest()));
        assertEquals(false, model.getAttribute("smtpConfigured"));
    }

    @Test
    void platformFooterDefaultsIncludeSuperAdminCompanyAddressAndWebsite() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        NotificationService notificationService = mock(NotificationService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);
        MailSettingsRepository mailSettingsRepository = mock(MailSettingsRepository.class);
        MailSettingsService mailSettingsService = mock(MailSettingsService.class);
        UserRepository userRepository = mock(UserRepository.class);

        GlobalModelAttributes attributes = new GlobalModelAttributes(
            tenantContextService,
            notificationService,
            billingAccessService,
            mailSettingsRepository,
            mailSettingsService,
            userRepository
        );

        User superAdmin = new User();
        superAdmin.setFullName("Owner Admin");
        superAdmin.setEmail("owner@printflow.test");
        superAdmin.setPhone("+38160000000");
        Company ownerCompany = new Company();
        ownerCompany.setAddress("Knez Mihailova 1, Beograd");
        ownerCompany.setWebsite("proprintflow.com");
        superAdmin.setCompany(ownerCompany);
        when(userRepository.findFirstActiveSuperAdmin()).thenReturn(Optional.of(superAdmin));

        Model model = new ExtendedModelMap();
        attributes.addNotificationAttributes(model, new MockHttpServletRequest());

        assertEquals("Owner Admin", model.getAttribute("footerCompanyName"));
        assertEquals("owner@printflow.test", model.getAttribute("footerCompanyEmail"));
        assertEquals("+38160000000", model.getAttribute("footerCompanyPhone"));
        assertEquals("Knez Mihailova 1, Beograd", model.getAttribute("footerCompanyAddress"));
        assertEquals("proprintflow.com", model.getAttribute("footerCompanyWebsite"));
    }
}
