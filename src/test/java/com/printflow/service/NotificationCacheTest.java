package com.printflow.service;

import com.printflow.config.CacheConfig;
import com.printflow.repository.NotificationRepository;
import com.printflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {CacheConfig.class, NotificationService.class})
@TestPropertySource(properties = {
    "app.cache.notification-count-ttl-seconds=20",
    "app.cache.notification-count-max-size=1000",
    "app.notification.email.enabled=false",
    "app.notification.sms.enabled=false"
})
class NotificationCacheTest {

    @Autowired private NotificationService notificationService;
    @Autowired private CacheManager cacheManager;

    @MockitoBean private NotificationRepository notificationRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private TenantGuard tenantGuard;
    @MockitoBean private EmailService emailService;
    @MockitoBean private EmailTemplateService emailTemplateService;
    @MockitoBean private CompanyBrandingService companyBrandingService;
    @MockitoBean private ApplicationEventPublisher applicationEventPublisher;

    @BeforeEach
    void setUp() {
        var cache = cacheManager.getCache("notificationCounts");
        if (cache != null) {
            cache.clear();
        }
        when(tenantGuard.requireCompanyId()).thenReturn(1L);
    }

    @Test
    void unreadCountIsLoadedOncePerTtlWindow() {
        when(notificationRepository.countByUserIdAndCompanyIdAndReadFalse(1L, 1L)).thenReturn(5);

        int first = notificationService.getUnreadNotificationCount(1L);
        int second = notificationService.getUnreadNotificationCount(1L);

        org.junit.jupiter.api.Assertions.assertEquals(5, first);
        org.junit.jupiter.api.Assertions.assertEquals(5, second);
        verify(notificationRepository, times(1))
            .countByUserIdAndCompanyIdAndReadFalse(1L, 1L);
    }
}
