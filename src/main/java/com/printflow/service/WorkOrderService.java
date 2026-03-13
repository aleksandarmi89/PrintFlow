package com.printflow.service;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.entity.Client;
import com.printflow.entity.User;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.AuditAction;
import com.printflow.entity.enums.PrintType;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.UserRepository;
import com.printflow.events.DesignApprovalRequestedEvent;
import com.printflow.events.OrderCreatedEvent;
import com.printflow.events.OrderStatusChangedEvent;
import com.printflow.repository.AttachmentRepository;
import com.printflow.util.OrderNumberGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkOrderService {
    
    private final WorkOrderRepository workOrderRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final TenantGuard tenantGuard;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final PlanLimitService planLimitService;
    private final BillingAccessService billingAccessService;
    private final PublicTokenService publicTokenService;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final ClientPricingProfileService pricingProfileService;
    private final ActivityLogService activityLogService;
    private final ApplicationEventPublisher eventPublisher;
    
    public WorkOrderService(WorkOrderRepository workOrderRepository, 
                          ClientRepository clientRepository,
                          UserRepository userRepository, 
                          AttachmentRepository attachmentRepository,
                          OrderNumberGenerator orderNumberGenerator, 
                          TenantGuard tenantGuard,
                          NotificationService notificationService,
                          AuditLogService auditLogService,
                          PlanLimitService planLimitService,
                          BillingAccessService billingAccessService,
                          PublicTokenService publicTokenService,
                          WorkOrderItemRepository workOrderItemRepository,
                          ClientPricingProfileService pricingProfileService,
                          ActivityLogService activityLogService,
                          ApplicationEventPublisher eventPublisher) {
        this.workOrderRepository = workOrderRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.tenantGuard = tenantGuard;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.planLimitService = planLimitService;
        this.billingAccessService = billingAccessService;
        this.publicTokenService = publicTokenService;
        this.workOrderItemRepository = workOrderItemRepository;
        this.pricingProfileService = pricingProfileService;
        this.activityLogService = activityLogService;
        this.eventPublisher = eventPublisher;
    }

    public WorkOrderDTO createWorkOrder(WorkOrderDTO workOrderDTO) {
        Long companyId = tenantGuard.requireCompanyId();
        String normalizedTitle = requireTitle(workOrderDTO.getTitle());
        Long clientId = requireClientId(workOrderDTO.getClientId());
        Client client = getClientOrThrow(clientId, companyId);
        billingAccessService.assertBillingActiveForPremiumAction(client.getCompany().getId());
        planLimitService.assertMonthlyOrdersLimit(client.getCompany());
        
        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber(orderNumberGenerator.generateOrderNumber());
        workOrder.setTitle(normalizedTitle);
        workOrder.setDescription(workOrderDTO.getDescription());
        workOrder.setSpecifications(workOrderDTO.getSpecifications());
        workOrder.setStatus(OrderStatus.NEW);
        workOrder.setPriority(workOrderDTO.getPriority() != null ? workOrderDTO.getPriority() : 5);
        workOrder.setDeadline(workOrderDTO.getDeadline());
        workOrder.setEstimatedHours(workOrderDTO.getEstimatedHours());
        workOrder.setPrice(workOrderDTO.getPrice());
        workOrder.setCost(workOrderDTO.getCost());
        workOrder.setClient(client);
        workOrder.setCompany(client.getCompany());
        workOrder.setPrintType(workOrderDTO.getPrintType() != null ? workOrderDTO.getPrintType() : PrintType.OTHER);
        PublicTokenService.TokenInfo tokenInfo = publicTokenService.newToken();
        workOrder.setPublicToken(tokenInfo.token());
        workOrder.setPublicTokenCreatedAt(tokenInfo.createdAt());
        workOrder.setPublicTokenExpiresAt(tokenInfo.expiresAt());
        
        if (workOrderDTO.getAssignedToId() != null) {
            User assignedTo = userRepository.findByIdAndCompany_Id(workOrderDTO.getAssignedToId(), companyId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            workOrder.setAssignedTo(assignedTo);
        }
        
        if (workOrderDTO.getCreatedById() != null) {
            User createdBy = userRepository.findByIdAndCompany_Id(workOrderDTO.getCreatedById(), companyId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            workOrder.setCreatedBy(createdBy);
        }
        
        WorkOrder savedOrder = workOrderRepository.save(workOrder);
        eventPublisher.publishEvent(new OrderCreatedEvent(savedOrder.getId()));
        return convertToDTO(savedOrder);
    }
    
    public WorkOrderDTO updateWorkOrder(Long id, WorkOrderDTO workOrderDTO) {
        WorkOrder workOrder = getWorkOrderWithRelationsOrThrow(id);
        String normalizedTitle = requireTitle(workOrderDTO.getTitle());
        Long oldClientId = workOrder.getClient() != null ? workOrder.getClient().getId() : null;
        String oldPrintType = workOrder.getPrintType() != null ? workOrder.getPrintType().name() : null;
        OrderStatus oldStatusEnum = workOrder.getStatus();
        String oldStatus = oldStatusEnum != null ? oldStatusEnum.name() : null;
        Double oldPrice = workOrder.getPrice();
        Double oldCost = workOrder.getCost();
        Long oldAssignedId = workOrder.getAssignedTo() != null ? workOrder.getAssignedTo().getId() : null;
        
        workOrder.setTitle(normalizedTitle);
        workOrder.setDescription(workOrderDTO.getDescription());
        workOrder.setSpecifications(workOrderDTO.getSpecifications());
        workOrder.setPriority(workOrderDTO.getPriority());
        workOrder.setDeadline(workOrderDTO.getDeadline());
        workOrder.setEstimatedHours(workOrderDTO.getEstimatedHours());
        workOrder.setActualHours(workOrderDTO.getActualHours());
        workOrder.setPrice(workOrderDTO.getPrice());
        workOrder.setCost(workOrderDTO.getCost());
        workOrder.setPaid(workOrderDTO.getPaid());
        workOrder.setInternalNotes(workOrderDTO.getInternalNotes());
        workOrder.setDeliveryType(workOrderDTO.getDeliveryType());
        workOrder.setCourierName(workOrderDTO.getCourierName());
        workOrder.setTrackingNumber(workOrderDTO.getTrackingNumber());
        workOrder.setDeliveryAddress(workOrderDTO.getDeliveryAddress());
        workOrder.setDeliveryDate(workOrderDTO.getDeliveryDate());
        workOrder.setPrintType(workOrderDTO.getPrintType() != null ? workOrderDTO.getPrintType() : PrintType.OTHER);
        
        if (workOrderDTO.getAssignedToId() != null) {
            User assignedTo = userRepository.findByIdAndCompany_Id(workOrderDTO.getAssignedToId(), tenantGuard.requireCompanyId())
                .orElseThrow(() -> new RuntimeException("User not found"));
            workOrder.setAssignedTo(assignedTo);
        } else {
            workOrder.setAssignedTo(null);
        }
        
        if (workOrderDTO.getStatus() != null) {
            validateOrderStatusTransition(workOrder.getStatus(), workOrderDTO.getStatus());
            workOrder.setStatus(workOrderDTO.getStatus());
        }

        if (workOrderDTO.getClientId() != null && (workOrder.getClient() == null || !workOrder.getClient().getId().equals(workOrderDTO.getClientId()))) {
            Client client = getClientOrThrow(workOrderDTO.getClientId(), tenantGuard.requireCompanyId());
            workOrder.setClient(client);
            workOrder.setCompany(client.getCompany());
        }
        
        WorkOrder updatedOrder = workOrderRepository.save(workOrder);
        if (oldClientId != null && workOrder.getClient() != null && !oldClientId.equals(workOrder.getClient().getId())) {
            auditLogService.log(AuditAction.UPDATE, "WorkOrder", workOrder.getId(),
                "clientId:" + oldClientId, "clientId:" + workOrder.getClient().getId(),
                "Order client updated", workOrder.getCompany());
        }
        String newPrintType = workOrder.getPrintType() != null ? workOrder.getPrintType().name() : null;
        if (oldPrintType != null && newPrintType != null && !oldPrintType.equals(newPrintType)) {
            auditLogService.log(AuditAction.UPDATE, "WorkOrder", workOrder.getId(),
                oldPrintType, newPrintType, "Order print type updated", workOrder.getCompany());
        }
        String newStatus = workOrder.getStatus() != null ? workOrder.getStatus().name() : null;
        if (oldStatus != null && newStatus != null && !oldStatus.equals(newStatus)) {
            auditLogService.log(AuditAction.STATUS_CHANGE, "WorkOrder", workOrder.getId(),
                oldStatus, newStatus, "Order status updated", workOrder.getCompany());
            eventPublisher.publishEvent(new OrderStatusChangedEvent(updatedOrder.getId(), oldStatusEnum, workOrder.getStatus()));
            if (workOrder.getStatus() == OrderStatus.READY_FOR_DELIVERY) {
                notificationService.notifyClientOrderReady(updatedOrder);
            }
        }
        Double newPrice = workOrder.getPrice();
        if (oldPrice != null && newPrice != null && !oldPrice.equals(newPrice)) {
            auditLogService.log(AuditAction.UPDATE, "WorkOrder", workOrder.getId(),
                String.valueOf(oldPrice), String.valueOf(newPrice), "Order price updated", workOrder.getCompany());
        }
        Double newCost = workOrder.getCost();
        if (oldCost != null && newCost != null && !oldCost.equals(newCost)) {
            auditLogService.log(AuditAction.UPDATE, "WorkOrder", workOrder.getId(),
                String.valueOf(oldCost), String.valueOf(newCost), "Order cost updated", workOrder.getCompany());
        }
        Long newAssignedId = workOrder.getAssignedTo() != null ? workOrder.getAssignedTo().getId() : null;
        if (!java.util.Objects.equals(oldAssignedId, newAssignedId)) {
            auditLogService.log(AuditAction.UPDATE, "WorkOrder", workOrder.getId(),
                "assignedToId:" + oldAssignedId, "assignedToId:" + newAssignedId, "Order assignment updated", workOrder.getCompany());
            if (workOrder.getAssignedTo() != null) {
                notificationService.sendOrderAssignedNotification(workOrder, workOrder.getAssignedTo());
            }
        }
        return convertToDTO(updatedOrder);
    }
    
    public WorkOrderDTO updateWorkOrderStatus(Long id, OrderStatus status, String notes) {
        if (status == null) {
            throw new RuntimeException("Status is required");
        }
        OrderStatus nextStatus = status;
        WorkOrder workOrder = getWorkOrderWithRelationsOrThrow(id);
        
        OrderStatus oldStatus = workOrder.getStatus();
        validateOrderStatusTransition(oldStatus, nextStatus);
        workOrder.setStatus(nextStatus);
        
        boolean printedEvent = nextStatus == OrderStatus.WAITING_QUALITY_CHECK;
        if (notes != null && !notes.trim().isEmpty()) {
            workOrder.setInternalNotes(
                (workOrder.getInternalNotes() != null ? workOrder.getInternalNotes() + "\n" : "") +
                LocalDateTime.now() + ": Status changed from " + oldStatus + " to " + nextStatus +
                " - " + notes.trim()
            );
        } else if (printedEvent) {
            workOrder.setInternalNotes(
                (workOrder.getInternalNotes() != null ? workOrder.getInternalNotes() + "\n" : "") +
                LocalDateTime.now() + ": Štampano"
            );
        }
        
        if (nextStatus == OrderStatus.COMPLETED) {
            workOrder.setCompletedAt(LocalDateTime.now());
        }
        
        WorkOrder updatedOrder = workOrderRepository.save(workOrder);
        String auditDescription = printedEvent ? "Štampano" : "Order status updated";
        auditLogService.log(AuditAction.STATUS_CHANGE, "WorkOrder", workOrder.getId(),
            oldStatus != null ? oldStatus.name() : null,
            nextStatus.name(),
            auditDescription,
            workOrder.getCompany());
        Long actorId = tenantGuard.getCurrentUser() != null ? tenantGuard.getCurrentUser().getId() : null;
        activityLogService.log(updatedOrder,
            "STATUS_CHANGE",
            "Status changed to " + orderStatusName(nextStatus),
            actorId);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(updatedOrder.getId(), oldStatus, nextStatus));
        if (nextStatus == OrderStatus.READY_FOR_DELIVERY) {
            notificationService.notifyClientOrderReady(updatedOrder);
        }
        return convertToDTO(updatedOrder);
    }

    public WorkOrderDTO reorderWorkOrder(Long sourceId, Long createdById, String sourceLabel) {
        WorkOrder source = getWorkOrderWithRelationsOrThrow(sourceId);
        String reorderReason = buildReorderReason(source.getOrderNumber(), sourceLabel);
        WorkOrder newOrder = new WorkOrder();
        newOrder.setOrderNumber(orderNumberGenerator.generateOrderNumber());
        newOrder.setTitle(source.getTitle());
        newOrder.setDescription(source.getDescription());
        newOrder.setSpecifications(source.getSpecifications());
        newOrder.setStatus(OrderStatus.NEW);
        newOrder.setPriority(source.getPriority());
        newOrder.setDeadline(null);
        newOrder.setEstimatedHours(source.getEstimatedHours());
        newOrder.setClient(source.getClient());
        newOrder.setCompany(source.getCompany());
        newOrder.setPrintType(source.getPrintType());
        newOrder.setInternalNotes(reorderReason);
        PublicTokenService.TokenInfo tokenInfo = publicTokenService.newToken();
        newOrder.setPublicToken(tokenInfo.token());
        newOrder.setPublicTokenCreatedAt(tokenInfo.createdAt());
        newOrder.setPublicTokenExpiresAt(tokenInfo.expiresAt());
        if (createdById != null) {
            Long sourceCompanyId = source.getCompany() != null ? source.getCompany().getId() : tenantGuard.requireCompanyId();
            newOrder.setCreatedBy(resolveCreatedByForClone(createdById, sourceCompanyId));
        }

        WorkOrder saved = workOrderRepository.save(newOrder);
        List<WorkOrderItem> items = workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
            sourceId, tenantGuard.requireCompanyId());
        for (WorkOrderItem item : items) {
            WorkOrderItem clone = new WorkOrderItem();
            clone.setCompany(saved.getCompany());
            clone.setWorkOrder(saved);
            clone.setVariant(item.getVariant());
            clone.setQuantity(item.getQuantity());
            clone.setWidthMm(item.getWidthMm());
            clone.setHeightMm(item.getHeightMm());
            clone.setAttributesJson(item.getAttributesJson());
            clone.setCalculatedCost(item.getCalculatedCost());
            clone.setCalculatedPrice(item.getCalculatedPrice());
            clone.setProfitAmount(item.getProfitAmount());
            clone.setMarginPercent(item.getMarginPercent());
            clone.setCurrency(item.getCurrency());
            clone.setBreakdownJson(item.getBreakdownJson());
            clone.setPricingSnapshotJson(item.getPricingSnapshotJson());
            clone.setPriceLocked(true);
            clone.setPriceCalculatedAt(LocalDateTime.now());
            workOrderItemRepository.save(clone);
            pricingProfileService.recordPrice(saved.getClient(), item.getVariant(), item.getCalculatedPrice());
        }
        auditLogService.log(AuditAction.CREATE, "WorkOrder", saved.getId(), null, null,
            reorderReason, saved.getCompany());
        activityLogService.log(saved,
            "ORDER_DUPLICATED",
            reorderReason,
            createdById);
        eventPublisher.publishEvent(new OrderCreatedEvent(saved.getId()));
        return convertToDTO(saved);
    }

    public WorkOrderDTO duplicateWorkOrder(Long sourceId, Long createdById, boolean includeAttachments) {
        WorkOrder source = getWorkOrderWithRelationsOrThrow(sourceId);
        WorkOrder newOrder = new WorkOrder();
        newOrder.setOrderNumber(orderNumberGenerator.generateOrderNumber());
        newOrder.setTitle(source.getTitle());
        newOrder.setDescription(source.getDescription());
        newOrder.setSpecifications(source.getSpecifications());
        newOrder.setStatus(OrderStatus.NEW);
        newOrder.setPriority(source.getPriority());
        newOrder.setDeadline(source.getDeadline());
        newOrder.setEstimatedHours(source.getEstimatedHours());
        newOrder.setClient(source.getClient());
        newOrder.setCompany(source.getCompany());
        newOrder.setPrintType(source.getPrintType());
        newOrder.setInternalNotes("Duplicated from WorkOrder #" + source.getOrderNumber());
        PublicTokenService.TokenInfo tokenInfo = publicTokenService.newToken();
        newOrder.setPublicToken(tokenInfo.token());
        newOrder.setPublicTokenCreatedAt(tokenInfo.createdAt());
        newOrder.setPublicTokenExpiresAt(tokenInfo.expiresAt());
        if (createdById != null) {
            Long sourceCompanyId = source.getCompany() != null ? source.getCompany().getId() : tenantGuard.requireCompanyId();
            newOrder.setCreatedBy(resolveCreatedByForClone(createdById, sourceCompanyId));
        }

        WorkOrder saved = workOrderRepository.save(newOrder);
        List<WorkOrderItem> items = workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
            sourceId, tenantGuard.requireCompanyId());
        for (WorkOrderItem item : items) {
            WorkOrderItem clone = new WorkOrderItem();
            clone.setCompany(saved.getCompany());
            clone.setWorkOrder(saved);
            clone.setVariant(item.getVariant());
            clone.setQuantity(item.getQuantity());
            clone.setWidthMm(item.getWidthMm());
            clone.setHeightMm(item.getHeightMm());
            clone.setAttributesJson(item.getAttributesJson());
            clone.setCalculatedCost(item.getCalculatedCost());
            clone.setCalculatedPrice(item.getCalculatedPrice());
            clone.setProfitAmount(item.getProfitAmount());
            clone.setMarginPercent(item.getMarginPercent());
            clone.setCurrency(item.getCurrency());
            clone.setBreakdownJson(item.getBreakdownJson());
            clone.setPricingSnapshotJson(item.getPricingSnapshotJson());
            clone.setPriceLocked(true);
            clone.setPriceCalculatedAt(LocalDateTime.now());
            workOrderItemRepository.save(clone);
            pricingProfileService.recordPrice(saved.getClient(), item.getVariant(), item.getCalculatedPrice());
        }

        if (includeAttachments) {
            List<com.printflow.entity.Attachment> attachments =
                attachmentRepository.findByWorkOrderIdAndCompany_IdAndActiveTrue(sourceId, tenantGuard.requireCompanyId());
            for (com.printflow.entity.Attachment attachment : attachments) {
                com.printflow.entity.Attachment copy = new com.printflow.entity.Attachment();
                copy.setCompany(saved.getCompany());
                copy.setWorkOrder(saved);
                copy.setFileName(attachment.getFileName());
                copy.setFilePath(attachment.getFilePath());
                copy.setOriginalFileName(attachment.getOriginalFileName());
                copy.setFileType(attachment.getFileType());
                copy.setMimeType(attachment.getMimeType());
                copy.setFileSize(attachment.getFileSize());
                copy.setAttachmentType(attachment.getAttachmentType());
                copy.setDescription(attachment.getDescription());
                copy.setThumbnailPath(attachment.getThumbnailPath());
                copy.setUploadedBy(attachment.getUploadedBy());
                copy.setActive(attachment.isActive());
                copy.setApproved(false);
                copy.setApprovedAt(null);
                copy.setApprovedBy(null);
                copy.setApprovalIp(null);
                copy.setApprovalToken(null);
                attachmentRepository.save(copy);
            }
        }

        auditLogService.log(AuditAction.CREATE, "WorkOrder", saved.getId(), null, null,
            "Duplicated from WorkOrder #" + source.getOrderNumber(), saved.getCompany());
        activityLogService.log(saved,
            "ORDER_DUPLICATED",
            "Duplicated from WorkOrder #" + source.getOrderNumber(),
            createdById);
        eventPublisher.publishEvent(new OrderCreatedEvent(saved.getId()));
        return convertToDTO(saved);
    }

    public WorkOrderDTO reorderWorkOrderForClientToken(String publicToken, Client client) {
        WorkOrder source = workOrderRepository.findByPublicTokenAndClient_IdAndCompany_Id(
            publicToken, client.getId(), client.getCompany().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return reorderWorkOrder(source.getId(), null, "portal");
    }
    
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        
        // Prvo obrišimo sve attachment-e
        attachmentRepository.deleteByWorkOrderId(id);
        
        // Onda obrišemo work order
        workOrderRepository.delete(workOrder);
    }
    
    public WorkOrderDTO getWorkOrderById(Long id) {
        WorkOrder workOrder = getWorkOrderWithRelationsOrThrow(id);
        return convertToDTO(workOrder);
    }

    public WorkOrder getWorkOrderEntityById(Long id) {
        return getWorkOrderWithRelationsOrThrow(id);
    }
    
    public WorkOrderDTO getWorkOrderByPublicToken(String token) {
        WorkOrder workOrder = getPublicWorkOrderOrThrow(token);
        return convertToDTO(workOrder);
    }

    public WorkOrder getWorkOrderEntityByPublicToken(String token) {
        return getPublicWorkOrderOrThrow(token);
    }

    public String resolvePublicTokenFromOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw workOrderNotFound();
        }
        String normalized = orderNumber.trim();
        WorkOrder workOrder = workOrderRepository.findByOrderNumberIgnoreCase(normalized)
            .orElseThrow(this::workOrderNotFound);
        if (workOrder.getPublicToken() == null || workOrder.getPublicToken().isBlank()) {
            throw workOrderNotFound();
        }
        if (workOrder.getPublicTokenExpiresAt() == null) {
            workOrder.setPublicTokenExpiresAt(publicTokenService.expiresAtFromNow());
            workOrderRepository.save(workOrder);
        }
        if (publicTokenService.isExpired(workOrder.getPublicTokenExpiresAt())) {
            throw workOrderNotFound();
        }
        return workOrder.getPublicToken();
    }
    
    public Page<WorkOrderDTO> getWorkOrders(Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            Page<WorkOrder> orders = workOrderRepository.findByOrderByCreatedAtDesc(pageable);
            return orders.map(this::convertToDTO);
        }
        Long companyId = tenantGuard.requireCompanyId();
        Page<WorkOrder> orders = workOrderRepository.findByCompany_IdOrderByCreatedAtDesc(companyId, pageable);
        return orders.map(this::convertToDTO);
    }

    public List<WorkOrderDTO> getRecentWorkOrders(int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, limit), Sort.by("createdAt").descending());
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.findByOrderByCreatedAtDesc(page).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return workOrderRepository.findByCompany_IdOrderByCreatedAtDesc(companyId, page).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> getWorkOrdersByStatus(OrderStatus status) {
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return workOrderRepository.findByStatusAndCompany_Id(status, companyId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public Page<WorkOrderDTO> getWorkOrdersByStatus(OrderStatus status, Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            Page<WorkOrder> orders = workOrderRepository.findByStatus(status, pageable);
            return orders.map(this::convertToDTO);
        }
        Long companyId = tenantGuard.requireCompanyId();
        Page<WorkOrder> orders = workOrderRepository.findByStatusAndCompany_Id(status, companyId, pageable);
        return orders.map(this::convertToDTO);
    }
    
    public List<WorkOrderDTO> getWorkOrdersByClient(Long clientId) {
        Client client = getClientOrThrow(clientId, tenantGuard.requireCompanyId());
        return workOrderRepository.findByClient(client).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> getWorkOrdersByAssignedUser(Long userId) {
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.findByAssignedToId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return workOrderRepository.findByAssignedToIdAndCompany_Id(userId, companyId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> searchWorkOrders(String query) {
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.search(query).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return workOrderRepository.searchByCompany(companyId, query).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public Page<WorkOrderDTO> searchWorkOrders(String query, Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            Page<WorkOrder> orders = workOrderRepository.searchAll(query, pageable);
            return orders.map(this::convertToDTO);
        }
        Long companyId = tenantGuard.requireCompanyId();
        Page<WorkOrder> orders = workOrderRepository.searchByCompanyAll(companyId, query, pageable);
        return orders.map(this::convertToDTO);
    }
    
    public List<WorkOrderDTO> getOverdueOrders() {
        List<OrderStatus> excludedStatuses = List.of(
            OrderStatus.COMPLETED, 
            OrderStatus.CANCELLED,
            OrderStatus.SENT
        );
        
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.findOverdueOrders(LocalDateTime.now(), excludedStatuses).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return workOrderRepository.findOverdueOrdersByCompany(
            companyId,
            LocalDateTime.now(),
            excludedStatuses
        ).stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
    }

    public List<WorkOrderDTO> getOverdueOrders(int limit) {
        List<OrderStatus> excludedStatuses = List.of(
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED,
            OrderStatus.SENT
        );
        PageRequest page = PageRequest.of(0, Math.max(1, limit), Sort.by("deadline").ascending());
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.findOverdueOrders(LocalDateTime.now(), excludedStatuses, page).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return workOrderRepository.findOverdueOrdersByCompany(
                companyId,
                LocalDateTime.now(),
                excludedStatuses,
                page
            ).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public long countOverdueOrders() {
        List<OrderStatus> excludedStatuses = List.of(
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED,
            OrderStatus.SENT
        );
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.countOverdueOrders(LocalDateTime.now(), excludedStatuses);
        }
        Long companyId = tenantGuard.requireCompanyId();
        return workOrderRepository.countOverdueOrdersByCompany(companyId, LocalDateTime.now(), excludedStatuses);
    }
    
    public WorkOrderDTO approveDesign(Long orderId, String token, boolean approved, String comment) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Work order not found");
        }
        WorkOrder workOrder = workOrderRepository
            .findByIdAndPublicTokenAndPublicTokenExpiresAtAfter(orderId, token, publicTokenService.now())
            .orElseThrow(() -> new RuntimeException("Work order not found"));
        if (workOrder.getCompany() != null) {
            billingAccessService.assertBillingActiveForPremiumAction(workOrder.getCompany().getId());
        }
        
        OrderStatus oldStatus = workOrder.getStatus();
        workOrder.setDesignApproved(approved);
        workOrder.setClientComment(comment);
        
        if (approved) {
            validateOrderStatusTransition(oldStatus, OrderStatus.APPROVED_FOR_PRINT);
            workOrder.setStatus(OrderStatus.APPROVED_FOR_PRINT);
        } else {
            validateOrderStatusTransition(oldStatus, OrderStatus.IN_DESIGN);
            workOrder.setStatus(OrderStatus.IN_DESIGN);
        }
        
        WorkOrder updatedOrder = workOrderRepository.save(workOrder);
        auditLogService.log(
            approved ? AuditAction.APPROVE : AuditAction.REJECT,
            "WorkOrder",
            workOrder.getId(),
            oldStatus != null ? oldStatus.name() : null,
            workOrder.getStatus() != null ? workOrder.getStatus().name() : null,
            approved ? "Client approved design" : "Client requested changes",
            workOrder.getCompany()
        );
        activityLogService.log(
            updatedOrder,
            approved ? "CLIENT_APPROVED" : "CLIENT_REQUESTED_CHANGES",
            approved ? "Client approved design" : "Client requested changes",
            null
        );
        notificationService.sendDesignApprovalResultNotification(updatedOrder, oldStatus, approved, comment);
        return convertToDTO(updatedOrder);
    }

    public String rotatePublicToken(Long orderId) {
        WorkOrder workOrder = getWorkOrderWithRelationsOrThrow(orderId);
        String oldToken = workOrder.getPublicToken();
        PublicTokenService.TokenInfo tokenInfo = publicTokenService.newToken();
        workOrder.setPublicToken(tokenInfo.token());
        workOrder.setPublicTokenCreatedAt(tokenInfo.createdAt());
        workOrder.setPublicTokenExpiresAt(tokenInfo.expiresAt());
        workOrderRepository.saveAndFlush(workOrder);
        auditLogService.log(AuditAction.UPDATE, "WorkOrder", workOrder.getId(),
            oldToken, tokenInfo.token(), "Rotated public tracking token", workOrder.getCompany());
        return tokenInfo.token();
    }

    public void sendForClientApproval(Long orderId) {
        WorkOrder workOrder = getWorkOrderWithRelationsOrThrow(orderId);

        if (workOrder.getClient() == null || workOrder.getClient().getEmail() == null) {
            throw new RuntimeException("Client email is required to send approval request");
        }

        OrderStatus oldStatus = workOrder.getStatus();
        validateOrderStatusTransition(oldStatus, OrderStatus.WAITING_CLIENT_APPROVAL);
        workOrder.setStatus(OrderStatus.WAITING_CLIENT_APPROVAL);
        workOrder.setDesignApproved(false);
        workOrderRepository.save(workOrder);

        eventPublisher.publishEvent(new DesignApprovalRequestedEvent(workOrder.getId()));
        auditLogService.log(AuditAction.STATUS_CHANGE, "WorkOrder", workOrder.getId(),
            oldStatus != null ? oldStatus.name() : null,
            OrderStatus.WAITING_CLIENT_APPROVAL.name(),
            "Sent design for client approval",
            workOrder.getCompany());
    }

    public void assignWorker(Long orderId, Long userId) {
        WorkOrder workOrder = getWorkOrderOrThrow(orderId);

        String oldAssigned = workOrder.getAssignedTo() != null ? workOrder.getAssignedTo().getFullName() : "Unassigned";
        Long previousAssignedId = workOrder.getAssignedTo() != null ? workOrder.getAssignedTo().getId() : null;
        User assignedTo = null;
        if (userId != null) {
            assignedTo = userRepository.findByIdAndCompany_Id(userId, tenantGuard.requireCompanyId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        }

        workOrder.setAssignedTo(assignedTo);
        workOrder.setUpdatedAt(LocalDateTime.now());
        workOrderRepository.save(workOrder);
        String newAssigned = assignedTo != null ? assignedTo.getFullName() : "Unassigned";
        auditLogService.log(AuditAction.UPDATE, "WorkOrder", workOrder.getId(),
            oldAssigned, newAssigned, "Order assignment updated", workOrder.getCompany());
        if (assignedTo != null && (previousAssignedId == null || !assignedTo.getId().equals(previousAssignedId))) {
            notificationService.sendOrderAssignedNotification(workOrder, assignedTo);
        }
    }
    
    public long countByStatus(OrderStatus status) {
        if (tenantGuard.isSuperAdmin()) {
            return workOrderRepository.countByStatus(status);
        }
        return workOrderRepository.countByStatusAndCompanyId(tenantGuard.requireCompanyId(), status);
    }

    private String orderStatusName(OrderStatus status) {
        return status != null ? status.name() : "UNKNOWN";
    }

    private String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new RuntimeException("Work order title is required");
        }
        return title.trim();
    }

    private Long requireClientId(Long clientId) {
        if (clientId == null) {
            throw new RuntimeException("Client is required");
        }
        return clientId;
    }

    private String buildReorderReason(String sourceOrderNumber, String sourceLabel) {
        String base = "Reordered from WorkOrder #" + sourceOrderNumber;
        if (sourceLabel == null || sourceLabel.isBlank()) {
            return base;
        }
        return base + " (" + sourceLabel.trim() + ")";
    }

    private User resolveCreatedByForClone(Long createdById, Long companyId) {
        return userRepository.findByIdAndCompany_Id(createdById, companyId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private WorkOrder getPublicWorkOrderOrThrow(String token) {
        String normalizedToken = normalizeTokenOrThrow(token);
        WorkOrder workOrder = workOrderRepository
            .findWithClientAndCompanyByPublicToken(normalizedToken)
            .orElseThrow(this::workOrderNotFound);
        if (workOrder.getPublicTokenExpiresAt() == null) {
            workOrder.setPublicTokenExpiresAt(publicTokenService.expiresAtFromNow());
            workOrderRepository.save(workOrder);
        }
        if (publicTokenService.isExpired(workOrder.getPublicTokenExpiresAt())) {
            throw workOrderNotFound();
        }
        return workOrder;
    }

    private String normalizeTokenOrThrow(String token) {
        if (token == null || token.isBlank()) {
            throw workOrderNotFound();
        }
        return token.trim();
    }

    private RuntimeException workOrderNotFound() {
        return new RuntimeException("Work order not found");
    }
    
    public Page<WorkOrderDTO> getUnassignedWorkOrders(Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            Page<WorkOrder> unassignedOrders = workOrderRepository.findByAssignedToIsNull(pageable);
            return unassignedOrders.map(this::convertToDTO);
        }
        Long companyId = tenantGuard.requireCompanyId();
        Page<WorkOrder> unassignedOrders = workOrderRepository.findByAssignedToIsNullAndCompany_Id(companyId, pageable);
        return unassignedOrders.map(this::convertToDTO);
    }

    public boolean isTaskAssignedToUser(Long workOrderId, Long userId) {
        return workOrderRepository.existsByIdAndAssignedToId(workOrderId, userId);
    }

    public Page<WorkOrderDTO> getWorkOrdersByAssignedUser(Long userId, Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            Page<WorkOrder> orders = workOrderRepository.findByAssignedToId(userId, pageable);
            return orders.map(this::convertToDTO);
        }
        Long companyId = tenantGuard.requireCompanyId();
        Page<WorkOrder> orders = workOrderRepository.findByAssignedToIdAndCompany_Id(userId, companyId, pageable);
        return orders.map(this::convertToDTO);
    }
    
    private WorkOrderDTO convertToDTO(WorkOrder workOrder) {
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(workOrder.getId());
        dto.setOrderNumber(workOrder.getOrderNumber());
        dto.setTitle(workOrder.getTitle());
        dto.setDescription(workOrder.getDescription());
        dto.setSpecifications(workOrder.getSpecifications());
        dto.setStatus(workOrder.getStatus());
        dto.setPriority(workOrder.getPriority());
        dto.setDeadline(workOrder.getDeadline());
        dto.setEstimatedHours(workOrder.getEstimatedHours());
        dto.setActualHours(workOrder.getActualHours());
        dto.setPrice(workOrder.getPrice());
        dto.setCost(workOrder.getCost());
        dto.setPaid(workOrder.getPaid());
        dto.setPublicToken(workOrder.getPublicToken());
        dto.setDesignApproved(workOrder.getDesignApproved());
        dto.setClientComment(workOrder.getClientComment());
        dto.setInternalNotes(workOrder.getInternalNotes());
        dto.setDeliveryType(workOrder.getDeliveryType());
        dto.setCourierName(workOrder.getCourierName());
        dto.setTrackingNumber(workOrder.getTrackingNumber());
        dto.setDeliveryAddress(workOrder.getDeliveryAddress());
        dto.setDeliveryDate(workOrder.getDeliveryDate());
        dto.setPrintType(workOrder.getPrintType());
        dto.setCreatedAt(workOrder.getCreatedAt());
        dto.setUpdatedAt(workOrder.getUpdatedAt());
        dto.setCompletedAt(workOrder.getCompletedAt());
        
        // Client info
        if (workOrder.getClient() != null) {
            dto.setClientId(workOrder.getClient().getId());
            dto.setClientName(workOrder.getClient().getCompanyName());
            dto.setClientEmail(workOrder.getClient().getEmail());
            dto.setClientPhone(workOrder.getClient().getPhone());
        }
        
        // Assigned user info
        if (workOrder.getAssignedTo() != null) {
            dto.setAssignedToId(workOrder.getAssignedTo().getId());
            dto.setAssignedToName(workOrder.getAssignedTo().getFullName());
        }
        
        // Created by info
        if (workOrder.getCreatedBy() != null) {
            dto.setCreatedById(workOrder.getCreatedBy().getId());
            dto.setCreatedByName(workOrder.getCreatedBy().getFullName());
        }
        
        // Attachment counts intentionally omitted to avoid N+1; compute on demand when needed.
        
        return dto;
    }

    private void validateOrderStatusTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        if (newStatus == null || oldStatus == newStatus) {
            return;
        }
        if (oldStatus == null) {
            return;
        }
        if (oldStatus == OrderStatus.COMPLETED || oldStatus == OrderStatus.CANCELLED) {
            throw new RuntimeException("Cannot change status from terminal state: " + oldStatus);
        }

        java.util.Map<OrderStatus, java.util.Set<OrderStatus>> allowed = java.util.Map.of(
            OrderStatus.NEW, java.util.Set.of(OrderStatus.IN_DESIGN, OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED),
            OrderStatus.IN_PROGRESS, java.util.Set.of(OrderStatus.IN_DESIGN, OrderStatus.WAITING_CLIENT_APPROVAL, OrderStatus.APPROVED_FOR_PRINT, OrderStatus.CANCELLED),
            OrderStatus.IN_DESIGN, java.util.Set.of(OrderStatus.WAITING_CLIENT_APPROVAL, OrderStatus.APPROVED_FOR_PRINT, OrderStatus.CANCELLED),
            OrderStatus.WAITING_CLIENT_APPROVAL, java.util.Set.of(OrderStatus.IN_DESIGN, OrderStatus.APPROVED_FOR_PRINT, OrderStatus.CANCELLED),
            OrderStatus.APPROVED_FOR_PRINT, java.util.Set.of(OrderStatus.IN_PRINT, OrderStatus.CANCELLED),
            OrderStatus.IN_PRINT, java.util.Set.of(OrderStatus.WAITING_QUALITY_CHECK, OrderStatus.CANCELLED),
            OrderStatus.WAITING_QUALITY_CHECK, java.util.Set.of(OrderStatus.READY_FOR_DELIVERY, OrderStatus.IN_PRINT, OrderStatus.CANCELLED),
            OrderStatus.READY_FOR_DELIVERY, java.util.Set.of(OrderStatus.SENT, OrderStatus.COMPLETED, OrderStatus.CANCELLED),
            OrderStatus.SENT, java.util.Set.of(OrderStatus.COMPLETED)
        );

        java.util.Set<OrderStatus> allowedNext = allowed.get(oldStatus);
        if (allowedNext == null || !allowedNext.contains(newStatus)) {
            throw new RuntimeException("Invalid status transition: " + oldStatus + " -> " + newStatus);
        }
    }

    private WorkOrder getWorkOrderOrThrow(Long id) {
        WorkOrder workOrder = workOrderRepository.findByIdAndCompany_Id(id, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Work order not found"));
        return workOrder;
    }

    private WorkOrder getWorkOrderWithRelationsOrThrow(Long id) {
        WorkOrder workOrder = workOrderRepository.findWithRelationsByIdAndCompany_Id(id, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Work order not found"));
        return workOrder;
    }

    private Client getClientOrThrow(Long clientId, Long companyId) {
        Client client = clientRepository.findByIdAndCompany_Id(clientId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        return client;
    }
    public List<WorkOrderDTO> getUnassignedWorkOrders() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WorkOrderDTO> page = getUnassignedWorkOrders(pageable);
        return page.getContent();
    }

	
}
