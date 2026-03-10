package com.printflow.controller;

import com.printflow.entity.UserInvite;
import com.printflow.service.InviteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "auth/accept-invite";
        }
        try {
            inviteService.acceptInvite(token, username, fullName, password);
            model.addAttribute("successMessage", "Account created. You can now sign in.");
            return "auth/accept-invite";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/accept-invite";
        }
    }
}
