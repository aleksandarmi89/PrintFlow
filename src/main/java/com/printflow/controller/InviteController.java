package com.printflow.controller;

import com.printflow.entity.UserInvite;
import com.printflow.service.InviteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@Controller
public class InviteController extends BaseController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/invite/{token}")
    public String acceptInviteForm(@PathVariable String token, Model model) {
        try {
            UserInvite invite = inviteService.getValidInvite(token);
            model.addAttribute("token", token);
            model.addAttribute("email", invite.getEmail());
            model.addAttribute("role", invite.getRole().name());
            return "auth/accept-invite";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/accept-invite";
        }
    }

    @PostMapping("/invite/{token}")
    public String acceptInvite(@PathVariable String token,
                               @RequestParam String username,
                               @RequestParam(required = false) String fullName,
                               @RequestParam String password,
                               @RequestParam String confirmPassword,
                               Model model) {
        String normalizedUsername = normalizeOptional(username);
        String normalizedFullName = normalizeOptional(fullName);
        String normalizedPassword = normalizeOptional(password);
        String normalizedConfirmPassword = normalizeOptional(confirmPassword);

        if (!Objects.equals(normalizedPassword, normalizedConfirmPassword)) {
            model.addAttribute("errorMessage", "auth.password_mismatch");
            return "auth/accept-invite";
        }
        try {
            inviteService.acceptInvite(token, normalizedUsername, normalizedFullName, normalizedPassword);
            model.addAttribute("successMessage", "auth.invite.success");
            return "auth/accept-invite";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", mapInviteErrorToKey(ex.getMessage()));
            return "auth/accept-invite";
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String mapInviteErrorToKey(String message) {
        if (message == null || message.isBlank()) {
            return "auth.invite.error.generic";
        }
        return switch (message.trim()) {
            case "Invalid invitation token" -> "auth.invite.error.invalid_token";
            case "Invitation not found" -> "auth.invite.error.not_found";
            case "Invitation already used" -> "auth.invite.error.used";
            case "Invitation expired" -> "auth.invite.error.expired";
            case "Username is required" -> "auth.register.error.username_required";
            case "Password must be at least 6 characters" -> "auth.password_min";
            case "Username already exists" -> "auth.register.error.username_exists";
            case "Email already exists" -> "auth.register.error.email_exists";
            default -> "auth.invite.error.generic";
        };
    }
}
