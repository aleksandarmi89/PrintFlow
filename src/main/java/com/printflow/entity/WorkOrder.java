package com.printflow.entity;

import com.printflow.entity.enums.OrderStatus;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_orders")
@Data
public class WorkOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String specifications;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;
    
    @Column(name = "priority")
    private Integer priority = 5; // 1-10, 10 je najviši
    
    @Column(name = "deadline")
    private java.time.LocalDateTime deadline;
    
    @Column(name = "estimated_hours")
    private Double estimatedHours;
    
    @Column(name = "actual_hours")
    private Double actualHours;
    
    @Column(name = "price")
    private Double price;
    
    @Column(name = "paid")
    private Boolean paid = false;
    
    @Column(name = "public_token", unique = true)
    private String publicToken = UUID.randomUUID().toString();
    
    @Column(name = "design_approved")
    private Boolean designApproved = false;
    
    @Column(name = "client_comment", columnDefinition = "TEXT")
    private String clientComment;
    
    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type")
    private DeliveryType deliveryType = DeliveryType.PICKUP;
    
    @Column(name = "courier_name")
    private String courierName;
    
    @Column(name = "tracking_number")
    private String trackingNumber;
    
    @Column(name = "delivery_address")
    private String deliveryAddress;
    
    @Column(name = "delivery_date")
    private java.time.LocalDateTime deliveryDate;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
    
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();
    
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
    
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();
    
    @Column(name = "completed_at")
    private java.time.LocalDateTime completedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
        if (orderNumber == null) {
            orderNumber = "ORD-" + System.currentTimeMillis();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
        if (status == OrderStatus.COMPLETED && completedAt == null) {
            completedAt = java.time.LocalDateTime.now();
        }
    }

    
    public WorkOrder() {
		
	}

	public WorkOrder(Long id, String orderNumber, String title, String description, String specifications,
			OrderStatus status, Integer priority, LocalDateTime deadline, Double estimatedHours, Double actualHours,
			Double price, Boolean paid, String publicToken, Boolean designApproved, String clientComment,
			String internalNotes, DeliveryType deliveryType, String courierName, String trackingNumber,
			String deliveryAddress, LocalDateTime deliveryDate, Client client, User assignedTo, User createdBy,
			List<Attachment> attachments, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime completedAt) {
		
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
		this.client = client;
		this.assignedTo = assignedTo;
		this.createdBy = createdBy;
		this.attachments = attachments;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.completedAt = completedAt;
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

	public java.time.LocalDateTime getDeadline() {
		return deadline;
	}

	public void setDeadline(java.time.LocalDateTime deadline) {
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

	public java.time.LocalDateTime getDeliveryDate() {
		return deliveryDate;
	}

	public void setDeliveryDate(java.time.LocalDateTime deliveryDate) {
		this.deliveryDate = deliveryDate;
	}

	public Client getClient() {
		return client;
	}

	public void setClient(Client client) {
		this.client = client;
	}

	public User getAssignedTo() {
		return assignedTo;
	}

	public void setAssignedTo(User assignedTo) {
		this.assignedTo = assignedTo;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
	}

	public List<Attachment> getAttachments() {
		return attachments;
	}

	public void setAttachments(List<Attachment> attachments) {
		this.attachments = attachments;
	}

	public java.time.LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(java.time.LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public java.time.LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public java.time.LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(java.time.LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}


	public enum DeliveryType {
        PICKUP,
        EXPRESS_POST,
        COURIER
    }
}