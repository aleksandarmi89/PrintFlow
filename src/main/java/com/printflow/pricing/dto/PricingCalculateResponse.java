package com.printflow.pricing.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PricingCalculateResponse {
    private String currency;
    private BigDecimal totalCost;
    private BigDecimal totalPrice;
    private BigDecimal profitAmount;
    private BigDecimal pricePerUnit;
    private BigDecimal marginPercent;
    private BigDecimal markupPercent;
    private BigDecimal recommendedPrice;
    private BigDecimal minimumPrice;
    private BigDecimal targetMarginPrice;
    private List<BreakdownRow> breakdown = new ArrayList<>();
    private List<String> appliedRules = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public BigDecimal getProfitAmount() {
        return profitAmount;
    }

    public void setProfitAmount(BigDecimal profitAmount) {
        this.profitAmount = profitAmount;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }

    public BigDecimal getMarginPercent() {
        return marginPercent;
    }

    public void setMarginPercent(BigDecimal marginPercent) {
        this.marginPercent = marginPercent;
    }

    public BigDecimal getMarkupPercent() {
        return markupPercent;
    }

    public void setMarkupPercent(BigDecimal markupPercent) {
        this.markupPercent = markupPercent;
    }

    public BigDecimal getRecommendedPrice() {
        return recommendedPrice;
    }

    public void setRecommendedPrice(BigDecimal recommendedPrice) {
        this.recommendedPrice = recommendedPrice;
    }

    public BigDecimal getMinimumPrice() {
        return minimumPrice;
    }

    public void setMinimumPrice(BigDecimal minimumPrice) {
        this.minimumPrice = minimumPrice;
    }

    public BigDecimal getTargetMarginPrice() {
        return targetMarginPrice;
    }

    public void setTargetMarginPrice(BigDecimal targetMarginPrice) {
        this.targetMarginPrice = targetMarginPrice;
    }

    public List<BreakdownRow> getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(List<BreakdownRow> breakdown) {
        this.breakdown = breakdown;
    }

    public List<String> getAppliedRules() {
        return appliedRules;
    }

    public void setAppliedRules(List<String> appliedRules) {
        this.appliedRules = appliedRules;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
