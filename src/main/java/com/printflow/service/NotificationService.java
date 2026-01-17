package com.printflow.service;

import com.printflow.dto.NotificationDTO;
import com.printflow.entity.Notification;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.repository.NotificationRepository;
import com.printflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final boolean emailEnabled;
    private final boolean smsEnabled;
    public NotificationService(
            NotificationRepository notificationRepository, 
            UserRepository userRepository,
            @Value("${app.notification.email.enabled:false}") boolean emailEnabled, 
            @Value("${app.notification.sms.enabled:false}") boolean smsEnabled) {
        
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
    }
    
    // ==================== USER NOTIFICATIONS ====================
    
    public Page<NotificationDTO> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::convertToDTO);
    }
    
    public int getUnreadNotificationCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
    
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied to this notification");
        }
        
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
    
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = notificationRepository
            .findByUserIdAndReadFalse(userId);
        
        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        });
        
        notificationRepository.saveAll(unreadNotifications);
    }
    
    public void createNotification(Long userId, String title, String message, String type) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        
        notificationRepository.save(notification);
    }
    
    public void createNotificationForUsers(List<Long> userIds, String title, String message, String type) {
        List<User> users = userRepository.findAllById(userIds);
        
        List<Notification> notifications = users.stream().map(user -> {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            return notification;
        }).collect(Collectors.toList());
        
        notificationRepository.saveAll(notifications);
    }
    
    // ==================== TASK NOTIFICATIONS ====================
    
    public void sendTaskAssignedNotification(Long taskId, Long assignedToId, String taskTitle) {
        String message = String.format("You have been assigned a new task: %s", taskTitle);
        createNotification(assignedToId, "New Task Assigned", message, "TASK_ASSIGNED");
    }
    
    public void sendTaskStatusNotification(Long taskId, Long userId, String taskTitle, String oldStatus, String newStatus) {
        String message = String.format("Task '%s' status changed from %s to %s", 
            taskTitle, oldStatus, newStatus);
        createNotification(userId, "Task Status Updated", message, "TASK_UPDATE");
    }
    
    public void sendTaskCommentNotification(Long taskId, Long commenterId, Long taskOwnerId, String taskTitle) {
        if (!commenterId.equals(taskOwnerId)) {
            String message = String.format("New comment on task: %s", taskTitle);
            createNotification(taskOwnerId, "New Comment", message, "TASK_COMMENT");
        }
    }
    
    public void sendTaskTimeLoggedNotification(Long taskId, Long userId, String taskTitle, double hours) {
        String message = String.format("%.2f hours logged on task: %s", hours, taskTitle);
        createNotification(userId, "Time Logged", message, "TIME_LOGGED");
    }
    
    public void sendTaskCompletedNotification(Long taskId, Long userId, String taskTitle) {
        String message = String.format("Task completed: %s", taskTitle);
        createNotification(userId, "Task Completed", message, "TASK_COMPLETED");
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
                "Order Status Updated", message, "ORDER_UPDATE");
        }
        
        if (emailEnabled) {
            sendEmailNotification(order, message);
        }
        
        if (smsEnabled && order.getClient() != null && order.getClient().getPhone() != null) {
            sendSmsNotification(order, message);
        }
    }
    
    public void sendDesignApprovalRequest(WorkOrder order, String approvalUrl) {
        String subject = "Design Approval Required";
        String message = String.format(
            "Dear %s,\n\nPlease review and approve the design for order %s.\n" +
            "You can view it here: %s\n\nBest regards,\nPrintFlow Team",
            order.getClient().getContactPerson(),
            order.getOrderNumber(),
            approvalUrl
        );
        
        log.info("Design Approval Request: " + message);
        
        sendEmail(order.getClient().getEmail(), subject, message);
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
                "Overdue Order", message, "OVERDUE_ORDER");
        }
        
        log.info("Overdue Order Notification: " + message);
    }
    
    // ==================== SYSTEM NOTIFICATIONS ====================
    
    public void sendSystemNotification(Long userId, String title, String message) {
        createNotification(userId, title, message, "SYSTEM");
    }
    
    public void sendBroadcastNotification(List<Long> userIds, String title, String message) {
        createNotificationForUsers(userIds, title, message, "BROADCAST");
    }
    
    public void sendNewMessageNotification(Long userId, String senderName, String messagePreview) {
        String title = "New Message from " + senderName;
        String message = String.format("You have a new message: %s", messagePreview);
        createNotification(userId, title, message, "MESSAGE");
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
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied to delete this notification");
        }
        
        notificationRepository.delete(notification);
    }
    
    // ==================== PRIVATE METHODS ====================
    
    private void sendEmail(String to, String subject, String body) {
        if (emailEnabled) {
            log.info("Sending email to: {} - Subject: {}", to, subject);
            // Implementacija JavaMailSender-a ide ovde
            // emailSender.send(simpleMailMessage);
        } else {
            log.info("Email sending is disabled. Would send to: {} - Subject: {}", to, subject);
        }
    }
    
    private void sendEmailNotification(WorkOrder order, String message) {
        if (order.getClient() != null && order.getClient().getEmail() != null) {
            sendEmail(order.getClient().getEmail(), "Order Update", message);
        }
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
        // KORIGOVANO: koristite findTopNByUserIdOrderByCreatedAtDesc umesto findTopByUserIdOrderByCreatedAtDesc
        return notificationRepository.findTopNByUserIdOrderByCreatedAtDesc(userId, limit).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // ==================== BULK OPERATIONS ====================
    
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
    
    public void deleteMultipleNotifications(List<Long> notificationIds, Long userId) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        
        List<Notification> userNotifications = notifications.stream()
            .filter(n -> n.getUser().getId().equals(userId))
            .collect(Collectors.toList());
        
        notificationRepository.deleteAll(userNotifications);
    }
    
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