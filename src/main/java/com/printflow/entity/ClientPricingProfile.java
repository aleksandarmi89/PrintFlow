package com.printflow.entity;

import com.printflow.pricing.entity.ProductVariant;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_pricing_profiles")
@Filter(name = "tenantFilter", condition = "tenant_id = :companyId")
public class ClientPricingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Company company;

    @Column(name = "last_price", precision = 12, scale = 2)
    private BigDecimal lastPrice;

    @Column(name = "average_price", precision = 12, scale = 2)
    private BigDecimal averagePrice;

    @Column(name = "discount_percent", precision = 6, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "last_ordered_at")
    private LocalDateTime lastOrderedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(BigDecimal discountPercent) {
        this.discountPercent = discountPercent;
    }

    public LocalDateTime getLastOrderedAt() {
        return lastOrderedAt;
    }

    public void setLastOrderedAt(LocalDateTime lastOrderedAt) {
        this.lastOrderedAt = lastOrderedAt;
    }
}
