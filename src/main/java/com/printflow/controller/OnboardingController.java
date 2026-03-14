package com.printflow.controller;

import com.printflow.service.OnboardingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Objects;

@Controller
public class OnboardingController extends BaseController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/register")
    public String registerCompany(@RequestParam String companyName,
                                  @RequestParam String username,
                                  @RequestParam String fullName,
                                  @RequestParam(required = false) String email,
                                  @RequestParam(required = false) String phone,
                                  @RequestParam String password,
                                  @RequestParam String confirmPassword,
                                  Model model) {
        String normalizedCompanyName = normalizeOptional(companyName);
        String normalizedUsername = normalizeOptional(username);
        String normalizedFullName = normalizeOptional(fullName);
        String normalizedEmail = normalizeOptional(email);
        String normalizedPhone = normalizeOptional(phone);
        String normalizedPassword = normalizeOptional(password);
        String normalizedConfirmPassword = normalizeOptional(confirmPassword);

        if (!Objects.equals(normalizedPassword, normalizedConfirmPassword)) {
            model.addAttribute("errorMessage", "auth.password_mismatch");
            return "auth/register";
        }
        try {
            onboardingService.registerCompanyAndAdmin(
                normalizedCompanyName, normalizedUsername, normalizedFullName, normalizedEmail, normalizedPhone, normalizedPassword
            );
            model.addAttribute("successMessage", "auth.register.success");
            return "auth/register";
        } catch (RuntimeException ex) {
            model.addAttribute("errorMessage", mapOnboardingErrorToKey(ex.getMessage()));
            return "auth/register";
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String mapOnboardingErrorToKey(String message) {
        if (message == null || message.isBlank()) {
            return "auth.register.error.generic";
        }
        return switch (message.trim()) {
            case "Company name is required" -> "auth.register.error.company_required";
            case "Username is required" -> "auth.register.error.username_required";
            case "Password must be at least 6 characters" -> "auth.password_min";
            case "Company name already exists" -> "auth.register.error.company_exists";
            case "Username already exists" -> "auth.register.error.username_exists";
            case "Email already exists" -> "auth.register.error.email_exists";
            default -> "auth.register.error.generic";
        };
    }
}
