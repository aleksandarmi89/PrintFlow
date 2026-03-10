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
        model.addAttribute("roles", getAssignableRoles());
        model.addAttribute("inviteLink", inviteLink);
        model.addAttribute("errorMessage", error);
        return "admin/users/invite";
    }

    @PostMapping("/invite")
    public String createInvite(@RequestParam String email,
                               @RequestParam String role,
                               Model model) {
        try {
            Role inviteRole = Role.valueOf(role);
            String link = inviteService.createInvite(email, inviteRole);
            return "redirect:/admin/users/invite?inviteLink=" + urlEncode(link);
        } catch (Exception ex) {
            model.addAttribute("roles", getAssignableRoles());
            model.addAttribute("errorMessage", ex.getMessage());
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
}
