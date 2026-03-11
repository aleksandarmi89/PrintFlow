package com.printflow.dto;

import com.printflow.entity.WorkOrder.DeliveryType;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.PrintType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkOrderDTO {
    private Long id;
    private String orderNumber;
    private String title;
    private String description;
    private String specifications;
    private OrderStatus status;
    private Integer priority;
    private LocalDateTime deadline;
    private Double estimatedHours;
    private Double actualHours;
    private Double price;
    private Double cost;
    private Boolean paid;
    private String publicToken;
    private Boolean designApproved;
    private String clientComment;
    private String internalNotes;
    private DeliveryType deliveryType;
    private String courierName;
    private String trackingNumber;
    private String deliveryAddress;
    private LocalDateTime deliveryDate;
    private PrintType printType;
    
    // Relacije
    private Long clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    private Long assignedToId;
    private String assignedToName;
    private Long createdById;
    private String createdByName;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    
    // Attachment counts
    private int totalAttachments;
    private int designAttachments;
    private int proofAttachments;
    
    
    
	public WorkOrderDTO() {
		
	}
	public WorkOrderDTO(Long id, String orderNumber, String title, String description, String specifications,
			OrderStatus status, Integer priority, LocalDateTime deadline, Double estimatedHours, Double actualHours,
			Double price, Double cost, Boolean paid, String publicToken, Boolean designApproved, String clientComment,
			String internalNotes, DeliveryType deliveryType, String courierName, String trackingNumber,
			String deliveryAddress, LocalDateTime deliveryDate, PrintType printType, Long clientId, String clientName, Long assignedToId,
			String assignedToName, Long createdById, String createdByName, LocalDateTime createdAt,
			LocalDateTime updatedAt, LocalDateTime completedAt, int totalAttachments, int designAttachments,
			int proofAttachments) {
		
		this.id = id;
		this.orderNumber = orderNumber;
		this.title = title;
		this.description = description;
		this.specifications = specifications;
		this.status = status;
		this.priority = priority;
		this.deadline = deadline;
		this.estimatedHours = estimatedHours;
		this.actualHours = actualHours;
		this.price = price;
		this.cost = cost;
		this.cost = cost;
		this.paid = paid;
		this.publicToken = publicToken;
		this.designApproved = designApproved;
		this.clientComment = clientComment;
		this.internalNotes = internalNotes;
		this.deliveryType = deliveryType;
		this.courierName = courierName;
		this.trackingNumber = trackingNumber;
		this.deliveryAddress = deliveryAddress;
		this.deliveryDate = deliveryDate;
		this.printType = printType;
		this.clientId = clientId;
		this.clientName = clientName;
		this.assignedToId = assignedToId;
		this.assignedToName = assignedToName;
		this.createdById = createdById;
		this.createdByName = createdByName;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.completedAt = completedAt;
		this.totalAttachments = totalAttachments;
		this.designAttachments = designAttachments;
		this.proofAttachments = proofAttachments;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getOrderNumber() {
		return orderNumber;
	}
	public void setOrderNumber(String orderNumber) {
		this.orderNumber = orderNumber;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getSpecifications() {
		return specifications;
	}
	public void setSpecifications(String specifications) {
		this.specifications = specifications;
	}
	public OrderStatus getStatus() {
		return status;
	}
	public void setStatus(OrderStatus status) {
		this.status = status;
	}
	public Integer getPriority() {
		return priority;
	}
	public void setPriority(Integer priority) {
		this.priority = priority;
	}
	public LocalDateTime getDeadline() {
		return deadline;
	}
	public void setDeadline(LocalDateTime deadline) {
		this.deadline = deadline;
	}
	public Double getEstimatedHours() {
		return estimatedHours;
	}
	public void setEstimatedHours(Double estimatedHours) {
		this.estimatedHours = estimatedHours;
	}
	public Double getActualHours() {
		return actualHours;
	}
	public void setActualHours(Double actualHours) {
		this.actualHours = actualHours;
	}
	public Double getPrice() {
		return price;
	}
	public void setPrice(Double price) {
		this.price = price;
	}
	public Double getCost() {
		return cost;
	}
	public void setCost(Double cost) {
		this.cost = cost;
	}
	public Boolean getPaid() {
		return paid;
	}
	public void setPaid(Boolean paid) {
		this.paid = paid;
	}
	public String getPublicToken() {
		return publicToken;
	}
	public void setPublicToken(String publicToken) {
		this.publicToken = publicToken;
	}
	public Boolean getDesignApproved() {
		return designApproved;
	}
	public void setDesignApproved(Boolean designApproved) {
		this.designApproved = designApproved;
	}
	public String getClientComment() {
		return clientComment;
	}
	public void setClientComment(String clientComment) {
		this.clientComment = clientComment;
	}
	public String getInternalNotes() {
		return internalNotes;
	}
	public void setInternalNotes(String internalNotes) {
		this.internalNotes = internalNotes;
	}
	public DeliveryType getDeliveryType() {
		return deliveryType;
	}
	public void setDeliveryType(DeliveryType deliveryType) {
		this.deliveryType = deliveryType;
	}
	public String getCourierName() {
		return courierName;
	}
	public void setCourierName(String courierName) {
		this.courierName = courierName;
	}
	public String getTrackingNumber() {
		return trackingNumber;
	}
	public void setTrackingNumber(String trackingNumber) {
		this.trackingNumber = trackingNumber;
	}
	public String getDeliveryAddress() {
		return deliveryAddress;
	}
	public void setDeliveryAddress(String deliveryAddress) {
		this.deliveryAddress = deliveryAddress;
	}
	public LocalDateTime getDeliveryDate() {
		return deliveryDate;
	}
	public void setDeliveryDate(LocalDateTime deliveryDate) {
		this.deliveryDate = deliveryDate;
	}
	public PrintType getPrintType() {
		return printType;
	}
	public void setPrintType(PrintType printType) {
		this.printType = printType;
	}
	public Long getClientId() {
		return clientId;
	}
	public void setClientId(Long clientId) {
		this.clientId = clientId;
	}
    public String getClientName() {
        return clientName;
    }
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    public String getClientEmail() {
        return clientEmail;
    }
    public void setClientEmail(String clientEmail) {
        this.clientEmail = clientEmail;
    }
    public String getClientPhone() {
        return clientPhone;
    }
    public void setClientPhone(String clientPhone) {
        this.clientPhone = clientPhone;
    }
	public Long getAssignedToId() {
		return assignedToId;
	}
	public void setAssignedToId(Long assignedToId) {
		this.assignedToId = assignedToId;
	}
	public String getAssignedToName() {
		return assignedToName;
	}
	public void setAssignedToName(String assignedToName) {
		this.assignedToName = assignedToName;
	}
	public Long getCreatedById() {
		return createdById;
	}
	public void setCreatedById(Long createdById) {
		this.createdById = createdById;
	}
	public String getCreatedByName() {
		return createdByName;
	}
    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    // Safe display helpers for templates
    public String getAssignedToNameSafe() {
        return assignedToName == null || assignedToName.isBlank() ? "Not assigned" : assignedToName;
    }

    public String getCreatedByNameSafe() {
        return createdByName == null || createdByName.isBlank() ? "System" : createdByName;
    }

    public String getPrintTypeSafe() {
        return printType != null ? printType.name() : "OTHER";
    }

    public boolean isPaidSafe() {
        return Boolean.TRUE.equals(paid);
    }

    public String getPaidLabel() {
        return isPaidSafe() ? "Paid" : "Not paid";
    }

    public String getPaidClass() {
        return isPaidSafe() ? "text-green-600" : "text-red-600";
    }

    public String getDescriptionSafe() {
        return description == null || description.isBlank() ? "No description" : description;
    }

    public String getSpecificationsSafe() {
        return specifications == null || specifications.isBlank() ? "No specifications" : specifications;
    }

    public String getInternalNotesSafe() {
        return internalNotes == null || internalNotes.isBlank() ? "No internal notes" : internalNotes;
    }

    public String getClientEmailSafe() {
        return clientEmail == null || clientEmail.isBlank() ? "No email" : clientEmail;
    }

    public String getClientPhoneSafe() {
        return clientPhone == null || clientPhone.isBlank() ? "No phone" : clientPhone;
    }
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	public LocalDateTime getCompletedAt() {
		return completedAt;
	}
	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}
	public int getTotalAttachments() {
		return totalAttachments;
	}
	public void setTotalAttachments(int totalAttachments) {
		this.totalAttachments = totalAttachments;
	}
	public int getDesignAttachments() {
		return designAttachments;
	}
	public void setDesignAttachments(int designAttachments) {
		this.designAttachments = designAttachments;
	}
	public int getProofAttachments() {
		return proofAttachments;
	}
	public void setProofAttachments(int proofAttachments) {
		this.proofAttachments = proofAttachments;
	}
    
    
}
