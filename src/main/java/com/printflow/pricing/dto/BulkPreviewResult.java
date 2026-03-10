package com.printflow.pricing.dto;

import java.util.ArrayList;
import java.util.List;

public class BulkPreviewResult {
    private int componentCount;
    private int variantCount;
    private List<BulkComponentPreview> components = new ArrayList<>();
    private List<BulkVariantPreview> variants = new ArrayList<>();

    public int getComponentCount() {
        return componentCount;
    }

    public void setComponentCount(int componentCount) {
        this.componentCount = componentCount;
    }

    public int getVariantCount() {
        return variantCount;
    }

    public void setVariantCount(int variantCount) {
        this.variantCount = variantCount;
    }

    public List<BulkComponentPreview> getComponents() {
        return components;
    }

    public void setComponents(List<BulkComponentPreview> components) {
        this.components = components;
    }

    public List<BulkVariantPreview> getVariants() {
        return variants;
    }

    public void setVariants(List<BulkVariantPreview> variants) {
        this.variants = variants;
    }
}
