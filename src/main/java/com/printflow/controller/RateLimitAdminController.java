package com.printflow.controller;

import com.printflow.service.RateLimitService;
import com.printflow.service.AuditLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;


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
        String normalizedIp = normalizeIpOrCidr(ip);
        if (!isValidIpOrCidr(normalizedIp)) {
            return redirectWithError("/admin/rate-limit", "admin.rate_limit.flash.invalid_ip", model);
        }
        java.time.LocalDateTime expiresAt = null;
        if (durationMinutes != null && durationMinutes > 0) {
            expiresAt = java.time.LocalDateTime.now().plusMinutes(durationMinutes);
        }
        boolean customReason = reason != null && !reason.isBlank();
        String normalizedReason = customReason ? reason.trim() : "manual";
        rateLimitService.ban(normalizedIp, normalizedReason, expiresAt);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            null, normalizedIp, "Banned IP " + normalizedIp + (customReason ? " (" + normalizedReason + ")" : ""));
        return redirectWithSuccess("/admin/rate-limit", "admin.rate_limit.flash.banned", model);
    }

    @PostMapping("/unban")
    public String unban(@RequestParam String ip, Model model) {
        String normalizedIp = normalizeIpOrCidr(ip);
        if (!isValidIpOrCidr(normalizedIp)) {
            return redirectWithError("/admin/rate-limit", "admin.rate_limit.flash.invalid_ip", model);
        }
        rateLimitService.unban(normalizedIp);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            normalizedIp, null, "Unbanned IP " + normalizedIp);
        return redirectWithSuccess("/admin/rate-limit", "admin.rate_limit.flash.unbanned", model);
    }

    @PostMapping("/whitelist")
    public String whitelist(@RequestParam String ip, Model model) {
        String normalizedIp = normalizeIpOrCidr(ip);
        if (!isValidIpOrCidr(normalizedIp)) {
            return redirectWithError("/admin/rate-limit", "admin.rate_limit.flash.invalid_ip", model);
        }
        rateLimitService.whitelist(normalizedIp);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            null, normalizedIp, "Whitelisted IP " + normalizedIp);
        return redirectWithSuccess("/admin/rate-limit", "admin.rate_limit.flash.whitelisted", model);
    }

    @PostMapping("/unwhitelist")
    public String unwhitelist(@RequestParam String ip, Model model) {
        String normalizedIp = normalizeIpOrCidr(ip);
        if (!isValidIpOrCidr(normalizedIp)) {
            return redirectWithError("/admin/rate-limit", "admin.rate_limit.flash.invalid_ip", model);
        }
        rateLimitService.unwhitelist(normalizedIp);
        auditLogService.log(com.printflow.entity.enums.AuditAction.UPDATE, "RateLimit", null,
            normalizedIp, null, "Removed IP from whitelist " + normalizedIp);
        return redirectWithSuccess("/admin/rate-limit", "admin.rate_limit.flash.unwhitelisted", model);
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String value = ip.trim();
        if (value.contains(".")) {
            if (!value.matches("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")) {
                return false;
            }
            try {
                return InetAddress.getByName(value) instanceof Inet4Address;
            } catch (Exception ex) {
                return false;
            }
        }
        if (value.contains(":")) {
            if (!value.matches("^[0-9a-fA-F:]+$")) {
                return false;
            }
            try {
                return InetAddress.getByName(value) instanceof Inet6Address;
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }

    private boolean isValidIpOrCidr(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String value = normalizeIpOrCidr(ip);
        if (isValidIp(value)) {
            return true;
        }
        if (value.contains("/")) {
            String[] parts = value.split("/");
            if (parts.length != 2) return false;
            String baseIp = parts[0].trim();
            if (!isValidIp(baseIp)) return false;
            try {
                int prefix = Integer.parseInt(parts[1].trim());
                int maxPrefix = baseIp.contains(".") ? 32 : 128;
                return prefix >= 0 && prefix <= maxPrefix;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }

    private String normalizeIpOrCidr(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s*/\\s*", "/");
    }
}
