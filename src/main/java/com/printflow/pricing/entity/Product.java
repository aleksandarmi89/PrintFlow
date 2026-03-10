package com.printflow.pricing.entity;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSource;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.UnitType;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_products_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_products_tenant_name", columnList = "tenant_id,name"),
        @Index(name = "idx_products_tenant_external_id", columnList = "tenant_id,external_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_products_tenant_sku", columnNames = {"tenant_id", "sku"})
    }
)
@Filter(name = "tenantFilter", condition = "tenant_id = :companyId")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(length = 120)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_label", length = 120)
    private String categoryLabel;

    @Column(name = "unit_label", length = 80)
    private String unitLabel;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "RSD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductSource source = ProductSource.MANUAL;

    @Column(name = "external_id", length = 191)
    private String externalId;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 50)
    private UnitType unitType;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (currency == null || currency.isBlank()) {
            currency = "RSD";
        }
        if (basePrice == null) {
            basePrice = BigDecimal.ZERO;
        }
        if (source == null) {
            source = ProductSource.MANUAL;
        }
        if (category == null) {
            category = ProductCategory.OTHER;
        }
        if (unitType == null) {
            unitType = UnitType.PIECE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    public String getUnitLabel() {
        return unitLabel;
    }

    public void setUnitLabel(String unitLabel) {
        this.unitLabel = unitLabel;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public ProductSource getSource() {
        return source;
    }

    public void setSource(ProductSource source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public UnitType getUnitType() {
        return unitType;
    }

    public void setUnitType(UnitType unitType) {
        this.unitType = unitType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
}
