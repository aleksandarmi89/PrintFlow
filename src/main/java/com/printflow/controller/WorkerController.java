package com.printflow.controller;

import com.printflow.dto.*;
import com.printflow.entity.User;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/worker")
public class WorkerController extends BaseController {

    private final WorkOrderService workOrderService;
    private final TaskService taskService;
    private final UserService userService;
    private final DashboardService dashboardService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public WorkerController(WorkOrderService workOrderService,
                           TaskService taskService,
                           UserService userService,
                           DashboardService dashboardService,
                           FileStorageService fileStorageService,
                           NotificationService notificationService) {
        this.workOrderService = workOrderService;
        this.taskService = taskService;
        this.userService = userService;
        this.dashboardService = dashboardService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Long currentUserId = getCurrentUserId();
        
        // Get assigned orders/tasks
        List<WorkOrderDTO> assignedOrders = workOrderService.getWorkOrdersByAssignedUser(currentUserId);
        model.addAttribute("assignedOrders", assignedOrders);
        
        // Get pending/available orders (unassigned or in queue)
        List<WorkOrderDTO> pendingOrders = workOrderService.getUnassignedWorkOrders();
        model.addAttribute("pendingOrders", pendingOrders);
        
        // Worker statistics
        WorkerDashboardStatsDTO stats = dashboardService.getWorkerDashboardStats(currentUserId);
        model.addAttribute("stats", stats);
        
        // Recent activities
        List<TaskActivityDTO> recentActivities = taskService.getRecentActivities(currentUserId, 10);
        model.addAttribute("recentActivities", recentActivities);
        
        return "worker/tasks/dashboard";
    }

    // ==================== MY TASKS ====================

    @GetMapping("/my-tasks")
    public String myTasks(Model model,
                         @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size) {
        Long currentUserId = getCurrentUserId();
        
        Pageable pageable = PageRequest.of(page, size);
        Page<TaskDTO> taskPage;
        
        if (status != null && !status.isEmpty()) {
            try {
                TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
                taskPage = taskService.getTasksByWorkerAndStatus(currentUserId, taskStatus, pageable);
            } catch (IllegalArgumentException e) {
                // Invalid status parameter
                taskPage = taskService.getTasksByWorker(currentUserId, pageable);
                model.addAttribute("errorMessage", "Invalid status: " + status);
            }
        } else {
            taskPage = taskService.getTasksByWorker(currentUserId, pageable);
        }
        
        model.addAttribute("tasks", taskPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", taskPage.getTotalPages());
        model.addAttribute("totalItems", taskPage.getTotalElements());
        model.addAttribute("status", status);
        model.addAttribute("taskStatuses", TaskStatus.values());
        
        // Task statistics
        TaskStatisticsDTO stats = taskService.getWorkerTaskStatistics(currentUserId);
        model.addAttribute("stats", stats);
        
        return "worker/tasks/list";
    }

    // ==================== TASK DETAILS ====================

    @GetMapping("/task/{id}")
    public String taskDetails(@PathVariable Long id, Model model) {
        try {
            Long currentUserId = getCurrentUserId();
            
            // Check if user has access to this task
            if (!taskService.isTaskAssignedToUser(id, currentUserId)) {
                model.addAttribute("errorMessage", "Nemate pristup ovom zadatku");
                return "redirect:/worker/my-tasks";
            }
            
            TaskDetailsDTO task = taskService.getTaskDetails(id);
            List<TaskActivityDTO> activities = taskService.getTaskActivities(id);
            
            // Get attachments from work order - POBOLJŠANA IMPLEMENTACIJA
            List<AttachmentDTO> attachments = getAttachmentsForTask(task);
            
            model.addAttribute("task", task);
            model.addAttribute("attachments", attachments);
            model.addAttribute("activities", activities);
            model.addAttribute("taskStatuses", TaskStatus.values());
            
            return "worker/tasks/details";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Greška pri učitavanju zadatka: " + e.getMessage());
            return "redirect:/worker/my-tasks";
        }
    }

    // Helper metoda za dobijanje attachment-a
    private List<AttachmentDTO> getAttachmentsForTask(TaskDetailsDTO task) {
        if (task.getWorkOrderId() != null) {
            return fileStorageService.getAttachmentsByWorkOrder(task.getWorkOrderId());
        }
        
        // Ako task nema workOrder, vrati praznu listu bez logovanja
        // Ovo može biti normalan scenario za neke tipove taskova
        return Collections.emptyList();
    }
    @PostMapping("/task/{id}/update-status")
    public String updateTaskStatus(@PathVariable Long id,
                                  @RequestParam TaskStatus status,
                                  @RequestParam(required = false) String notes,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            
            // Check if user has access to this task
            if (!taskService.isTaskAssignedToUser(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            
            taskService.updateTaskStatus(id, status, notes, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Task status updated successfully");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating task status: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    // ==================== NOTIFICATIONS ====================

    @PostMapping("/notifications/{id}/mark-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markNotificationAsRead(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long currentUserId = getCurrentUserId();
            notificationService.markAsRead(id, currentUserId);
            response.put("status", "success");
            response.put("message", "Notification marked as read");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllNotificationsAsRead() {
        Map<String, Object> response = new HashMap<>();
        try {
            Long currentUserId = getCurrentUserId();
            notificationService.markAllAsRead(currentUserId);
            response.put("status", "success");
            response.put("message", "All notifications marked as read");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== HELPERS ====================

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (principal instanceof User) {
            return ((User) principal).getId();
        } 
        else if (principal instanceof UserDetails) {
            // Ako koristite Spring Security UserDetails
            String username = ((UserDetails) principal).getUsername();
            User user = userService.findByUsername(username);
            if (user != null) {
                return user.getId();
            }
        }
        else if (principal instanceof String && !principal.equals("anonymousUser")) {
            // U nekim konfiguracijama principal je username kao String
            String username = (String) principal;
            User user = userService.findByUsername(username);
            if (user != null) {
                return user.getId();
            }
        }
        
        throw new RuntimeException("User not authenticated or principal type not recognized");
    }

    // Helper methods za redirect sa porukama
    protected String redirectWithError(String url, String message, Model model) {
        // Ovo je simplifikovano - u praksi koristite RedirectAttributes
        return "redirect:" + url + "?error=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
    }

    protected String redirectWithSuccess(String url, String message, Model model) {
        return "redirect:" + url + "?success=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ==================== DTO CLASSES ====================

    // Inner DTO classes for worker-specific forms
    public static class PasswordChangeDTO {
        private String currentPassword;
        private String newPassword;
        private String confirmPassword;

        // Getters and setters
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
        
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }
}