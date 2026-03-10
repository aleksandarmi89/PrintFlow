package com.printflow.pricing.dto;

import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;

import java.math.BigDecimal;

public class BreakdownRow {
    private PricingComponentType componentType;
    private PricingModel pricingModel;
    private BigDecimal baseAmount;
    private String multiplierDescription;
    private BigDecimal computedAmount;
    private BigDecimal multiplierValue;
    private String unit;
    private BigDecimal lineCost;
    private String notes;

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

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public String getMultiplierDescription() {
        return multiplierDescription;
    }

    public void setMultiplierDescription(String multiplierDescription) {
        this.multiplierDescription = multiplierDescription;
    }

    public BigDecimal getComputedAmount() {
        return computedAmount;
    }

    public void setComputedAmount(BigDecimal computedAmount) {
        this.computedAmount = computedAmount;
    }

    public BigDecimal getMultiplierValue() {
        return multiplierValue;
    }

    public void setMultiplierValue(BigDecimal multiplierValue) {
        this.multiplierValue = multiplierValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getLineCost() {
        return lineCost;
    }

    public void setLineCost(BigDecimal lineCost) {
        this.lineCost = lineCost;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
