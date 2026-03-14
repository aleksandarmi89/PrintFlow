package com.printflow.controller;

import com.printflow.entity.User;
import com.printflow.service.CurrentContextService;
import com.printflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class HomeController extends BaseController {

    private final CurrentContextService currentContextService;
    private final UserService userService;
    
    @GetMapping("/")
    public String home(Model model) {
        return "redirect:/public/";
    }
    
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }
    
    @GetMapping("/register")
    public String registerPage(Model model) {
        return "auth/register";
    }
    
    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }
    
    @GetMapping("/profile")
    public String profile(Model model) {
        User user = currentContextService.currentUser();
        model.addAttribute("user", user);
        return "auth/profile";
    }
    
    @GetMapping("/settings")
    public String settings(Model model) {
        User user = currentContextService.currentUser();
        model.addAttribute("user", user);
        return "auth/settings";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam(required = false) String firstName,
                                @RequestParam(required = false) String lastName,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String department,
                                @RequestParam(required = false) String position,
                                @RequestParam(required = false) String notes,
                                Model model) {
        User user = currentContextService.currentUser();
        userService.updateProfile(
            user.getId(),
            normalizeOptional(firstName),
            normalizeOptional(lastName),
            normalizeOptional(email),
            normalizeOptional(phone),
            normalizeOptional(department),
            normalizeOptional(position),
            normalizeOptional(notes)
        );
        return redirectWithSuccess("/profile", "profile.updated", model);
    }

    @PostMapping("/settings/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model) {
        String normalizedCurrentPassword = normalizeOptional(currentPassword);
        String normalizedNewPassword = normalizeOptional(newPassword);
        String normalizedConfirmPassword = normalizeOptional(confirmPassword);
        if (normalizedNewPassword == null || !Objects.equals(normalizedNewPassword, normalizedConfirmPassword)) {
            return redirectWithError("/settings", "auth.password_mismatch", model);
        }
        User user = currentContextService.currentUser();
        boolean changed = userService.changePassword(user.getId(), normalizedCurrentPassword, normalizedNewPassword);
        if (!changed) {
            return redirectWithError("/settings", "auth.password_current_incorrect", model);
        }
        return redirectWithSuccess("/settings", "auth.password_updated", model);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
