package com.printflow.controller;

import com.printflow.service.CompanyBrandingService;
import com.printflow.service.PasswordResetService;
import com.printflow.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetControllerTest {

    @Test
    void requestResetTrimsIdentifierBeforeServiceCall() {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        CompanyBrandingService companyBrandingService = mock(CompanyBrandingService.class);
        com.printflow.repository.PasswordResetTokenRepository tokenRepository =
            mock(com.printflow.repository.PasswordResetTokenRepository.class);
        PasswordResetController controller = new PasswordResetController(
            passwordResetService, rateLimitService, companyBrandingService, tokenRepository,
            false, 5, 60
        );

        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.requestReset("  user@example.com  ", request, redirect);

        assertEquals("redirect:/forgot-password", view);
        verify(passwordResetService).requestReset("user@example.com");
    }

    @Test
    void resetPasswordFormTrimsTokenBeforeValidation() {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        CompanyBrandingService companyBrandingService = mock(CompanyBrandingService.class);
        com.printflow.repository.PasswordResetTokenRepository tokenRepository =
            mock(com.printflow.repository.PasswordResetTokenRepository.class);
        PasswordResetController controller = new PasswordResetController(
            passwordResetService, rateLimitService, companyBrandingService, tokenRepository,
            false, 5, 60
        );

        when(passwordResetService.validateToken("token123")).thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        String view = controller.resetPasswordForm("  token123  ", model);

        assertEquals("auth/reset-password", view);
        assertEquals("token123", model.getAttribute("token"));
        assertEquals(false, model.getAttribute("tokenValid"));
        verify(passwordResetService).validateToken("token123");
    }

    @Test
    void doResetEncodesTokenInRedirectWhenInvalidPassword() {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        CompanyBrandingService companyBrandingService = mock(CompanyBrandingService.class);
        com.printflow.repository.PasswordResetTokenRepository tokenRepository =
            mock(com.printflow.repository.PasswordResetTokenRepository.class);
        PasswordResetController controller = new PasswordResetController(
            passwordResetService, rateLimitService, companyBrandingService, tokenRepository,
            false, 5, 60
        );

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.doReset("token with space", "short", "short", redirect);

        assertEquals("redirect:/reset-password?token=token+with+space", view);
        verify(passwordResetService, never()).resetPassword(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
