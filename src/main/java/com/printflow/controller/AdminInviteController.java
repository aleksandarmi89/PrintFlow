package com.printflow.controller;

import com.printflow.entity.User.Role;
import com.printflow.service.InviteService;
import com.printflow.service.TenantContextService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminInviteController extends BaseController {
    private static final java.util.regex.Pattern MESSAGE_KEY_PATTERN =
        java.util.regex.Pattern.compile("^[a-z0-9._-]{1,120}$");

    private final InviteService inviteService;
    private final TenantContextService tenantContextService;

    public AdminInviteController(InviteService inviteService, TenantContextService tenantContextService) {
        this.inviteService = inviteService;
        this.tenantContextService = tenantContextService;
    }

    @GetMapping("/invite")
    public String inviteForm(Model model,
                             @RequestParam(required = false) String inviteLink,
                             @RequestParam(required = false) String error) {
        String normalizedInviteLink = normalizeOptional(inviteLink);
        String normalizedError = normalizeOptional(error);
        model.addAttribute("roles", getAssignableRoles());
        model.addAttribute("inviteLink", normalizedInviteLink);
        model.addAttribute("errorMessage", isMessageKey(normalizedError) ? normalizedError : null);
        return "admin/users/invite";
    }

    @PostMapping("/invite")
    public String createInvite(@RequestParam String email,
                               @RequestParam String role,
                               Model model) {
        String normalizedEmail = normalizeOptional(email);
        try {
            Role inviteRole = parseRole(role);
            if (inviteRole == null || normalizedEmail == null) {
                model.addAttribute("roles", getAssignableRoles());
                model.addAttribute("errorMessage",
                    inviteRole == null ? "admin.users.invite.invalid_role" : "admin.users.invite.invalid_email");
                return "admin/users/invite";
            }
            String link = inviteService.createInvite(normalizedEmail, inviteRole);
            return "redirect:/admin/users/invite?inviteLink=" + urlEncode(link);
        } catch (Exception ex) {
            model.addAttribute("roles", getAssignableRoles());
            String message = normalizeOptional(ex.getMessage());
            model.addAttribute("errorMessage", isMessageKey(message) ? message : "admin.users.invite.create_failed");
            return "admin/users/invite";
        }
    }

    private List<Role> getAssignableRoles() {
        if (tenantContextService.isSuperAdmin()) {
            return List.of(Role.ADMIN, Role.MANAGER, Role.WORKER_GENERAL, Role.WORKER_DESIGN, Role.WORKER_PRINT);
        }
        return List.of(Role.ADMIN, Role.MANAGER, Role.WORKER_GENERAL, Role.WORKER_DESIGN, Role.WORKER_PRINT);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isMessageKey(String value) {
        return value != null && MESSAGE_KEY_PATTERN.matcher(value).matches();
    }
}
