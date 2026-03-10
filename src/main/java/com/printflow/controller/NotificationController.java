package com.printflow.controller;

import com.printflow.service.NotificationService;
import com.printflow.service.TenantContextService;
import com.printflow.config.PaginationConfig;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class NotificationController {

    private final NotificationService notificationService;
    private final TenantContextService tenantContextService;
    private final PaginationConfig paginationConfig;

    public NotificationController(NotificationService notificationService,
                                  TenantContextService tenantContextService,
                                  PaginationConfig paginationConfig) {
        this.notificationService = notificationService;
        this.tenantContextService = tenantContextService;
        this.paginationConfig = paginationConfig;
    }

    @PostMapping("/notifications/{id}/mark-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markNotificationAsRead(@PathVariable Long id) {
        try {
            Long userId = tenantContextService.getCurrentUser().getId();
            notificationService.markAsRead(id, userId);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllNotificationsAsRead() {
        try {
            Long userId = tenantContextService.getCurrentUser().getId();
            notificationService.markAllAsRead(userId);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/notifications/{id}/delete")
    public String deleteNotification(@PathVariable Long id) {
        Long userId = tenantContextService.getCurrentUser().getId();
        notificationService.deleteNotification(id, userId);
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/delete-selected")
    public String deleteSelectedNotifications(@RequestParam(name = "notificationIds", required = false) List<Long> notificationIds,
                                              RedirectAttributes redirectAttributes) {
        Long userId = tenantContextService.getCurrentUser().getId();
        if (notificationIds == null || notificationIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Select at least one notification.");
            return "redirect:/notifications";
        }
        notificationService.deleteMultipleNotifications(notificationIds, userId);
        redirectAttributes.addFlashAttribute("successMessage", "Selected notifications deleted.");
        return "redirect:/notifications";
    }

    @GetMapping("/notifications")
    public String listNotifications(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) Boolean read,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(required = false) Integer size,
                                    Model model) {
        Long userId = tenantContextService.getCurrentUser().getId();
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        var notifications = notificationService.getNotificationsWithFilters(
            userId,
            type,
            read,
            PageRequest.of(safePage, pageSize)
        );
        model.addAttribute("notifications", notifications);
        model.addAttribute("type", type);
        model.addAttribute("read", read);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", notifications.getTotalPages());
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        return "notifications/list";
    }
}
