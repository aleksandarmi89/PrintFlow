package com.printflow.controller;

import com.printflow.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final com.printflow.service.RateLimitService rateLimitService;
    private final com.printflow.service.CompanyBrandingService companyBrandingService;
    private final com.printflow.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    private final boolean forgotRateLimitEnabled;
    private final int forgotRateLimitMax;
    private final int forgotRateLimitWindowSeconds;

    public PasswordResetController(PasswordResetService passwordResetService,
                                   com.printflow.service.RateLimitService rateLimitService,
                                   com.printflow.service.CompanyBrandingService companyBrandingService,
                                   com.printflow.repository.PasswordResetTokenRepository passwordResetTokenRepository,
                                   @org.springframework.beans.factory.annotation.Value("${app.rate-limit.forgot.enabled:true}") boolean forgotRateLimitEnabled,
                                   @org.springframework.beans.factory.annotation.Value("${app.rate-limit.forgot.max-requests:5}") int forgotRateLimitMax,
                                   @org.springframework.beans.factory.annotation.Value("${app.rate-limit.forgot.window-seconds:60}") int forgotRateLimitWindowSeconds) {
        this.passwordResetService = passwordResetService;
        this.rateLimitService = rateLimitService;
        this.companyBrandingService = companyBrandingService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.forgotRateLimitEnabled = forgotRateLimitEnabled;
        this.forgotRateLimitMax = forgotRateLimitMax;
        this.forgotRateLimitWindowSeconds = forgotRateLimitWindowSeconds;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam("identifier") String identifier,
                               jakarta.servlet.http.HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String normalizedIdentifier = normalizeOptional(identifier);
        String ip = getClientIp(request);
        if (forgotRateLimitEnabled) {
            boolean allowed = rateLimitService.allow(
                "forgot-password:" + ip,
                forgotRateLimitMax,
                forgotRateLimitWindowSeconds * 1000L
            );
            if (!allowed) {
                redirectAttributes.addFlashAttribute("resetError", "auth.reset_rate_limited");
                return "redirect:/forgot-password";
            }
        }
        passwordResetService.requestReset(normalizedIdentifier);
        redirectAttributes.addFlashAttribute("resetRequested", true);
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam("token") String token, Model model) {
        String normalizedToken = normalizeOptional(token);
        boolean valid = normalizedToken != null && passwordResetService.validateToken(normalizedToken).isPresent();
        model.addAttribute("token", normalizedToken);
        model.addAttribute("tokenValid", valid);
        if (valid) {
            passwordResetTokenRepository.findCompanyIdByToken(normalizedToken)
                .flatMap(companyId -> companyBrandingService.getBrandingByCompanyId(companyId, normalizedToken, "reset"))
                .ifPresent(brand -> model.addAttribute("companyBrand", brand));
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String doReset(@RequestParam("token") String token,
                          @RequestParam("password") String password,
                          @RequestParam("confirmPassword") String confirmPassword,
                          RedirectAttributes redirectAttributes) {
        String normalizedToken = normalizeOptional(token);
        if (password == null || password.length() < 6 || !password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("resetError", "auth.reset_invalid");
            return "redirect:/reset-password?token=" + encodeTokenForRedirect(normalizedToken);
        }
        boolean success = passwordResetService.resetPassword(normalizedToken, password);
        if (!success) {
            redirectAttributes.addFlashAttribute("resetError", "auth.reset_invalid_token");
            return "redirect:/reset-password?token=" + encodeTokenForRedirect(normalizedToken);
        }
        redirectAttributes.addFlashAttribute("resetSuccess", true);
        return "redirect:/login";
    }

    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String encodeTokenForRedirect(String token) {
        if (token == null) {
            return "";
        }
        return java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
    }
}
