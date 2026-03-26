package com.printflow.controller;

import com.printflow.dto.*;
import com.printflow.entity.User;
import com.printflow.entity.Comment;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.service.*;
import com.printflow.config.PaginationConfig;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.service.TenantContextService;
import com.printflow.service.AuditLogService;
import com.printflow.entity.enums.AuditAction;
import com.printflow.service.BillingRequiredException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final PaginationConfig paginationConfig;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final TenantContextService tenantContextService;
    private final AuditLogService auditLogService;

    public WorkerController(WorkOrderService workOrderService,
                           TaskService taskService,
                           UserService userService,
                           DashboardService dashboardService,
                           FileStorageService fileStorageService,
                           NotificationService notificationService,
                           PaginationConfig paginationConfig,
                           WorkOrderItemRepository workOrderItemRepository,
                           TenantContextService tenantContextService,
                           AuditLogService auditLogService) {
        this.workOrderService = workOrderService;
        this.taskService = taskService;
        this.userService = userService;
        this.dashboardService = dashboardService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.paginationConfig = paginationConfig;
        this.workOrderItemRepository = workOrderItemRepository;
        this.tenantContextService = tenantContextService;
        this.auditLogService = auditLogService;
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
                         @RequestParam(required = false) String filter,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) String sort,
                         @RequestParam(required = false) String dir,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) Integer size) {
        Long currentUserId = getCurrentUserId();

        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage, pageSize);
        Page<TaskDTO> taskPage;

        String normalizedStatus = status != null ? status.trim() : null;
        TaskStatus taskStatus = parseTaskStatus(normalizedStatus);
        if (normalizedStatus != null && !normalizedStatus.isBlank() && taskStatus == null) {
            model.addAttribute("errorMessage", "worker.tasks.error.invalid_status");
            normalizedStatus = null;
        } else if (taskStatus != null) {
            normalizedStatus = taskStatus.name();
        }
        taskPage = taskService.getTasksByWorkerFiltered(currentUserId, taskStatus, filter, q, sort, dir, pageable);

        int totalPages = taskPage.getTotalPages();
        if (totalPages > 0 && safePage >= totalPages) {
            return "redirect:" + buildMyTasksUrl(totalPages - 1, pageSize, normalizedStatus, filter, q, sort, dir);
        }
        
        model.addAttribute("tasks", taskPage.getContent());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", taskPage.getTotalElements());
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        model.addAttribute("status", normalizedStatus);
        model.addAttribute("filter", filter);
        model.addAttribute("q", q);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("pageNumbers", buildPageNumbers(totalPages, safePage));
        model.addAttribute("taskStatuses", TaskStatus.values());
        
        // Task statistics
        TaskStatisticsDTO stats = taskService.getWorkerTaskStatistics(currentUserId);
        model.addAttribute("stats", stats);
        
        return "worker/tasks/list";
    }

    // ==================== TASK DETAILS ====================

    @GetMapping("/task/{id}")
    public String taskDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            
            // Check if user has access to this task
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Nemate pristup ovom zadatku");
                return "redirect:/worker/my-tasks";
            }
            
            TaskDetailsDTO task = taskService.getTaskDetailsForWorker(id, currentUserId);
            List<TaskActivityDTO> activities = taskService.getTaskActivities(id);
            String blockReason = activities.stream()
                .map(TaskActivityDTO::getDescription)
                .filter(d -> d != null && d.contains("Reason:"))
                .map(d -> d.substring(d.indexOf("Reason:") + "Reason:".length()).trim())
                .findFirst()
                .orElse(null);
            List<CommentDTO> comments = taskService.getTaskComments(id);
            notificationService.markUnreadByLink(currentUserId, "/worker/task/" + id);
            
            // Get attachments from work order - POBOLJŠANA IMPLEMENTACIJA
            List<AttachmentDTO> attachments = getAttachmentsForTask(task);
            List<AttachmentDTO> adminAttachments = attachments.stream()
                .filter(a -> a.getAttachmentType() == AttachmentType.INSTRUCTION || a.getAttachmentType() == AttachmentType.DESIGN_PREVIEW)
                .toList();
            List<AttachmentDTO> proofAttachments = fileStorageService.getAttachmentsByTask(id);
            
            model.addAttribute("task", task);
            model.addAttribute("attachments", attachments);
            model.addAttribute("adminAttachments", adminAttachments);
            model.addAttribute("proofAttachments", proofAttachments);
            model.addAttribute("activities", activities);
            model.addAttribute("recentActivities", activities);
            model.addAttribute("comments", comments);
            model.addAttribute("timeSummary", taskService.getTimeSummary(currentUserId, null));
            model.addAttribute("currentSession", task.getTimerStartedAt());
            model.addAttribute("currentUserId", currentUserId);
            model.addAttribute("blockReason", blockReason);
            model.addAttribute("canAdminOverride", isAdminOrManager());
            model.addAttribute("taskStatuses", TaskStatus.values());
            model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
                ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
            if (task.getWorkOrderId() != null) {
                model.addAttribute("orderItems", workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
                    task.getWorkOrderId(), tenantContextService.requireCompanyId()));
            } else {
                model.addAttribute("orderItems", Collections.emptyList());
            }
            
            return "worker/tasks/details";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Greška pri učitavanju zadatka: " + e.getMessage());
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
                                  @RequestParam(required = false) String returnTo,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            
            // Check if user has access to this task
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            
            taskService.updateTaskStatusForWorker(id, status, notes, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Task status updated successfully");
            if ("list".equalsIgnoreCase(returnTo)) {
                return "redirect:/worker/my-tasks";
            }
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating task status: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/update-progress")
    public String updateTaskProgress(@PathVariable Long id,
                                     @RequestParam int progress,
                                     @RequestParam(required = false) String notes,
                                     RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            taskService.updateTaskProgressForWorker(id, progress, currentUserId, notes);
            redirectAttributes.addFlashAttribute("successMessage", "Progress updated successfully");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating progress: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/complete")
    public String completeTaskWithProof(@PathVariable Long id,
                                        @RequestParam(required = false) MultipartFile proofFile,
                                        @RequestParam(required = false) String proofDescription,
                                        @RequestParam(required = false) String notes,
                                        RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            if (proofFile != null && !proofFile.isEmpty()) {
                fileStorageService.uploadTaskFile(proofFile, id, AttachmentType.PROOF_OF_WORK,
                    currentUserId, proofDescription);
            }
            String mergedNotes = notes;
            if (proofDescription != null && !proofDescription.trim().isEmpty()) {
                mergedNotes = (mergedNotes == null || mergedNotes.isBlank())
                    ? ("Proof: " + proofDescription)
                    : (mergedNotes + "\nProof: " + proofDescription);
            }
            taskService.updateTaskStatusForWorker(id, TaskStatus.AWAITING_REVIEW, mergedNotes, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Task sent for review and proof uploaded");
            return "redirect:/worker/task/" + id;
        } catch (BillingRequiredException e) {
            auditLogService.log(AuditAction.UPDATE, "BillingAccess", null, null, null,
                "Blocked worker action: complete-task");
            redirectAttributes.addFlashAttribute("errorMessage", "Billing is required to upload proof.");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error completing task: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/upload-proof")
    public String uploadProof(@PathVariable Long id,
                              @RequestParam MultipartFile proofFile,
                              @RequestParam(required = false) String proofDescription,
                              RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            if (proofFile == null || proofFile.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please select a file to upload");
                return "redirect:/worker/task/" + id;
            }
            fileStorageService.uploadTaskFile(proofFile, id, AttachmentType.PROOF_OF_WORK,
                currentUserId, proofDescription);
            redirectAttributes.addFlashAttribute("successMessage", "Proof uploaded successfully");
            return "redirect:/worker/task/" + id;
        } catch (BillingRequiredException e) {
            auditLogService.log(AuditAction.UPDATE, "BillingAccess", null, null, null,
                "Blocked worker action: upload-proof");
            redirectAttributes.addFlashAttribute("errorMessage", "Billing is required to upload proof.");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error uploading proof: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{taskId}/proof/{attachmentId}/delete")
    public String deleteProofAttachment(@PathVariable Long taskId,
                                        @PathVariable Long attachmentId,
                                        RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(taskId, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied");
                return "redirect:/worker/task/" + taskId;
            }
            List<AttachmentDTO> proofAttachments = fileStorageService.getAttachmentsByTask(taskId);
            AttachmentDTO attachment = proofAttachments.stream()
                .filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElse(null);
            if (attachment == null || attachment.getAttachmentType() != AttachmentType.PROOF_OF_WORK) {
                redirectAttributes.addFlashAttribute("errorMessage", "Attachment not found");
                return "redirect:/worker/task/" + taskId;
            }
            if (attachment.getUploadedById() == null || !attachment.getUploadedById().equals(currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You can delete only your own proof");
                return "redirect:/worker/task/" + taskId;
            }
            fileStorageService.deleteAttachment(attachmentId);
            redirectAttributes.addFlashAttribute("successMessage", "Proof deleted");
            return "redirect:/worker/task/" + taskId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting proof: " + e.getMessage());
            return "redirect:/worker/task/" + taskId;
        }
    }

    @PostMapping("/task/{id}/start-timer")
    public String startTimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            taskService.startTimer(id, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Timer started");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error starting timer: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/stop-timer")
    public String stopTimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            taskService.stopTimer(id, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Timer stopped");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error stopping timer: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/pause-timer")
    public String pauseTimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            taskService.pauseTimer(id, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Timer paused");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error pausing timer: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/resume-timer")
    public String resumeTimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            taskService.startTimer(id, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Timer resumed");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error resuming timer: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/log-time")
    public String logTime(@PathVariable Long id,
                          @RequestParam int hours,
                          @RequestParam int minutes,
                          @RequestParam(required = false) String description,
                          RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            int safeHours = Math.max(0, hours);
            int safeMinutes = Math.max(0, Math.min(59, minutes));
            String desc = (description == null || description.isBlank()) ? "Manual time entry" : description;
            taskService.logTime(id, safeHours, safeMinutes, desc, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Time logged");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error logging time: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{id}/comment")
    public String addComment(@PathVariable Long id,
                             @RequestParam String content,
                             @RequestParam(required = false) MultipartFile[] commentFiles,
                             RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(id, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this task");
                return "redirect:/worker/my-tasks";
            }
            if (content == null || content.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Comment cannot be empty");
                return "redirect:/worker/task/" + id;
            }
            Comment saved = taskService.addComment(id, content.trim(), currentUserId);
            if (commentFiles != null && commentFiles.length > 0 && saved != null) {
                for (MultipartFile file : commentFiles) {
                    if (file != null && !file.isEmpty()) {
                        fileStorageService.uploadCommentFile(file, saved.getTask(), saved, currentUserId);
                    }
                }
            }
            redirectAttributes.addFlashAttribute("successMessage", "Comment added");
            return "redirect:/worker/task/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding comment: " + e.getMessage());
            return "redirect:/worker/task/" + id;
        }
    }

    @PostMapping("/task/{taskId}/comment/{commentId}/delete")
    public String deleteComment(@PathVariable Long taskId,
                                @PathVariable Long commentId,
                                RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(taskId, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied");
                return "redirect:/worker/task/" + taskId;
            }
            taskService.deleteCommentForUser(taskId, commentId, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Comment deleted");
            return "redirect:/worker/task/" + taskId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting comment: " + e.getMessage());
            return "redirect:/worker/task/" + taskId;
        }
    }

    @PostMapping("/task/{taskId}/comment/{commentId}/attachment/{attachmentId}/delete")
    public String deleteCommentAttachment(@PathVariable Long taskId,
                                          @PathVariable Long commentId,
                                          @PathVariable Long attachmentId,
                                          RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(taskId, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied");
                return "redirect:/worker/task/" + taskId;
            }
            boolean allowAdminOverride = isAdminOrManager();
            fileStorageService.deleteCommentAttachment(attachmentId, commentId, currentUserId, allowAdminOverride);
            redirectAttributes.addFlashAttribute("successMessage", "Attachment deleted");
            return "redirect:/worker/task/" + taskId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting attachment: " + e.getMessage());
            return "redirect:/worker/task/" + taskId;
        }
    }

    @PostMapping("/task/{taskId}/comment/{commentId}/attachments/delete")
    public String deleteCommentAttachments(@PathVariable Long taskId,
                                           @PathVariable Long commentId,
                                           @RequestParam(required = false) List<Long> attachmentIds,
                                           RedirectAttributes redirectAttributes) {
        try {
            Long currentUserId = getCurrentUserId();
            if (!taskService.canUserAccessTask(taskId, currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied");
                return "redirect:/worker/task/" + taskId;
            }
            if (attachmentIds == null || attachmentIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No attachments selected");
                return "redirect:/worker/task/" + taskId;
            }
            boolean allowAdminOverride = isAdminOrManager();
            fileStorageService.deleteCommentAttachments(attachmentIds, commentId, currentUserId, allowAdminOverride);
            redirectAttributes.addFlashAttribute("successMessage", "Selected attachments deleted");
            return "redirect:/worker/task/" + taskId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting attachments: " + e.getMessage());
            return "redirect:/worker/task/" + taskId;
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

    private boolean isAdminOrManager() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = null;
        if (principal instanceof User) {
            user = (User) principal;
        } else if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            user = userService.findByUsername(username);
        } else if (principal instanceof String && !principal.equals("anonymousUser")) {
            user = userService.findByUsername((String) principal);
        }
        if (user == null || user.getRole() == null) {
            return false;
        }
        return user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.MANAGER;
    }

    private String buildMyTasksUrl(int page, int size, String status, String filter, String q, String sort, String dir) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/worker/my-tasks")
            .queryParam("page", page)
            .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        if (filter != null && !filter.isBlank()) {
            builder.queryParam("filter", filter);
        }
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q);
        }
        if (sort != null && !sort.isBlank()) {
            builder.queryParam("sort", sort);
        }
        if (dir != null && !dir.isBlank()) {
            builder.queryParam("dir", dir);
        }
        return builder.build().toUriString();
    }

    private TaskStatus parseTaskStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<Integer> buildPageNumbers(int totalPages, int currentPage) {
        List<Integer> pages = new ArrayList<>();
        if (totalPages <= 1) {
            return pages;
        }
        if (totalPages <= 7) {
            for (int i = 0; i < totalPages; i++) {
                pages.add(i);
            }
            return pages;
        }
        pages.add(0);
        int start = Math.max(1, currentPage - 1);
        int end = Math.min(totalPages - 2, currentPage + 1);
        if (start > 1) {
            pages.add(-1);
        }
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        if (end < totalPages - 2) {
            pages.add(-1);
        }
        pages.add(totalPages - 1);
        return pages;
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
