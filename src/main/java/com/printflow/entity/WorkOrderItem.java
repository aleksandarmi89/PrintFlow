package com.printflow.entity;

import com.printflow.pricing.entity.ProductVariant;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_order_items")
@Filter(name = "tenantFilter", condition = "tenant_id = :companyId")
public class WorkOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant variant;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Lob
    @Column(name = "attributes_json", columnDefinition = "LONGTEXT")
    private String attributesJson;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedPrice;

    @Column(name = "profit_amount", precision = 12, scale = 2)
    private BigDecimal profitAmount;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marginPercent;

    @Column(name = "currency", length = 10)
    private String currency;

    @Lob
    @Column(name = "breakdown_json", nullable = false, columnDefinition = "LONGTEXT")
    private String breakdownJson;

    @Lob
    @Column(name = "pricing_snapshot_json", columnDefinition = "LONGTEXT")
    private String pricingSnapshotJson;

    @Column(nullable = false)
    private boolean priceLocked = true;

    @Column(name = "price_calculated_at", nullable = false)
    private LocalDateTime priceCalculatedAt;

    @Column(name = "status", length = 40)
    private String status;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Transient
    private BigDecimal originalQuantity;

    @Transient
    private Long originalVariantId;

    @Transient
    private BigDecimal originalCalculatedCost;

    @Transient
    private BigDecimal originalCalculatedPrice;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (priceCalculatedAt == null) {
            priceCalculatedAt = createdAt;
        }
        priceLocked = true;
    }

    @PostLoad
    protected void onLoad() {
        originalQuantity = quantity;
        originalVariantId = variant != null ? variant.getId() : null;
        originalCalculatedCost = calculatedCost;
        originalCalculatedPrice = calculatedPrice;
    }

    @PreUpdate
    protected void onUpdate() {
        if (priceLocked && priceChanged()) {
            throw new IllegalStateException("Price is locked");
        }
    }

    private boolean priceChanged() {
        if (originalQuantity != null && quantity != null && originalQuantity.compareTo(quantity) != 0) {
            return true;
        }
        if (originalVariantId != null && variant != null && !originalVariantId.equals(variant.getId())) {
            return true;
        }
        if (originalCalculatedCost != null && calculatedCost != null && originalCalculatedCost.compareTo(calculatedCost) != 0) {
            return true;
        }
        if (originalCalculatedPrice != null && calculatedPrice != null && originalCalculatedPrice.compareTo(calculatedPrice) != 0) {
            return true;
        }
        return false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public void setWorkOrder(WorkOrder workOrder) {
        this.workOrder = workOrder;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Integer getWidthMm() {
        return widthMm;
    }

    public void setWidthMm(Integer widthMm) {
        this.widthMm = widthMm;
    }

    public Integer getHeightMm() {
        return heightMm;
    }

    public void setHeightMm(Integer heightMm) {
        this.heightMm = heightMm;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
    }

    public BigDecimal getCalculatedCost() {
        return calculatedCost;
    }

    public void setCalculatedCost(BigDecimal calculatedCost) {
        this.calculatedCost = calculatedCost;
    }

    public BigDecimal getCalculatedPrice() {
        return calculatedPrice;
    }

    public void setCalculatedPrice(BigDecimal calculatedPrice) {
        this.calculatedPrice = calculatedPrice;
    }

    public BigDecimal getProfitAmount() {
        return profitAmount;
    }

    public void setProfitAmount(BigDecimal profitAmount) {
        this.profitAmount = profitAmount;
    }

    public BigDecimal getMarginPercent() {
        return marginPercent;
    }

    public void setMarginPercent(BigDecimal marginPercent) {
        this.marginPercent = marginPercent;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBreakdownJson() {
        return breakdownJson;
    }

    public void setBreakdownJson(String breakdownJson) {
        this.breakdownJson = breakdownJson;
    }

    public String getPricingSnapshotJson() {
        return pricingSnapshotJson;
    }

    public void setPricingSnapshotJson(String pricingSnapshotJson) {
        this.pricingSnapshotJson = pricingSnapshotJson;
    }

    public boolean isPriceLocked() {
        return priceLocked;
    }

    public void setPriceLocked(boolean priceLocked) {
        this.priceLocked = priceLocked;
    }

    public LocalDateTime getPriceCalculatedAt() {
        return priceCalculatedAt;
    }

    public void setPriceCalculatedAt(LocalDateTime priceCalculatedAt) {
        this.priceCalculatedAt = priceCalculatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
