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
import com.printflow.repository.WorkOrderRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ClientPortalService {

    private final ClientPortalAccessRepository accessRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AttachmentRepository attachmentRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public ClientPortalService(ClientPortalAccessRepository accessRepository,
                               WorkOrderRepository workOrderRepository,
                               AttachmentRepository attachmentRepository,
                               AuditLogService auditLogService,
                               NotificationService notificationService) {
        this.accessRepository = accessRepository;
        this.workOrderRepository = workOrderRepository;
        this.attachmentRepository = attachmentRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public ClientPortalAccess getAccessOrThrow(String token) {
        ClientPortalAccess access = accessRepository.findByAccessToken(token)
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
        return orders.stream()
            .filter(order -> order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.CANCELLED)
            .toList();
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

    public Attachment getAttachmentForApproval(String approvalToken, Long clientId) {
        return attachmentRepository.findByApprovalTokenAndWorkOrder_Client_IdAndActiveTrue(approvalToken, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
    }
}
