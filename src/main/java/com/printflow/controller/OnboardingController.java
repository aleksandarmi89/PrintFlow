package com.printflow.controller;

import com.printflow.service.OnboardingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "auth/register";
        }
        try {
            onboardingService.registerCompanyAndAdmin(
                companyName, username, fullName, email, phone, password
            );
            model.addAttribute("successMessage", "Company created. You can now sign in.");
            return "auth/register";
        } catch (RuntimeException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }
    }
}
