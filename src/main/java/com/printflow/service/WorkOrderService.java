package com.printflow.service;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.Client;
import com.printflow.entity.User;
import com.printflow.entity.Attachment;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.AttachmentRepository;
import com.printflow.util.OrderNumberGenerator;
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
    private final FileStorageService fileStorageService;
    
    public WorkOrderService(WorkOrderRepository workOrderRepository, 
                          ClientRepository clientRepository,
                          UserRepository userRepository, 
                          AttachmentRepository attachmentRepository,
                          OrderNumberGenerator orderNumberGenerator, 
                          FileStorageService fileStorageService) {
        this.workOrderRepository = workOrderRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.fileStorageService = fileStorageService;
    }

    public WorkOrderDTO createWorkOrder(WorkOrderDTO workOrderDTO) {
        Client client = clientRepository.findById(workOrderDTO.getClientId())
            .orElseThrow(() -> new RuntimeException("Client not found"));
        
        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber(orderNumberGenerator.generateOrderNumber());
        workOrder.setTitle(workOrderDTO.getTitle());
        workOrder.setDescription(workOrderDTO.getDescription());
        workOrder.setSpecifications(workOrderDTO.getSpecifications());
        workOrder.setStatus(OrderStatus.NEW);
        workOrder.setPriority(workOrderDTO.getPriority() != null ? workOrderDTO.getPriority() : 5);
        workOrder.setDeadline(workOrderDTO.getDeadline());
        workOrder.setEstimatedHours(workOrderDTO.getEstimatedHours());
        workOrder.setPrice(workOrderDTO.getPrice());
        workOrder.setClient(client);
        
        if (workOrderDTO.getAssignedToId() != null) {
            User assignedTo = userRepository.findById(workOrderDTO.getAssignedToId())
                .orElse(null);
            workOrder.setAssignedTo(assignedTo);
        }
        
        if (workOrderDTO.getCreatedById() != null) {
            User createdBy = userRepository.findById(workOrderDTO.getCreatedById())
                .orElse(null);
            workOrder.setCreatedBy(createdBy);
        }
        
        WorkOrder savedOrder = workOrderRepository.save(workOrder);
        return convertToDTO(savedOrder);
    }
    
    public WorkOrderDTO updateWorkOrder(Long id, WorkOrderDTO workOrderDTO) {
        WorkOrder workOrder = workOrderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Work order not found"));
        
        workOrder.setTitle(workOrderDTO.getTitle());
        workOrder.setDescription(workOrderDTO.getDescription());
        workOrder.setSpecifications(workOrderDTO.getSpecifications());
        workOrder.setPriority(workOrderDTO.getPriority());
        workOrder.setDeadline(workOrderDTO.getDeadline());
        workOrder.setEstimatedHours(workOrderDTO.getEstimatedHours());
        workOrder.setActualHours(workOrderDTO.getActualHours());
        workOrder.setPrice(workOrderDTO.getPrice());
        workOrder.setPaid(workOrderDTO.getPaid());
        workOrder.setInternalNotes(workOrderDTO.getInternalNotes());
        workOrder.setDeliveryType(workOrderDTO.getDeliveryType());
        workOrder.setCourierName(workOrderDTO.getCourierName());
        workOrder.setTrackingNumber(workOrderDTO.getTrackingNumber());
        workOrder.setDeliveryAddress(workOrderDTO.getDeliveryAddress());
        workOrder.setDeliveryDate(workOrderDTO.getDeliveryDate());
        
        if (workOrderDTO.getAssignedToId() != null) {
            User assignedTo = userRepository.findById(workOrderDTO.getAssignedToId())
                .orElse(null);
            workOrder.setAssignedTo(assignedTo);
        }
        
        if (workOrderDTO.getStatus() != null) {
            workOrder.setStatus(workOrderDTO.getStatus());
        }
        
        WorkOrder updatedOrder = workOrderRepository.save(workOrder);
        return convertToDTO(updatedOrder);
    }
    
    public WorkOrderDTO updateWorkOrderStatus(Long id, OrderStatus status, String notes) {
        WorkOrder workOrder = workOrderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Work order not found"));
        
        OrderStatus oldStatus = workOrder.getStatus();
        workOrder.setStatus(status);
        
        if (notes != null && !notes.trim().isEmpty()) {
            workOrder.setInternalNotes(
                (workOrder.getInternalNotes() != null ? workOrder.getInternalNotes() + "\n" : "") +
                LocalDateTime.now() + ": Status changed from " + oldStatus + " to " + status + 
                (notes != null ? " - " + notes : "")
            );
        }
        
        if (status == OrderStatus.COMPLETED) {
            workOrder.setCompletedAt(LocalDateTime.now());
        }
        
        WorkOrder updatedOrder = workOrderRepository.save(workOrder);
        return convertToDTO(updatedOrder);
    }
    
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Work order not found"));
        
        // Prvo obrišimo sve attachment-e
        attachmentRepository.deleteByWorkOrderId(id);
        
        // Onda obrišemo work order
        workOrderRepository.delete(workOrder);
    }
    
    public WorkOrderDTO getWorkOrderById(Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Work order not found"));
        return convertToDTO(workOrder);
    }
    
    public WorkOrderDTO getWorkOrderByPublicToken(String token) {
        WorkOrder workOrder = workOrderRepository.findByPublicToken(token)
            .orElseThrow(() -> new RuntimeException("Work order not found"));
        return convertToDTO(workOrder);
    }
    
    public List<WorkOrderDTO> getAllWorkOrders() {
        return workOrderRepository.findAll().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> getWorkOrdersByStatus(OrderStatus status) {
        return workOrderRepository.findByStatus(status).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> getWorkOrdersByClient(Long clientId) {
        return workOrderRepository.findByClientId(clientId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> getWorkOrdersByAssignedUser(Long userId) {
        return workOrderRepository.findByAssignedToId(userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> searchWorkOrders(String query) {
        return workOrderRepository.search(query).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<WorkOrderDTO> getOverdueOrders() {
        List<OrderStatus> excludedStatuses = List.of(
            OrderStatus.COMPLETED, 
            OrderStatus.CANCELLED,
            OrderStatus.SENT
        );
        
        return workOrderRepository.findOverdueOrders(
            LocalDateTime.now(), 
            excludedStatuses
        ).stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
    }
    
    public WorkOrderDTO approveDesign(Long orderId, boolean approved, String comment) {
        WorkOrder workOrder = workOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Work order not found"));
        
        workOrder.setDesignApproved(approved);
        workOrder.setClientComment(comment);
        
        if (approved) {
            workOrder.setStatus(OrderStatus.APPROVED_FOR_PRINT);
        } else {
            workOrder.setStatus(OrderStatus.IN_DESIGN);
        }
        
        WorkOrder updatedOrder = workOrderRepository.save(workOrder);
        return convertToDTO(updatedOrder);
    }
    
    public long countByStatus(OrderStatus status) {
        return workOrderRepository.countByStatus(status);
    }
    
    public Page<WorkOrderDTO> getUnassignedWorkOrders(Pageable pageable) {
        Page<WorkOrder> unassignedOrders = workOrderRepository.findByAssignedToIsNull(pageable);
        return unassignedOrders.map(this::convertToDTO);
    }

    public boolean isTaskAssignedToUser(Long workOrderId, Long userId) {
        return workOrderRepository.existsByIdAndAssignedToId(workOrderId, userId);
    }

    public List<WorkOrderDTO> getRecentWorkOrders(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WorkOrder> recentOrdersPage = workOrderRepository.findByOrderByCreatedAtDesc(pageable);
        return recentOrdersPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Page<WorkOrderDTO> getWorkOrdersByAssignedUser(Long userId, Pageable pageable) {
        Page<WorkOrder> orders = workOrderRepository.findByAssignedToId(userId, pageable);
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
        dto.setCreatedAt(workOrder.getCreatedAt());
        dto.setUpdatedAt(workOrder.getUpdatedAt());
        dto.setCompletedAt(workOrder.getCompletedAt());
        
        // Client info
        if (workOrder.getClient() != null) {
            dto.setClientId(workOrder.getClient().getId());
            dto.setClientName(workOrder.getClient().getCompanyName());
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
        
        // Attachment counts
        List<Attachment> attachments = attachmentRepository.findByWorkOrder(workOrder);
        dto.setTotalAttachments(attachments.size());
        dto.setDesignAttachments((int) attachments.stream()
            .filter(a -> a.getAttachmentType() == AttachmentType.DESIGN_SOURCE || 
                        a.getAttachmentType() == AttachmentType.DESIGN_PREVIEW)
            .count());
        dto.setProofAttachments((int) attachments.stream()
            .filter(a -> a.getAttachmentType() == AttachmentType.PROOF_OF_WORK)
            .count());
        
        return dto;
    }
    public List<WorkOrderDTO> getUnassignedWorkOrders() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WorkOrderDTO> page = getUnassignedWorkOrders(pageable);
        return page.getContent();
    }

	
}