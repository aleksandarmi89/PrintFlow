package com.printflow.pricing.dto;

import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import com.printflow.entity.enums.ProductCategory;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class BulkCategoryPricingUpdateRequest {

    private ProductCategory category;

    private boolean applyAllCategories;

    private PricingComponentType componentType;

    private PricingModel pricingModel;

    private BigDecimal amountPercent;

    private BigDecimal setMarkupPercent;

    private BigDecimal setWastePercent;

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public boolean isApplyAllCategories() {
        return applyAllCategories;
    }

    public void setApplyAllCategories(boolean applyAllCategories) {
        this.applyAllCategories = applyAllCategories;
    }

    public PricingComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(PricingComponentType componentType) {
        this.componentType = componentType;
    }

    public PricingModel getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(PricingModel pricingModel) {
        this.pricingModel = pricingModel;
    }

    public BigDecimal getAmountPercent() {
        return amountPercent;
    }

    public void setAmountPercent(BigDecimal amountPercent) {
        this.amountPercent = amountPercent;
    }

    public BigDecimal getSetMarkupPercent() {
        return setMarkupPercent;
    }

    public void setSetMarkupPercent(BigDecimal setMarkupPercent) {
        this.setMarkupPercent = setMarkupPercent;
    }

    public BigDecimal getSetWastePercent() {
        return setWastePercent;
    }

    public void setSetWastePercent(BigDecimal setWastePercent) {
        this.setWastePercent = setWastePercent;
    }
}
