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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class NotificationController {

    private static final String API_ERROR_MESSAGE = "notifications.error";

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
            Long userId = requireCurrentUserId();
            notificationService.markAsRead(id, userId);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("status", "error", "message", API_ERROR_MESSAGE));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", API_ERROR_MESSAGE));
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllNotificationsAsRead() {
        try {
            Long userId = requireCurrentUserId();
            notificationService.markAllAsRead(userId);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("status", "error", "message", API_ERROR_MESSAGE));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", API_ERROR_MESSAGE));
        }
    }

    @PostMapping("/notifications/{id}/delete")
    public String deleteNotification(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        notificationService.deleteNotification(id, userId);
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/delete-selected")
    public String deleteSelectedNotifications(@RequestParam(name = "notificationIds", required = false) List<Long> notificationIds,
                                              RedirectAttributes redirectAttributes) {
        Long userId = requireCurrentUserId();
        List<Long> sanitizedIds = notificationIds == null
            ? List.of()
            : notificationIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (sanitizedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "notifications.flash.select_one");
            return "redirect:/notifications";
        }
        notificationService.deleteMultipleNotifications(sanitizedIds, userId);
        redirectAttributes.addFlashAttribute("successMessage", "notifications.flash.deleted_selected");
        return "redirect:/notifications";
    }

    @GetMapping("/notifications")
    public String listNotifications(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) Boolean read,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(required = false) Integer size,
                                    Model model) {
        Long userId = requireCurrentUserId();
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        String normalizedType = (type != null ? type.trim() : null);
        if (normalizedType != null && normalizedType.isBlank()) {
            normalizedType = null;
        }
        var notifications = notificationService.getNotificationsWithFilters(
            userId,
            normalizedType,
            read,
            PageRequest.of(safePage, pageSize)
        );
        if (safePage >= notifications.getTotalPages() && notifications.getTotalPages() > 0) {
            int lastPage = notifications.getTotalPages() - 1;
            notifications = notificationService.getNotificationsWithFilters(
                userId,
                normalizedType,
                read,
                PageRequest.of(lastPage, pageSize)
            );
        }
        model.addAttribute("notifications", notifications);
        model.addAttribute("type", normalizedType);
        model.addAttribute("read", read);
        model.addAttribute("currentPage", notifications.getNumber());
        model.addAttribute("totalPages", notifications.getTotalPages());
        model.addAttribute("displayTotalPages", Math.max(1, notifications.getTotalPages()));
        model.addAttribute("lastPage", Math.max(0, notifications.getTotalPages() - 1));
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        return "notifications/list";
    }

    private Long requireCurrentUserId() {
        var currentUser = tenantContextService.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not resolved");
        }
        return currentUser.getId();
    }
}
