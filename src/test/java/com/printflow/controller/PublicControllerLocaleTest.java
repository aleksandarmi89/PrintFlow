package com.printflow.controller;

import com.printflow.repository.ClientPortalAccessRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.PasswordResetTokenRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.service.ActivityLogService;
import com.printflow.service.AuditLogService;
import com.printflow.service.CompanyBrandingService;
import com.printflow.service.FileStorageService;
import com.printflow.service.OrderPdfService;
import com.printflow.service.RateLimitService;
import com.printflow.service.WorkOrderService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class PublicControllerLocaleTest {

    @Test
    void resolveRequestedLocaleHonorsLangParam() throws Exception {
        PublicController controller = new PublicController(
            mock(WorkOrderService.class),
            mock(FileStorageService.class),
            mock(RateLimitService.class),
            mock(WorkOrderItemRepository.class),
            mock(WorkOrderRepository.class),
            mock(CompanyRepository.class),
            mock(ClientPortalAccessRepository.class),
            mock(PasswordResetTokenRepository.class),
            mock(ActivityLogService.class),
            mock(CompanyBrandingService.class),
            mock(AuditLogService.class),
            mock(OrderPdfService.class),
            true, 5, 60,
            true, 10, 60,
            true, 30, 60,
            true, 60, 60,
            true, 20, 60,
            ".pdf,.jpg,.png",
            10_485_760L,
            10,
            52_428_800L,
            20_000
        );

        Method resolveRequestedLocale = PublicController.class.getDeclaredMethod(
            "resolveRequestedLocale",
            String.class,
            Locale.class
        );
        resolveRequestedLocale.setAccessible(true);

        assertEquals("sr", ((Locale) resolveRequestedLocale.invoke(controller, "sr", Locale.ENGLISH)).getLanguage());
        assertEquals("en", ((Locale) resolveRequestedLocale.invoke(controller, "en", new Locale("sr"))).getLanguage());
        assertEquals("en", ((Locale) resolveRequestedLocale.invoke(controller, "en-US", new Locale("sr"))).getLanguage());
        assertEquals("sr", ((Locale) resolveRequestedLocale.invoke(controller, "sr_RS", Locale.ENGLISH)).getLanguage());
        assertEquals("en", ((Locale) resolveRequestedLocale.invoke(controller, "xx", Locale.ENGLISH)).getLanguage());
        assertEquals("sr", ((Locale) resolveRequestedLocale.invoke(controller, "xx", new Locale("sr"))).getLanguage());
        assertEquals("en", ((Locale) resolveRequestedLocale.invoke(controller, null, null)).getLanguage());
    }

    @Test
    void normalizePublicTokenStripsWhitespaceInsideToken() throws Exception {
        PublicController controller = new PublicController(
            mock(WorkOrderService.class),
            mock(FileStorageService.class),
            mock(RateLimitService.class),
            mock(WorkOrderItemRepository.class),
            mock(WorkOrderRepository.class),
            mock(CompanyRepository.class),
            mock(ClientPortalAccessRepository.class),
            mock(PasswordResetTokenRepository.class),
            mock(ActivityLogService.class),
            mock(CompanyBrandingService.class),
            mock(AuditLogService.class),
            mock(OrderPdfService.class),
            true, 5, 60,
            true, 10, 60,
            true, 30, 60,
            true, 60, 60,
            true, 20, 60,
            ".pdf,.jpg,.png",
            10_485_760L,
            10,
            52_428_800L,
            20_000
        );

        Method normalizePublicToken = PublicController.class.getDeclaredMethod("normalizePublicToken", String.class);
        normalizePublicToken.setAccessible(true);

        assertEquals("abc123", normalizePublicToken.invoke(controller, "  abc 123  "));
        assertEquals(null, normalizePublicToken.invoke(controller, "   "));
    }

    @Test
    void resolvePublicLangPicksFirstValidAliasInOrder() throws Exception {
        PublicController controller = new PublicController(
            mock(WorkOrderService.class),
            mock(FileStorageService.class),
            mock(RateLimitService.class),
            mock(WorkOrderItemRepository.class),
            mock(WorkOrderRepository.class),
            mock(CompanyRepository.class),
            mock(ClientPortalAccessRepository.class),
            mock(PasswordResetTokenRepository.class),
            mock(ActivityLogService.class),
            mock(CompanyBrandingService.class),
            mock(AuditLogService.class),
            mock(OrderPdfService.class),
            true, 5, 60,
            true, 10, 60,
            true, 30, 60,
            true, 60, 60,
            true, 20, 60,
            ".pdf,.jpg,.png",
            10_485_760L,
            10,
            52_428_800L,
            20_000
        );

        Method resolvePublicLang = PublicController.class.getDeclaredMethod("resolvePublicLang", String[].class);
        resolvePublicLang.setAccessible(true);

        assertEquals("en", (String) resolvePublicLang.invoke(controller, (Object) new String[]{"de", "en-US", "sr"}));
        assertEquals("sr", (String) resolvePublicLang.invoke(controller, (Object) new String[]{null, " ", "sr_RS", "en"}));
        assertNull((String) resolvePublicLang.invoke(controller, (Object) new String[]{"de", "fr", "es"}));
    }

    @Test
    void sanitizeMessageKeyAndNormalizeDecisionBehaveAsExpected() throws Exception {
        PublicController controller = new PublicController(
            mock(WorkOrderService.class),
            mock(FileStorageService.class),
            mock(RateLimitService.class),
            mock(WorkOrderItemRepository.class),
            mock(WorkOrderRepository.class),
            mock(CompanyRepository.class),
            mock(ClientPortalAccessRepository.class),
            mock(PasswordResetTokenRepository.class),
            mock(ActivityLogService.class),
            mock(CompanyBrandingService.class),
            mock(AuditLogService.class),
            mock(OrderPdfService.class),
            true, 5, 60,
            true, 10, 60,
            true, 30, 60,
            true, 60, 60,
            true, 20, 60,
            ".pdf,.jpg,.png",
            10_485_760L,
            10,
            52_428_800L,
            20_000
        );

        Method sanitizeMessageKey = PublicController.class.getDeclaredMethod("sanitizeMessageKey", String.class, int.class);
        sanitizeMessageKey.setAccessible(true);
        Method normalizeDecision = PublicController.class.getDeclaredMethod("normalizeDecision", String.class);
        normalizeDecision.setAccessible(true);
        Method trimAndCap = PublicController.class.getDeclaredMethod("trimAndCap", String.class, int.class);
        trimAndCap.setAccessible(true);

        assertEquals("public.upload.error.generic", sanitizeMessageKey.invoke(controller, "public.upload.error.generic", 120));
        assertNull(sanitizeMessageKey.invoke(controller, "public.upload.error.<script>", 120));
        assertEquals("a".repeat(120), sanitizeMessageKey.invoke(controller, "a".repeat(130), 120));

        assertEquals("true", normalizeDecision.invoke(controller, " TRUE "));
        assertEquals("false", normalizeDecision.invoke(controller, "false"));
        assertNull(normalizeDecision.invoke(controller, "yes"));

        assertEquals("hello", trimAndCap.invoke(controller, "  hello  ", 10));
        assertEquals("x".repeat(5), trimAndCap.invoke(controller, "x".repeat(8), 5));
        assertNull(trimAndCap.invoke(controller, "   ", 10));
    }

    @Test
    void redirectErrorHelpersUseSafeFallbackKeys() throws Exception {
        PublicController controller = new PublicController(
            mock(WorkOrderService.class),
            mock(FileStorageService.class),
            mock(RateLimitService.class),
            mock(WorkOrderItemRepository.class),
            mock(WorkOrderRepository.class),
            mock(CompanyRepository.class),
            mock(ClientPortalAccessRepository.class),
            mock(PasswordResetTokenRepository.class),
            mock(ActivityLogService.class),
            mock(CompanyBrandingService.class),
            mock(AuditLogService.class),
            mock(OrderPdfService.class),
            true, 5, 60,
            true, 10, 60,
            true, 30, 60,
            true, 60, 60,
            true, 20, 60,
            ".pdf,.jpg,.png",
            10_485_760L,
            10,
            52_428_800L,
            20_000
        );

        Method redirectWithUploadErrorKey = PublicController.class.getDeclaredMethod(
            "redirectWithUploadErrorKey", String.class, String.class, String.class);
        redirectWithUploadErrorKey.setAccessible(true);
        Method redirectWithApproveErrorKey = PublicController.class.getDeclaredMethod(
            "redirectWithApproveErrorKey", String.class, String.class, String.class, String.class, String.class);
        redirectWithApproveErrorKey.setAccessible(true);

        String uploadRedirect = (String) redirectWithUploadErrorKey.invoke(
            controller, "token123", "invalid<script>", "EN");
        assertEquals("redirect:/public/order/token123?uploadErrorKey=public.upload.error.generic&lang=en", uploadRedirect);

        String approveRedirect = (String) redirectWithApproveErrorKey.invoke(
            controller, "token123", "invalid<script>", "en-US", " TRUE ", " ok ");
        assertEquals(
            "redirect:/public/order/token123?approveErrorKey=order_tracking.design_error&lang=en&approveDraftDecision=true&approveDraftComment=ok#design-approval-section",
            approveRedirect
        );

        String uploadNotFound = (String) redirectWithUploadErrorKey.invoke(
            controller, "   ", "public.upload.error.generic", "en");
        assertEquals("public/order-not-found", uploadNotFound);

        String approveNotFound = (String) redirectWithApproveErrorKey.invoke(
            controller, null, "order_tracking.design_error", "sr", "false", "comment");
        assertEquals("public/order-not-found", approveNotFound);
    }
}
