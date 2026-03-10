package com.printflow.pricing.dto;

import com.printflow.pricing.entity.ProductVariant;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class UpdateVariantPricingRequest {

    @NotNull
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "1000.00")
    private BigDecimal defaultMarkupPercent;

    @DecimalMin(value = "0.00")
    private BigDecimal minPrice;

    @NotNull
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal wastePercent;

    public static UpdateVariantPricingRequest fromVariant(ProductVariant variant) {
        UpdateVariantPricingRequest request = new UpdateVariantPricingRequest();
        request.setDefaultMarkupPercent(variant.getDefaultMarkupPercent());
        request.setMinPrice(variant.getMinPrice());
        request.setWastePercent(variant.getWastePercent());
        return request;
    }

    public BigDecimal getDefaultMarkupPercent() {
        return defaultMarkupPercent;
    }

    public void setDefaultMarkupPercent(BigDecimal defaultMarkupPercent) {
        this.defaultMarkupPercent = defaultMarkupPercent;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getWastePercent() {
        return wastePercent;
    }

    public void setWastePercent(BigDecimal wastePercent) {
        this.wastePercent = wastePercent;
    }
}
