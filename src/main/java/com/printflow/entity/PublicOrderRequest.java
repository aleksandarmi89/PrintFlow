package com.printflow.entity;

import com.printflow.entity.enums.PublicOrderRequestSourceChannel;
import com.printflow.entity.enums.PublicOrderRequestStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "public_order_requests")
@Filter(name = "tenantFilter", condition = "tenant_id = :companyId")
public class PublicOrderRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Company company;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 190)
    private String customerEmail;

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    @Column(name = "customer_company_name", length = 190)
    private String customerCompanyName;

    @Column(name = "product_type", nullable = false, length = 150)
    private String productType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "dimensions", length = 190)
    private String dimensions;

    @Column(name = "material", length = 150)
    private String material;

    @Column(name = "finishing", length = 150)
    private String finishing;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PublicOrderRequestStatus status = PublicOrderRequestStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 40)
    private PublicOrderRequestSourceChannel sourceChannel = PublicOrderRequestSourceChannel.PUBLIC_FORM;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_order_id")
    private WorkOrder convertedOrder;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PublicOrderRequestAttachment> attachments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerCompanyName() {
        return customerCompanyName;
    }

    public void setCustomerCompanyName(String customerCompanyName) {
        this.customerCompanyName = customerCompanyName;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getFinishing() {
        return finishing;
    }

    public void setFinishing(String finishing) {
        this.finishing = finishing;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public PublicOrderRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PublicOrderRequestStatus status) {
        this.status = status;
    }

    public PublicOrderRequestSourceChannel getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(PublicOrderRequestSourceChannel sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getConvertedAt() {
        return convertedAt;
    }

    public void setConvertedAt(LocalDateTime convertedAt) {
        this.convertedAt = convertedAt;
    }

    public WorkOrder getConvertedOrder() {
        return convertedOrder;
    }

    public void setConvertedOrder(WorkOrder convertedOrder) {
        this.convertedOrder = convertedOrder;
    }

    public List<PublicOrderRequestAttachment> getAttachments() {
        return attachments;
    }
}
