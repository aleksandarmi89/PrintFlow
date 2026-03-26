package com.printflow.pricing.dto;

import java.math.BigDecimal;

public class WorkOrderItemResponse {
    private Long id;
    private Long workOrderId;
    private Long variantId;
    private BigDecimal quantity;
    private BigDecimal calculatedCost;
    private BigDecimal calculatedPrice;
    private BigDecimal marginPercent;
    private BigDecimal workOrderTotalCost;
    private BigDecimal workOrderTotalPrice;
    private String currency;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
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

    public BigDecimal getMarginPercent() {
        return marginPercent;
    }

    public void setMarginPercent(BigDecimal marginPercent) {
        this.marginPercent = marginPercent;
    }

    public BigDecimal getWorkOrderTotalCost() {
        return workOrderTotalCost;
    }

    public void setWorkOrderTotalCost(BigDecimal workOrderTotalCost) {
        this.workOrderTotalCost = workOrderTotalCost;
    }

    public BigDecimal getWorkOrderTotalPrice() {
        return workOrderTotalPrice;
    }

    public void setWorkOrderTotalPrice(BigDecimal workOrderTotalPrice) {
        this.workOrderTotalPrice = workOrderTotalPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
