package com.printflow.pricing.dto;

import com.printflow.entity.enums.ProductSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ProductForm {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name is too long")
    private String name;

    @Size(max = 120, message = "SKU is too long")
    private String sku;

    private String description;

    @Size(max = 120, message = "Category is too long")
    private String category;

    @Size(max = 80, message = "Unit is too long")
    private String unit;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Base price must be >= 0")
    private BigDecimal basePrice;

    @Size(max = 10, message = "Currency is too long")
    private String currency;

    private boolean active = true;

    private String externalId;

    private ProductSource source;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public ProductSource getSource() {
        return source;
    }

    public void setSource(ProductSource source) {
        this.source = source;
    }
}
