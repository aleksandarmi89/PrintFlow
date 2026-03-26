package com.printflow.service;

import com.printflow.dto.NotificationDTO;
import com.printflow.entity.Company;
import com.printflow.entity.Notification;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.repository.NotificationRepository;
import com.printflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaTypeFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String NOT_AVAILABLE = "N/A";
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TenantGuard tenantGuard;
    private final String baseUrl;
    private final boolean emailEnabled;
    private final boolean smsEnabled;
    private final CacheManager cacheManager;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final CompanyBrandingService companyBrandingService;
    private final ApplicationEventPublisher eventPublisher;
    private final int recentNotificationLimit;
    public NotificationService(
            NotificationRepository notificationRepository, 
            UserRepository userRepository,
            @Value("${app.base-url:http://localhost:8088}") String baseUrl,
            @Value("${app.notification.email.enabled:false}") boolean emailEnabled,
            @Value("${app.notification.sms.enabled:false}") boolean smsEnabled,
            @Value("${app.notification.recent-limit:10}") int recentNotificationLimit,
            TenantGuard tenantGuard,
            CacheManager cacheManager,
            EmailService emailService,
            EmailTemplateService emailTemplateService,
            CompanyBrandingService companyBrandingService,
            ApplicationEventPublisher eventPublisher) {
        
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.recentNotificationLimit = recentNotificationLimit;
        this.tenantGuard = tenantGuard;
        this.cacheManager = cacheManager;
        this.emailService = emailService;
        this.emailTemplateService = emailTemplateService;
        this.companyBrandingService = companyBrandingService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== CLIENT EMAIL NOTIFICATIONS ====================
    public void notifyClientOrderCreated(WorkOrder order) {
        if (order == null || order.getClient() == null) {
            return;
        }
        String subject = "Order created: " + order.getOrderNumber();
        String trackingCode = order.getPublicToken();
        String link = buildPublicOrderUrl(order);
        if (!emailEnabled) {
            log.info("Email sending is disabled. Would send to: {} - Subject: {}", order.getClient().getEmail(), subject);
            return;
        }
        String logoUrl = buildInlineCompanyLogo(order.getCompany());
        if (logoUrl == null) {
            logoUrl = buildPublicCompanyLogoUrl(order.getCompany());
        }
        String body = """
            <div style="font-family: Arial, sans-serif; color:#111;">
              %s
              <h2 style="margin:0 0 12px 0;">Your order is created</h2>
              <p>Hello %s,</p>
              <p>Your order <strong>#%s</strong> has been created.</p>
              <p><strong>Tracking code:</strong> %s</p>
              <p style="margin:16px 0;">
                <a href="%s" style="background:#2563eb;color:#fff;padding:10px 16px;border-radius:6px;text-decoration:none;">
                  Track order
                </a>
              </p>
              <p style="font-size:12px;color:#555;">If the button doesn’t work, use this link:</p>
              <p style="font-size:12px;color:#555;word-break:break-all;">%s</p>
            </div>
            """.formatted(renderLogoHtml(logoUrl),
                order.getClient().getCompanyName() != null ? order.getClient().getCompanyName() : "there",
                order.getOrderNumber(),
                trackingCode,
                link,
                link);
        sendEmailInternal(order.getClient().getEmail(), subject, body, true, order.getCompany());
    }

    public void notifyClientStatusChanged(WorkOrder order) {
        if (order == null || order.getClient() == null) {
            return;
        }
        String subject = "Order status update: " + order.getOrderNumber();
        String message = "Hello " + order.getClient().getCompanyName() + ",\n" +
            "Your order #" + order.getOrderNumber() + " is now " + order.getStatus() + ".";
        sendClientEmail(order.getClient().getEmail(), subject, message, order.getCompany());
    }

    public void notifyClientDesignApproved(WorkOrder order) {
        if (order == null || order.getClient() == null) {
            return;
        }
        String subject = "Design approved for order " + order.getOrderNumber();
        String message = "Hello " + order.getClient().getCompanyName() + ",\n" +
            "Your design has been approved. We will proceed with production.";
        sendClientEmail(order.getClient().getEmail(), subject, message, order.getCompany());
    }

    public void notifyClientOrderReady(WorkOrder order) {
        if (order == null || order.getClient() == null) {
            return;
        }
        String subject = "Order ready: " + order.getOrderNumber();
        String message = "Hello " + order.getClient().getCompanyName() + ",\n" +
            "Your order #" + order.getOrderNumber() + " is now READY FOR PICKUP.";
        sendClientEmail(order.getClient().getEmail(), subject, message, order.getCompany());
    }

    public void sendCompanySmtpTest(Company company, String toEmail) {
        if (company == null || toEmail == null || toEmail.isBlank()) {
            return;
        }
        String subject = "SMTP test - " + (company.getName() != null ? company.getName() : "PrintFlow");
        String body = """
            <div style="font-family: Arial, sans-serif; color:#111;">
              <h2>SMTP test email</h2>
              <p>This is a test message sent from your company SMTP settings.</p>
              <p><strong>Company:</strong> %s</p>
            </div>
            """.formatted(company.getName() != null ? company.getName() : "PrintFlow");
        sendEmailInternal(toEmail, subject, body, true, company);
    }

    private void sendClientEmail(String to, String subject, String body, Company company) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (!emailEnabled) {
            log.info("[CLIENT_EMAIL_DISABLED] to={} subject={}", to, subject);
            return;
        }
        log.info("[CLIENT_EMAIL] to={} subject={} body={}", to, subject, body);
        sendEmailInternal(to, subject, body, false, company);
    }
    
    // ==================== USER NOTIFICATIONS ====================
    
    public Page<NotificationDTO> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdAndCompanyIdOrderByCreatedAtDesc(userId, tenantGuard.requireCompanyId(), pageable)
            .map(this::convertToDTO);
    }
    
    public int getUnreadNotificationCount(Long userId) {
        Cache cache = cacheManager.getCache("notificationCounts");
        // User.id is a globally unique PK, so userId alone is safe for cache keys.
        String key = "u:" + userId;
        if (cache == null) {
            return notificationRepository.countByUserIdAndCompanyIdAndReadFalse(userId, tenantGuard.requireCompanyId());
        }
        Integer cached = cache.get(key, () ->
            notificationRepository.countByUserIdAndCompanyIdAndReadFalse(userId, tenantGuard.requireCompanyId())
        );
        return cached != null ? cached : 0;
    }
    
    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserCompanyId(notificationId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied to this notification");
        }
        
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
    
    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = notificationRepository
            .findByUserIdAndCompanyIdAndReadFalse(userId, tenantGuard.requireCompanyId());
        
        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        });
        
        notificationRepository.saveAll(unreadNotifications);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    public void markUnreadByLink(Long userId, String link) {
        if (link == null || link.isBlank()) {
            return;
        }
        List<Notification> notifications = notificationRepository.findByUserIdAndCompanyIdAndReadFalseAndLink(
            userId, tenantGuard.requireCompanyId(), link);
        if (notifications.isEmpty()) {
            return;
        }
        notifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        });
        notificationRepository.saveAll(notifications);
    }
    
    public void createNotification(Long userId, String title, String message, String type) {
        createNotification(userId, title, message, type, null);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    public void createNotification(Long userId, String title, String message, String type, String link) {
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        
        notificationRepository.save(notification);
    }
    
    public void createNotificationForUsers(List<Long> userIds, String title, String message, String type) {
        createNotificationForUsers(userIds, title, message, type, null);
    }

    public void createNotificationForUsers(List<Long> userIds, String title, String message, String type, String link) {
        List<User> users = userRepository.findByCompany_IdAndIdIn(tenantGuard.requireCompanyId(), userIds);
        
        List<Notification> notifications = users.stream().map(user -> {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setLink(link);
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            return notification;
        }).collect(Collectors.toList());
        
        notificationRepository.saveAll(notifications);
        eventPublisher.publishEvent(new NotificationBatchCreatedEvent(userIds));
    }
    
    // ==================== TASK NOTIFICATIONS ====================
    
    public void sendTaskAssignedNotification(Long taskId, Long assignedToId, String taskTitle) {
        String message = String.format("You have been assigned a new task: %s", taskTitle);
        User user = userRepository.findByIdAndCompany_Id(assignedToId, tenantGuard.requireCompanyId()).orElse(null);
        String link = taskLinkForUser(user, taskId);
        createNotification(assignedToId, "New Task Assigned", message, "TASK_ASSIGNED", link);
        if (user != null) {
            sendTaskEmail(user, "New Task Assigned", taskTitle, link);
        }
    }
    
    public void sendTaskStatusNotification(Long taskId, Long userId, String taskTitle, String oldStatus, String newStatus) {
        String message = String.format("Task '%s' status changed from %s to %s", 
            taskTitle, oldStatus, newStatus);
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElse(null);
        createNotification(userId, "Task Status Updated", message, "TASK_UPDATE", taskLinkForUser(user, taskId));
    }
    
    public void sendTaskCommentNotification(Long taskId, Long commenterId, Long taskOwnerId, String taskTitle) {
        if (!commenterId.equals(taskOwnerId)) {
            String message = String.format("New comment on task: %s", taskTitle);
            User user = userRepository.findByIdAndCompany_Id(taskOwnerId, tenantGuard.requireCompanyId()).orElse(null);
            createNotification(taskOwnerId, "New Comment", message, "TASK_COMMENT", taskLinkForUser(user, taskId));
        }
    }
    
    public void sendTaskTimeLoggedNotification(Long taskId, Long userId, String taskTitle, double hours) {
        String message = String.format("%.2f hours logged on task: %s", hours, taskTitle);
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElse(null);
        createNotification(userId, "Time Logged", message, "TIME_LOGGED", taskLinkForUser(user, taskId));
    }
    
    public void sendTaskCompletedNotification(Long taskId, Long userId, String taskTitle) {
        String message = String.format("Task completed: %s", taskTitle);
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElse(null);
        String link = taskLinkForUser(user, taskId);
        createNotification(userId, "Task Completed", message, "TASK_COMPLETED", link);
        if (user != null) {
            sendTaskEmail(user, "Task Completed", taskTitle, link);
        }
    }

    public void sendTaskReviewDecisionNotification(Long userId, String title, String message, Long taskId) {
        User user = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId()).orElse(null);
        createNotification(userId, title, message, "TASK_REVIEW", taskLinkForUser(user, taskId));
    }

    private String taskLinkForUser(User user, Long taskId) {
        if (user == null || user.getRole() == null) {
            return buildUrl("/worker/task/" + taskId);
        }
        return switch (user.getRole()) {
            case SUPER_ADMIN, ADMIN, MANAGER -> buildUrl("/admin/tasks/" + taskId);
            default -> buildUrl("/worker/task/" + taskId);
        };
    }

    public void sendTaskAwaitingReviewNotification(List<User> admins, Long taskId, String taskTitle) {
        if (admins == null || admins.isEmpty()) {
            return;
        }
        String message = String.format("Task '%s' is awaiting review.", taskTitle);
        List<Long> adminIds = admins.stream().map(User::getId).toList();
        String link = buildUrl("/admin/tasks/" + taskId);
        createNotificationForUsers(adminIds, "Task Awaiting Review", message, "TASK_REVIEW", link);
        admins.forEach(admin -> sendTaskEmail(admin, "Task Awaiting Review", taskTitle, link));
    }

    public void sendClientTaskApprovedNotification(WorkOrder order, String taskTitle) {
        if (order == null || order.getClient() == null) {
            return;
        }
        String subject = "Task Completed";
        String link = buildPublicOrderUrl(order);
        String companyName = order.getCompany() != null ? order.getCompany().getName() : "PrintFlow";
        String html = buildClientUpdateEmailHtml(
            order.getClient().getContactPerson(),
            subject,
            taskTitle,
            order.getOrderNumber(),
            order.getTitle(),
            order.getPrice(),
            link,
            companyName,
            "View order"
        );
        sendHtmlEmail(order.getClient().getEmail(), subject, html, order.getCompany());
        if (smsEnabled && order.getClient().getPhone() != null) {
            log.info("Would send SMS to: {} - Message: {}", order.getClient().getPhone(), subject);
        }
    }
    
    // ==================== ORDER NOTIFICATIONS ====================
    
    public void sendOrderStatusNotification(WorkOrder order, OrderStatus oldStatus, OrderStatus newStatus) {
        String message = String.format(
            "Order %s status changed from %s to %s",
            order.getOrderNumber(),
            oldStatus,
            newStatus
        );
        
        log.info("Status Notification: " + message);
        
        if (order.getAssignedTo() != null) {
            createNotification(order.getAssignedTo().getId(), 
                "Order Status Updated", message, "ORDER_UPDATE", buildUrl("/admin/orders/" + order.getId()));
        }
        
        if (emailEnabled) {
            sendEmailNotification(order, message);
        }
        
        if (smsEnabled && order.getClient() != null && order.getClient().getPhone() != null) {
            sendSmsNotification(order, message);
        }
    }
    
    public void sendDesignApprovalRequest(WorkOrder order, String approvalUrl) {
        if (order == null || order.getClient() == null) {
            return;
        }
        String subject = "Design Approval Required";
        String link = approvalUrl != null && !approvalUrl.isBlank()
            ? approvalUrl
            : buildPublicOrderUrl(order);
        String companyName = order.getCompany() != null ? order.getCompany().getName() : "PrintFlow";
        String html = buildClientUpdateEmailHtml(
            order.getClient().getContactPerson(),
            subject,
            "Design Approval",
            order.getOrderNumber(),
            order.getTitle(),
            order.getPrice(),
            link,
            companyName,
            "Review design"
        );
        log.info("Design Approval Request: {}", subject);
        sendHtmlEmail(order.getClient().getEmail(), subject, html, order.getCompany());
    }

    public void sendDesignApprovalRequest(WorkOrder order) {
        sendDesignApprovalRequest(order, null);
    }

    public EmailPreview buildDesignApprovalPreview(WorkOrder order, String approvalUrl) {
        if (order == null || order.getClient() == null) {
            return new EmailPreview("Design Approval Required", "<p>Order or client missing.</p>");
        }
        boolean serbian = isSerbianCompany(order.getCompany());
        String subject = serbian ? "Potrebno odobrenje dizajna" : "Design Approval Required";
        String link = approvalUrl != null && !approvalUrl.isBlank()
            ? approvalUrl
            : buildPublicOrderUrl(order);
        Map<String, Object> model = buildOrderEmailModel(order, link);
        String html = emailTemplateService.render("design-approval-request", model);
        return new EmailPreview(subject, html);
    }

    public EmailPreview buildDesignFeedbackPreview(WorkOrder order, boolean approved, String comment) {
        if (order == null || order.getClient() == null) {
            return new EmailPreview("Design Feedback", "<p>Order or client missing.</p>");
        }
        boolean serbian = isSerbianCompany(order.getCompany());
        String subject = serbian ? "Povratna informacija o dizajnu" : "Design Feedback";
        String link = buildPublicOrderUrl(order);
        String html = emailTemplateService.render("design-feedback-client", buildDesignFeedbackModel(order, approved, comment, link));
        return new EmailPreview(subject, html);
    }

    private Map<String, Object> buildOrderEmailModel(WorkOrder order, String trackingUrl) {
        Map<String, Object> model = new HashMap<>();
        Company company = order.getCompany();
        model.put("orderNumber", order.getOrderNumber());
        model.put("orderTitle", order.getTitle());
        model.put("clientName", order.getClient() != null ? order.getClient().getCompanyName() : "");
        model.put("companyName", company != null ? company.getName() : "PrintFlow");
        model.put("trackingUrl", trackingUrl);
        model.put("logoUrl", buildPublicCompanyLogoUrl(company));
        model.put("logoInline", buildInlineCompanyLogo(company));
        model.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : "");
        model.put("orderPrice", order.getPrice());
        model.put("currency", company != null && company.getCurrency() != null ? company.getCurrency() : "RSD");
        model.put("deadline", order.getDeadline());
        model.put("companyEmail", company != null ? company.getEmail() : null);
        model.put("companyPhone", company != null ? company.getPhone() : null);
        model.put("companyWebsite", company != null ? company.getWebsite() : null);
        model.put("companyAddress", company != null ? company.getAddress() : null);
        model.put("lang", isSerbianCompany(company) ? "sr" : "en");
        return model;
    }

    public void sendDesignApprovalResultNotification(WorkOrder order, OrderStatus oldStatus, boolean approved, String comment) {
        if (order == null) {
            return;
        }
        String result = approved ? "approved" : "requested changes";
        String message = buildDesignFeedbackMessage(result, order.getOrderNumber(), comment);
        final String finalMessage = message;
        boolean serbian = isSerbianCompany(order.getCompany());
        String subject = approved
            ? (serbian ? "Dizajn je odobren" : "Design approved")
            : (serbian ? "Tražene su izmene dizajna" : "Design changes requested");
        String adminLink = buildUrl("/admin/orders/" + order.getId());
        String publicLink = buildPublicOrderUrl(order);
        String internalHtml = emailTemplateService.render("design-feedback-internal", buildDesignFeedbackModel(order, approved, comment, adminLink));
        String clientHtml = emailTemplateService.render("design-feedback-client", buildDesignFeedbackModel(order, approved, comment, publicLink));

        if (order.getClient() != null && order.getClient().getEmail() != null && !order.getClient().getEmail().isBlank()) {
            sendHtmlEmail(order.getClient().getEmail(), subject, clientHtml, order.getCompany());
        }

        if (order.getAssignedTo() != null) {
            createNotification(order.getAssignedTo().getId(), "Design Feedback", finalMessage, "DESIGN_FEEDBACK",
                adminLink);
            sendHtmlEmail(order.getAssignedTo().getEmail(), subject, internalHtml, order.getCompany());
        }

        if (order.getCompany() != null) {
            List<User> admins = userRepository.findByCompany_IdAndRoleInAndActiveTrue(
                order.getCompany().getId(),
                List.of(User.Role.ADMIN, User.Role.MANAGER)
            );
            List<Long> adminIds = admins.stream().map(User::getId).toList();
            if (!adminIds.isEmpty()) {
                createNotificationForUsers(adminIds, "Design Feedback", finalMessage, "DESIGN_FEEDBACK",
                    adminLink);
                for (User admin : admins) {
                    if (order.getAssignedTo() != null && order.getAssignedTo().getId().equals(admin.getId())) {
                        continue;
                    }
                    if (admin.getEmail() == null || admin.getEmail().isBlank()) {
                        continue;
                    }
                    sendHtmlEmail(admin.getEmail(), subject, internalHtml, order.getCompany());
                }
            }
        }
    }

    private Map<String, Object> buildDesignFeedbackModel(WorkOrder order, boolean approved, String comment, String link) {
        Map<String, Object> model = new HashMap<>();
        Company company = order.getCompany();
        model.put("approved", approved);
        boolean serbian = isSerbianCompany(company);
        model.put("action", approved
            ? (serbian ? "odobrio" : "approved")
            : (serbian ? "zatražio izmene" : "requested changes"));
        model.put("heading", approved
            ? (serbian ? "Dizajn je odobren" : "Design approved")
            : (serbian ? "Tražene su izmene dizajna" : "Design changes requested"));
        model.put(
            "statusLine",
            approved
                ? (serbian
                    ? "Vaš dizajn je odobren i proizvodnja može da se nastavi."
                    : "Your design has been approved and production can continue.")
                : (serbian
                    ? "Primili smo izmenu dizajna. Naš tim će doraditi i poslati novu verziju."
                    : "Your design update was received. Our team will revise and send an updated version.")
        );
        model.put("orderNumber", order.getOrderNumber());
        model.put("orderTitle", order.getTitle());
        model.put("currentStatus", order.getStatus() != null ? order.getStatus().name() : "UNKNOWN");
        model.put("comment", comment != null && !comment.isBlank() ? comment.trim() : null);
        model.put("link", link);
        model.put("logoUrl", buildPublicCompanyLogoUrl(company));
        model.put("logoInline", buildInlineCompanyLogo(company));
        model.put("companyName", company != null ? company.getName() : "PrintFlow");
        model.put("companyEmail", company != null ? company.getEmail() : null);
        model.put("companyPhone", company != null ? company.getPhone() : null);
        model.put("companyWebsite", company != null ? company.getWebsite() : null);
        model.put("companyAddress", company != null ? company.getAddress() : null);
        model.put("lang", isSerbianCompany(company) ? "sr" : "en");
        return model;
    }

    public void sendOrderAssignedNotification(WorkOrder order, User assignedTo) {
        if (order == null || assignedTo == null) {
            return;
        }
        String title = "New Order Assigned";
        String message = String.format("You have been assigned to order %s.", order.getOrderNumber());
        String link = buildUrl("/admin/orders/" + order.getId());
        createNotification(assignedTo.getId(), title, message, "ORDER_ASSIGNED", link);
        sendTaskEmail(assignedTo, title, "Order " + order.getOrderNumber(), link);
    }

    public void sendPreviewEmail(String toEmail, EmailPreview preview) {
        if (preview == null) {
            throw new RuntimeException("Preview is missing");
        }
        sendEmailInternal(toEmail, preview.getSubject(), preview.getHtml(), true, null);
    }

    private String buildDesignFeedbackMessage(String result, String orderNumber, String comment) {
        String message = String.format("Client has %s the design for order %s.", result, orderNumber);
        if (comment != null && !comment.isBlank()) {
            message += " Comment: " + comment;
        }
        return message;
    }

    public void sendDeliveryNotification(WorkOrder order) {
        String subject = "Your Order is Ready";
        String message;
        
        if (order.getDeliveryType() == com.printflow.entity.WorkOrder.DeliveryType.PICKUP) {
            message = String.format(
                "Dear %s,\n\nYour order %s is ready for pickup.\n" +
                "Order details: %s\n\nBest regards,\nPrintFlow Team",
                order.getClient().getContactPerson(),
                order.getOrderNumber(),
                order.getTitle()
            );
        } else {
            message = String.format(
                "Dear %s,\n\nYour order %s has been shipped.\n" +
                "Courier: %s\nTracking Number: %s\n\nBest regards,\nPrintFlow Team",
                order.getClient().getContactPerson(),
                order.getOrderNumber(),
                order.getCourierName(),
                order.getTrackingNumber()
            );
        }
        
        log.info("Delivery Notification: " + message);
        
        sendEmail(order.getClient().getEmail(), subject, message);
    }
    
    public void sendOverdueOrderNotification(WorkOrder order) {
        String message = String.format(
            "Order %s is overdue. Please take action.",
            order.getOrderNumber()
        );
        
        if (order.getAssignedTo() != null) {
            createNotification(order.getAssignedTo().getId(), 
                "Overdue Order", message, "OVERDUE_ORDER", buildUrl("/admin/orders/" + order.getId()));
        }
        
        log.info("Overdue Order Notification: " + message);
    }
    
    // ==================== SYSTEM NOTIFICATIONS ====================
    
    public void sendSystemNotification(Long userId, String title, String message) {
        createNotification(userId, title, message, "SYSTEM", null);
    }
    
    public void sendBroadcastNotification(List<Long> userIds, String title, String message) {
        createNotificationForUsers(userIds, title, message, "BROADCAST", null);
    }
    
    public void sendNewMessageNotification(Long userId, String senderName, String messagePreview) {
        String title = "New Message from " + senderName;
        String message = String.format("You have a new message: %s", messagePreview);
        createNotification(userId, title, message, "MESSAGE", null);
    }
    
    // ==================== CLEANUP METHODS ====================
    
    public void deleteOldNotifications(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        List<Notification> oldNotifications = notificationRepository
            .findByCreatedAtBeforeAndReadTrue(cutoffDate);
        
        notificationRepository.deleteAll(oldNotifications);
        log.info("Deleted {} old notifications", oldNotifications.size());
    }
    
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserCompanyId(notificationId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied to delete this notification");
        }
        
        notificationRepository.delete(notification);
    }
    
    // ==================== PRIVATE METHODS ====================
    
    private void sendEmail(String to, String subject, String body) {
        sendEmailInternal(to, subject, body, false, null);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        sendEmailInternal(to, subject, htmlBody, true, null);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody, Company company) {
        sendEmailInternal(to, subject, htmlBody, true, company);
    }

    private void sendTaskEmail(User user, String subject, String taskTitle, String link) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String html = buildTaskEmailHtml(user.getFullName(), subject, taskTitle, link);
        sendHtmlEmail(user.getEmail(), subject, html);
    }

    private String buildTaskEmailHtml(String fullName, String title, String taskTitle, String link) {
        String safeName = (fullName != null && !fullName.isBlank()) ? fullName : "there";
        String safeTask = (taskTitle != null) ? taskTitle : "Task";
        String safeLink = (link != null && !link.isBlank()) ? link : baseUrl;
        return """
            <div style="font-family: Arial, sans-serif; background:#f6f7fb; padding:24px;">
              <div style="max-width:600px; margin:0 auto; background:#ffffff; border:1px solid #e5e7eb; border-radius:12px; overflow:hidden;">
                <div style="padding:20px 24px; background:#111827; color:#ffffff;">
                  <h2 style="margin:0; font-size:18px;">PrintFlow</h2>
                  <p style="margin:6px 0 0; font-size:14px; color:#d1d5db;">%s</p>
                </div>
                <div style="padding:24px;">
                  <p style="font-size:14px; margin:0 0 16px;">Hi %s,</p>
                  <p style="font-size:14px; margin:0 0 16px;">
                    There is an update for task <strong>%s</strong>.
                  </p>
                  <a href="%s" style="display:inline-block; padding:10px 16px; background:#2563eb; color:#ffffff; text-decoration:none; border-radius:8px; font-size:14px;">
                    Open task
                  </a>
                </div>
                <div style="padding:16px 24px; border-top:1px solid #e5e7eb; font-size:12px; color:#6b7280;">
                  If the button doesn’t work, paste this link into your browser:<br/>
                  <span style="word-break:break-all;">%s</span>
                </div>
              </div>
            </div>
            """.formatted(title, safeName, safeTask, safeLink, safeLink);
    }

    private String buildClientUpdateEmailHtml(String contactPerson, String title, String taskTitle,
                                              String orderNumber, String orderTitle, Double price, String link,
                                              String companyName, String ctaLabel) {
        String name = (contactPerson != null && !contactPerson.isBlank()) ? contactPerson : "there";
        String safeTask = (taskTitle != null) ? taskTitle : "Update";
        String safeOrderNumber = (orderNumber != null) ? orderNumber : NOT_AVAILABLE;
        String safeOrderTitle = (orderTitle != null) ? orderTitle : "Order";
        String safePrice = (price != null) ? String.format("%.2f", price) : NOT_AVAILABLE;
        String safeLink = (link != null && !link.isBlank()) ? link : baseUrl;
        String safeCompany = (companyName != null && !companyName.isBlank()) ? companyName : "PrintFlow";
        String safeCta = (ctaLabel != null && !ctaLabel.isBlank()) ? ctaLabel : "Open order";
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; background:#f3f4f6; padding:24px;">
              <div style="max-width:620px; margin:0 auto;">
                <div style="background:linear-gradient(135deg,#0f172a,#1e293b); color:#ffffff; padding:20px 24px; border-radius:16px 16px 0 0;">
                  <div style="font-size:12px; letter-spacing:1px; text-transform:uppercase; color:#cbd5f5;">%s</div>
                  <div style="font-size:22px; font-weight:700; margin-top:6px;">%s</div>
                  <div style="margin-top:10px; display:inline-block; background:#2563eb; color:#ffffff; padding:4px 10px; border-radius:999px; font-size:11px; letter-spacing:0.3px;">
                    Action required
                  </div>
                </div>
                <div style="background:#ffffff; border:1px solid #e5e7eb; border-top:none; padding:24px;">
                  <p style="font-size:14px; margin:0 0 10px;">Hi %s,</p>
                  <p style="font-size:15px; margin:0 0 18px; color:#111827;">
                    <strong>%s</strong>
                  </p>
                  <div style="background:#f8fafc; border:1px solid #e5e7eb; border-radius:12px; padding:16px; margin-bottom:18px;">
                    <div style="font-size:12px; color:#64748b;">Order number</div>
                    <div style="font-size:16px; font-weight:700; margin-bottom:8px;">%s</div>
                    <div style="font-size:12px; color:#64748b;">Title</div>
                    <div style="font-size:14px; font-weight:600; margin-bottom:8px;">%s</div>
                    <div style="font-size:12px; color:#64748b;">Price</div>
                    <div style="font-size:14px; font-weight:600;">%s</div>
                  </div>
                  <a href="%s" style="display:inline-block; padding:12px 18px; background:#2563eb; color:#ffffff; text-decoration:none; border-radius:10px; font-size:14px; font-weight:600;">
                    %s
                  </a>
                </div>
                <div style="background:#ffffff; border:1px solid #e5e7eb; border-top:none; padding:14px 24px; border-radius:0 0 16px 16px; font-size:12px; color:#6b7280;">
                  If the button doesn’t work, paste this link into your browser:<br/>
                  <span style="word-break:break-all;">%s</span>
                </div>
              </div>
            </div>
            """.formatted(safeCompany, title, name, safeTask, safeOrderNumber, safeOrderTitle, safePrice, safeLink, safeCta, safeLink);
    }

    private String buildUrl(String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        String normalizedBase = baseUrl != null ? baseUrl.replaceAll("/$", "") : "";
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private String buildPublicOrderUrl(WorkOrder order) {
        if (order == null || order.getPublicToken() == null || order.getPublicToken().isBlank()) {
            return buildUrl("/public/track");
        }
        String lang = isSerbianCompany(order.getCompany()) ? "sr" : "en";
        return buildUrl("/public/order/" + order.getPublicToken() + "?lang=" + lang);
    }

    public static class EmailPreview {
        private final String subject;
        private final String html;

        public EmailPreview(String subject, String html) {
            this.subject = subject;
            this.html = html;
        }

        public String getSubject() {
            return subject;
        }

        public String getHtml() {
            return html;
        }
    }

    private void sendEmailInternal(String to, String subject, String body, boolean html, Company company) {
        com.printflow.dto.EmailMessage message = new com.printflow.dto.EmailMessage();
        message.setTo(to);
        message.setSubject(subject);
        if (html) {
            message.setHtmlBody(body);
        } else {
            message.setTextBody(body);
        }
        emailService.send(message, company, null);
    }

    public void sendPasswordResetEmail(User user, String resetUrl, String token) {
        sendPasswordResetEmail(user, resetUrl, token, user != null ? user.getCompany() : null);
    }

    public void sendPasswordResetEmail(User user, String resetUrl, String token, Company company) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Password reset requested but user email is missing.");
            return;
        }
        String logoUrl = buildCompanyLogoUrl(company, token, "reset");
        String subject = "Reset your PrintFlow password";
        String body = """
            <div style="font-family: Arial, sans-serif; color:#111;">
              %s
              <h2 style="margin:0 0 12px 0;">Password reset request</h2>
              <p>We received a request to reset your password.</p>
              <p>If you didn’t request this, you can ignore this email.</p>
              <p style="margin:16px 0;">
                <a href="%s" style="background:#2563eb;color:#fff;padding:10px 16px;border-radius:6px;text-decoration:none;">
                  Reset password
                </a>
              </p>
              <p style="font-size:12px;color:#555;">If the button doesn’t work, use this link:</p>
              <p style="font-size:12px;color:#555;word-break:break-all;">%s</p>
            </div>
            """.formatted(renderLogoHtml(logoUrl), resetUrl, resetUrl);
        sendEmailInternal(user.getEmail(), subject, body, true, company);
    }
    
    private void sendEmailNotification(WorkOrder order, String message) {
        if (order.getClient() != null && order.getClient().getEmail() != null) {
            sendEmailInternal(order.getClient().getEmail(), "Order Update", message, false, order.getCompany());
        }
    }

    private String buildCompanyLogoUrl(Company company, String token, String type) {
        if (company == null || company.getLogoPath() == null || company.getLogoPath().isBlank()) {
            return null;
        }
        if (token == null || token.isBlank() || type == null || type.isBlank()) {
            return null;
        }
        String base = buildUrl("/public/company-logo/" + company.getId());
        return base + "?token=" + token + "&type=" + type;
    }

    private String buildPublicCompanyLogoUrl(Company company) {
        if (company == null || company.getLogoPath() == null || company.getLogoPath().isBlank()) {
            return null;
        }
        return buildUrl("/public/company-logo/" + company.getId());
    }

    private boolean isSerbianCompany(Company company) {
        if (company == null) {
            return false;
        }
        if ("RSD".equalsIgnoreCase(company.getCurrency())) {
            return true;
        }
        String address = company.getAddress();
        if (address == null) {
            return false;
        }
        String lower = address.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("srbija") || lower.contains("serbia");
    }

    private String buildInlineCompanyLogo(Company company) {
        if (company == null || company.getId() == null || company.getLogoPath() == null || company.getLogoPath().isBlank()) {
            return null;
        }
        try {
            byte[] data = companyBrandingService.loadLogo(company.getId());
            String mime = MediaTypeFactory.getMediaType(company.getLogoPath())
                .map(Object::toString)
                .orElse("image/png");
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (Exception ex) {
            return null;
        }
    }

    private String renderLogoHtml(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            return "";
        }
        return """
            <div style="margin-bottom:14px;">
              <img src="%s" alt="Logo" style="height:48px; max-width:180px; object-fit:contain;" />
            </div>
            """.formatted(logoUrl);
    }
    
    private void sendSmsNotification(WorkOrder order, String message) {
        if (smsEnabled && order.getClient() != null && order.getClient().getPhone() != null) {
            log.info("Would send SMS to: {} - Message: {}", order.getClient().getPhone(), message);
            // Implementacija SMS servisa ide ovde
        }
    }
    
    // ==================== DTO CONVERSION ====================
    
    private NotificationDTO convertToDTO(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setType(notification.getType());
        dto.setLink(notification.getLink());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setReadAt(notification.getReadAt());
        
        if (notification.getUser() != null) {
            dto.setUserId(notification.getUser().getId());
        }
        
        return dto;
    }
    
    // ==================== QUERY METHODS (ISPRAVLJENE) ====================
    
    public List<NotificationDTO> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<NotificationDTO> getNotificationsByType(Long userId, String type) {
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public long getTotalNotificationCount(Long userId) {
        return notificationRepository.countByUserId(userId);
    }
    
    public List<NotificationDTO> getRecentNotifications(Long userId, int limit) {
        int effectiveLimit = normalizeRecentLimit(limit);
        Cache cache = cacheManager.getCache("recentNotifications");
        String key = "u:" + userId;
        if (cache == null) {
            return fetchRecentNotifications(userId, effectiveLimit);
        }
        List<NotificationDTO> cached = cache.get(key, () -> fetchRecentNotifications(userId, recentNotificationLimit));
        if (cached == null || cached.isEmpty()) {
            return List.of();
        }
        if (cached.size() <= effectiveLimit) {
            return cached;
        }
        return cached.subList(0, effectiveLimit);
    }
    
    // ==================== BULK OPERATIONS ====================
    
    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    public void markNotificationsAsRead(List<Long> notificationIds, Long userId) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        
        notifications.forEach(notification -> {
            if (notification.getUser().getId().equals(userId)) {
                notification.setRead(true);
                notification.setReadAt(LocalDateTime.now());
            }
        });
        
        notificationRepository.saveAll(notifications);
    }
    
    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    public void deleteMultipleNotifications(List<Long> notificationIds, Long userId) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        List<Notification> userNotifications = notificationRepository.findByIdsForUserAndCompany(
            notificationIds, userId, tenantGuard.requireCompanyId());
        if (!userNotifications.isEmpty()) {
            notificationRepository.deleteAll(userNotifications);
        }
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    // ==================== STATISTICS ====================
    
    public NotificationStatsDTO getNotificationStats(Long userId) {
        long total = notificationRepository.countByUserId(userId);
        long unread = notificationRepository.countByUserIdAndReadFalse(userId);
        long read = total - unread;
        
        // KORIGOVANO: Koristite countByUserIdAndCreatedAtAfter metodu
        long today = notificationRepository.countByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusDays(1));
        long week = notificationRepository.countByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusWeeks(1));
        
        return new NotificationStatsDTO(total, unread, read, today, week);
    }
    
    // ==================== ADDITIONAL HELPER METHODS ====================
    
    public Page<NotificationDTO> getNotificationsWithFilters(Long userId, String type, Boolean read, Pageable pageable) {
        return notificationRepository.findByUserIdWithFilters(userId, type, read, pageable)
            .map(this::convertToDTO);
    }
    
    public List<Object[]> getNotificationStatsByType(Long userId) {
        return notificationRepository.countByTypeGroupedByUserId(userId);
    }
    
    @Caching(evict = {
        @CacheEvict(cacheNames = "notificationCounts", key = "'u:' + #userId"),
        @CacheEvict(cacheNames = "recentNotifications", key = "'u:' + #userId")
    })
    public void markAllOldAsRead(Long userId, int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        List<Notification> oldUnreadNotifications = notificationRepository
            .findOldUnreadNotifications(userId, cutoffDate);
        
        oldUnreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        });
        
        notificationRepository.saveAll(oldUnreadNotifications);
    }

    private int normalizeRecentLimit(int requested) {
        int cap = Math.max(1, recentNotificationLimit);
        if (requested <= 0) {
            return cap;
        }
        return Math.min(requested, cap);
    }

    private List<NotificationDTO> fetchRecentNotifications(Long userId, int limit) {
        int safeLimit = normalizeRecentLimit(limit);
        return notificationRepository
            .findByUserIdAndCompanyIdOrderByCreatedAtDesc(
                userId,
                tenantGuard.requireCompanyId(),
                PageRequest.of(0, safeLimit)
            )
            .map(this::convertToDTO)
            .toList();
    }
    
    // ==================== INNER CLASSES ====================
    
    public static class NotificationStatsDTO {
        private final long totalNotifications;
        private final long unreadNotifications;
        private final long readNotifications;
        private final long todayNotifications;
        private final long weekNotifications;
        
        public NotificationStatsDTO(long totalNotifications, long unreadNotifications, 
                                   long readNotifications, long todayNotifications, 
                                   long weekNotifications) {
            this.totalNotifications = totalNotifications;
            this.unreadNotifications = unreadNotifications;
            this.readNotifications = readNotifications;
            this.todayNotifications = todayNotifications;
            this.weekNotifications = weekNotifications;
        }
        
        public long getTotalNotifications() { return totalNotifications; }
        public long getUnreadNotifications() { return unreadNotifications; }
        public long getReadNotifications() { return readNotifications; }
        public long getTodayNotifications() { return todayNotifications; }
        public long getWeekNotifications() { return weekNotifications; }
        
        public double getReadPercentage() {
            if (totalNotifications == 0) return 0;
            return (double) readNotifications / totalNotifications * 100;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Total: %d, Unread: %d, Read: %d, Today: %d, Week: %d, Read %%: %.2f%%",
                totalNotifications, unreadNotifications, readNotifications,
                todayNotifications, weekNotifications, getReadPercentage()
            );
        }
    }
}
