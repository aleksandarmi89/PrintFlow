package com.printflow.pricing.dto;

public class PricingVariantRequirementsResponse {
    private Long variantId;
    private String variantName;
    private String unitType;
    private boolean requiresDimensions;
    private boolean requiresColors;
    private boolean requiresSides;
    private boolean requiresMeters;
    private boolean requiresMinutes;

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public boolean isRequiresDimensions() {
        return requiresDimensions;
    }

    public void setRequiresDimensions(boolean requiresDimensions) {
        this.requiresDimensions = requiresDimensions;
    }

    public boolean isRequiresColors() {
        return requiresColors;
    }

    public void setRequiresColors(boolean requiresColors) {
        this.requiresColors = requiresColors;
    }

    public boolean isRequiresSides() {
        return requiresSides;
    }

    public void setRequiresSides(boolean requiresSides) {
        this.requiresSides = requiresSides;
    }

    public boolean isRequiresMeters() {
        return requiresMeters;
    }

    public void setRequiresMeters(boolean requiresMeters) {
        this.requiresMeters = requiresMeters;
    }

    public boolean isRequiresMinutes() {
        return requiresMinutes;
    }

    public void setRequiresMinutes(boolean requiresMinutes) {
        this.requiresMinutes = requiresMinutes;
    }
}
