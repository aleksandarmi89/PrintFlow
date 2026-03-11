package com.printflow.controller;

import com.printflow.service.RateLimitService;
import com.printflow.service.AuditLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequestMapping("/admin/rate-limit")
public class RateLimitAdminController extends BaseController {

    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;

    public RateLimitAdminController(RateLimitService rateLimitService, AuditLogService auditLogService) {
        this.rateLimitService = rateLimitService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String list(Model model) {
        var bannedEntities = rateLimitService.getActiveBannedIps();
        var whitelistedEntities = rateLimitService.getActiveWhitelistedIps();
        model.addAttribute("bannedEntities", bannedEntities);
        model.addAttribute("whitelistedEntities", whitelistedEntities);
        model.addAttribute("totalViolations", rateLimitService.getTotalViolations());
        model.addAttribute("topViolations", rateLimitService.getTopViolations(10));
        return "admin/rate-limit/ban-list";
    }

    @PostMapping("/ban")
    public String ban(@RequestParam String ip,
                      @RequestParam(required = false) String reason,
                      @RequestParam(required = false) Integer durationMinutes,
                      Model model) {
        if (!isValidIpOrCidr(ip)) {
            return redirectWithError("/admin/rate-limit", "Invalid IP address", model);
        }
        java.time.LocalDateTime expiresAt = null;
        if (durationMinutes != null && durationMinutes > 0) {
            expiresAt = java.time.LocalDateTime.now().plusMinutes(durationMinutes);
        }
        rateLimitService.ban(ip, reason != null && !reason.isBlank() ? reason : "manual", expiresAt);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            null, ip, "Banned IP " + ip + (reason != null ? " (" + reason + ")" : ""));
        return redirectWithSuccess("/admin/rate-limit", "IP banned", model);
    }

    @PostMapping("/unban")
    public String unban(@RequestParam String ip, Model model) {
        if (!isValidIpOrCidr(ip)) {
            return redirectWithError("/admin/rate-limit", "Invalid IP address", model);
        }
        rateLimitService.unban(ip);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            ip, null, "Unbanned IP " + ip);
        return redirectWithSuccess("/admin/rate-limit", "IP unbanned", model);
    }

    @PostMapping("/whitelist")
    public String whitelist(@RequestParam String ip, Model model) {
        if (!isValidIpOrCidr(ip)) {
            return redirectWithError("/admin/rate-limit", "Invalid IP address", model);
        }
        rateLimitService.whitelist(ip);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            null, ip, "Whitelisted IP " + ip);
        return redirectWithSuccess("/admin/rate-limit", "IP whitelisted", model);
    }

    @PostMapping("/unwhitelist")
    public String unwhitelist(@RequestParam String ip, Model model) {
        if (!isValidIpOrCidr(ip)) {
            return redirectWithError("/admin/rate-limit", "Invalid IP address", model);
        }
        rateLimitService.unwhitelist(ip);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            ip, null, "Removed IP from whitelist " + ip);
        return redirectWithSuccess("/admin/rate-limit", "IP removed from whitelist", model);
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        // IPv4
        if (ip.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$")) {
            return true;
        }
        // IPv6
        return ip.matches("^([0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}$");
    }

    private boolean isValidIpOrCidr(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (isValidIp(ip)) {
            return true;
        }
        if (ip.contains("/")) {
            String[] parts = ip.split("/");
            if (parts.length != 2) return false;
            if (!isValidIp(parts[0])) return false;
            try {
                int prefix = Integer.parseInt(parts[1]);
                return prefix >= 0 && prefix <= 128;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }
}
