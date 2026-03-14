package com.printflow.controller;

import com.printflow.service.AuditLogService;
import com.printflow.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RateLimitAdminControllerTest {

    @Test
    void banRejectsInvalidIpv4AndDoesNotCallService() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.ban("999.1.1.1", null, null, model);

        assertEquals("redirect:/admin/rate-limit", view);
        verifyNoInteractions(rateLimitService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void banAcceptsCompressedIpv6() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.ban("2001:db8::1", "manual-ban", 30, model);

        assertEquals("redirect:/admin/rate-limit", view);
        verify(rateLimitService).ban(eq("2001:db8::1"), eq("manual-ban"), any());
        verify(auditLogService).log(any(), eq("RateLimit"), eq(null), eq(null), eq("2001:db8::1"), anyString());
    }

    @Test
    void whitelistRejectsInvalidIpv4CidrPrefix() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.whitelist("10.0.0.0/64", model);

        assertEquals("redirect:/admin/rate-limit", view);
        verifyNoInteractions(rateLimitService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void whitelistAcceptsValidIpv4CidrPrefix() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.whitelist("10.0.0.0/24", model);

        assertEquals("redirect:/admin/rate-limit", view);
        verify(rateLimitService).whitelist("10.0.0.0/24");
        verify(auditLogService).log(any(), eq("RateLimit"), eq(null), eq(null), eq("10.0.0.0/24"), anyString());
    }

    @Test
    void banNormalizesIpAndReasonBeforeSaving() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.ban(" 10.0.0.1 / 24 ", "  burst  ", 15, model);

        assertEquals("redirect:/admin/rate-limit", view);
        verify(rateLimitService).ban(eq("10.0.0.1/24"), eq("burst"), any());
    }

    @Test
    void unbanNormalizesIpBeforeSaving() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.unban(" 192.168.0.10 ", model);

        assertEquals("redirect:/admin/rate-limit", view);
        verify(rateLimitService).unban("192.168.0.10");
    }

    @Test
    void unbanRejectsInvalidIpv6CidrPrefix() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.unban("2001:db8::/129", model);

        assertEquals("redirect:/admin/rate-limit", view);
        verifyNoInteractions(rateLimitService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void banUsesManualReasonWhenRequestReasonIsBlank() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.ban("203.0.113.10", "   ", 5, model);

        assertEquals("redirect:/admin/rate-limit", view);
        verify(rateLimitService).ban(eq("203.0.113.10"), eq("manual"), any());
        verify(auditLogService).log(any(), eq("RateLimit"), eq(null), eq(null), eq("203.0.113.10"), eq("Banned IP 203.0.113.10"));
    }

    @Test
    void banUsesNullExpiryWhenDurationIsNotPositive() {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RateLimitAdminController controller = new RateLimitAdminController(rateLimitService, auditLogService);
        Model model = new ExtendedModelMap();

        String view = controller.ban("203.0.113.11", "test", 0, model);

        assertEquals("redirect:/admin/rate-limit", view);
        verify(rateLimitService).ban(eq("203.0.113.11"), eq("test"), isNull());
    }
}
