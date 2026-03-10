package com.printflow.pricing.entity;

import com.printflow.entity.Company;
import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

@Entity
@Table(name = "pricing_components")
@Filter(name = "tenantFilter", condition = "tenant_id = :companyId")
public class PricingComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PricingComponentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PricingModel model;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String notes;

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

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public PricingComponentType getType() {
        return type;
    }

    public void setType(PricingComponentType type) {
        this.type = type;
    }

    public PricingModel getModel() {
        return model;
    }

    public void setModel(PricingModel model) {
        this.model = model;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
