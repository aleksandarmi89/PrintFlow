package com.printflow.service;

import com.printflow.entity.Attachment;
import com.printflow.entity.Client;
import com.printflow.entity.ClientPortalAccess;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.AuditAction;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientPortalAccessRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ClientPortalService {

    private final ClientPortalAccessRepository accessRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final AttachmentRepository attachmentRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public ClientPortalService(ClientPortalAccessRepository accessRepository,
                               WorkOrderRepository workOrderRepository,
                               WorkOrderItemRepository workOrderItemRepository,
                               AttachmentRepository attachmentRepository,
                               AuditLogService auditLogService,
                               NotificationService notificationService) {
        this.accessRepository = accessRepository;
        this.workOrderRepository = workOrderRepository;
        this.workOrderItemRepository = workOrderItemRepository;
        this.attachmentRepository = attachmentRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public ClientPortalAccess getAccessOrThrow(String token) {
        String normalizedToken = normalizeToken(token);
        ClientPortalAccess access = accessRepository.findByAccessTokenWithClientAndCompany(normalizedToken)
            .orElseThrow(() -> new AccessDeniedException("Invalid portal token"));
        if (access.getExpiresAt() != null && access.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AccessDeniedException("Portal token expired");
        }
        access.setLastAccessedAt(LocalDateTime.now());
        return accessRepository.save(access);
    }

    public ClientPortalAccess createOrRefreshAccess(Client client, Company company, LocalDateTime expiresAt) {
        ClientPortalAccess access = accessRepository.findByClient_IdAndCompany_Id(client.getId(), company.getId())
            .orElseGet(ClientPortalAccess::new);
        access.setClient(client);
        access.setCompany(company);
        access.setAccessToken(UUID.randomUUID().toString());
        access.setExpiresAt(expiresAt);
        return accessRepository.save(access);
    }

    public ClientPortalAccess getAccessForClient(Long clientId, Long companyId) {
        return accessRepository.findByClient_IdAndCompany_Id(clientId, companyId).orElse(null);
    }

    public List<WorkOrder> getActiveOrdersForClient(Client client, Company company) {
        List<WorkOrder> orders = workOrderRepository.findByClient_IdAndCompany_Id(client.getId(), company.getId());
        List<WorkOrder> filtered = orders.stream()
            .filter(order -> order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.CANCELLED)
            .sorted(Comparator.comparing(WorkOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
        applyItemTotals(filtered, company);
        return filtered;
    }

    public List<WorkOrder> getRecentOrdersForClient(Client client, Company company, int maxCount) {
        int limit = Math.max(1, maxCount);
        List<WorkOrder> orders = workOrderRepository.findByClient_IdAndCompany_Id(client.getId(), company.getId());
        List<WorkOrder> recent = orders.stream()
            .sorted(Comparator.comparing(WorkOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(limit)
            .toList();
        applyItemTotals(recent, company);
        return recent;
    }

    public List<Attachment> getAttachmentsForClient(Client client, Company company) {
        List<WorkOrder> orders = workOrderRepository.findByClient_IdAndCompany_Id(client.getId(), company.getId());
        List<Long> orderIds = orders.stream().map(WorkOrder::getId).toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }
        List<Attachment> attachments = attachmentRepository.findByWorkOrderIdInAndActiveTrue(orderIds);
        boolean changed = false;
        for (Attachment attachment : attachments) {
            if (attachment.getApprovalToken() == null || attachment.getApprovalToken().isBlank()) {
                attachment.setApprovalToken(UUID.randomUUID().toString());
                changed = true;
            }
        }
        if (changed) {
            attachmentRepository.saveAll(attachments);
        }
        return attachments;
    }

    public void approveAttachment(Attachment attachment, String clientName, String ip) {
        if (attachment.isApproved()) {
            throw new IllegalStateException("Design already approved");
        }
        attachment.setApproved(true);
        attachment.setApprovedAt(LocalDateTime.now());
        attachment.setApprovedBy(clientName != null ? clientName : "PORTAL_USER");
        attachment.setApprovalIp(ip);
        // Do not overwrite approvalToken: it is the attachment lookup token and must remain stable.
        attachmentRepository.save(attachment);

        auditLogService.log(AuditAction.APPROVE, "Attachment", attachment.getId(), null, "approved",
            "Design approved via portal", attachment.getWorkOrder().getCompany());
        notificationService.notifyClientDesignApproved(attachment.getWorkOrder());
    }

    public void rejectAttachmentForRevision(Attachment attachment, String clientName, String ip, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() > 500) {
            normalizedReason = normalizedReason.substring(0, 500);
        }
        attachment.setApproved(false);
        attachment.setApprovedAt(null);
        attachment.setApprovedBy(null);
        attachment.setApprovalIp(ip);
        attachmentRepository.save(attachment);

        String actor = (clientName == null || clientName.isBlank()) ? "portal client" : clientName;
        String details = normalizedReason.isBlank()
            ? "Design rejected via portal by " + actor
            : "Design rejected via portal by " + actor + ": " + normalizedReason;
        auditLogService.log(AuditAction.REJECT, "Attachment", attachment.getId(), null, "rejected",
            details, attachment.getWorkOrder().getCompany());
    }

    public Attachment getAttachmentForApproval(String approvalToken, Long clientId) {
        return attachmentRepository.findByApprovalTokenAndWorkOrder_Client_IdAndActiveTrue(approvalToken, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
    }

    private void applyItemTotals(List<WorkOrder> orders, Company company) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Long companyId = company != null ? company.getId() : null;
        if (companyId == null) {
            return;
        }
        List<Long> orderIds = orders.stream()
            .map(WorkOrder::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
        if (orderIds.isEmpty()) {
            return;
        }
        java.util.Map<Long, Double> priceMap = toDoubleMap(
            workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(orderIds, companyId)
        );
        java.util.Map<Long, Double> costMap = toDoubleMap(
            workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(orderIds, companyId)
        );
        java.util.Map<Long, Long> itemCountMap = toLongMap(
            workOrderItemRepository.countItemsByWorkOrderIdsAndCompanyId(orderIds, companyId)
        );
        List<WorkOrder> changedOrders = new ArrayList<>();
        for (WorkOrder order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }
            Long orderId = order.getId();
            long itemCount = itemCountMap.getOrDefault(orderId, 0L);
            if (itemCount <= 0L) {
                // Keep manually managed order totals when there are no pricing items.
                continue;
            }
            boolean changed = false;
            Double nextPrice = priceMap.getOrDefault(orderId, 0.0d);
            if (order.getPrice() == null || Math.abs(order.getPrice() - nextPrice) > 0.0001d) {
                order.setPrice(nextPrice);
                changed = true;
            }
            Double nextCost = costMap.getOrDefault(orderId, 0.0d);
            if (order.getCost() == null || Math.abs(order.getCost() - nextCost) > 0.0001d) {
                order.setCost(nextCost);
                changed = true;
            }
            if (changed) {
                changedOrders.add(order);
            }
        }
        if (!changedOrders.isEmpty()) {
            workOrderRepository.saveAll(changedOrders);
        }
    }

    private java.util.Map<Long, Double> toDoubleMap(List<Object[]> rows) {
        java.util.Map<Long, Double> result = new java.util.HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            try {
                result.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
            } catch (Exception ignored) {
                // Ignore malformed aggregate rows and keep existing WorkOrder totals.
            }
        }
        return result;
    }

    private java.util.Map<Long, Long> toLongMap(List<Object[]> rows) {
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            try {
                result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            } catch (Exception ignored) {
                // Ignore malformed aggregate rows and keep existing WorkOrder totals.
            }
        }
        return result;
    }

    private String normalizeToken(String token) {
        if (token == null) {
            throw new AccessDeniedException("Invalid portal token");
        }
        String normalized = token.trim().replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            throw new AccessDeniedException("Invalid portal token");
        }
        return normalized;
    }
}
