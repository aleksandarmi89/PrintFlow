package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.pricing.dto.UpdateComponentAmountRequest;
import com.printflow.pricing.dto.UpdateVariantPricingRequest;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.service.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.pricing.dto.BulkCategoryPricingUpdateRequest;
import com.printflow.pricing.dto.BulkPreviewResult;
import com.printflow.pricing.dto.BulkComponentPreview;
import com.printflow.pricing.dto.BulkVariantPreview;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;

@Service
public class PricingAdminService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;

    public PricingAdminService(ProductRepository productRepository,
                               ProductVariantRepository variantRepository,
                               PricingComponentRepository componentRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
    }

    @Transactional
    public ProductVariant updateVariantPricing(Company company, Long variantId, UpdateVariantPricingRequest request) {
        ProductVariant variant = variantRepository.findByIdAndCompany_Id(variantId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        variant.setDefaultMarkupPercent(request.getDefaultMarkupPercent());
        variant.setWastePercent(request.getWastePercent());
        variant.setMinPrice(request.getMinPrice());
        return variantRepository.save(variant);
    }

    @Transactional
    public PricingComponent updateComponentAmount(Company company, Long componentId, UpdateComponentAmountRequest request) {
        PricingComponent component = componentRepository.findByIdAndCompany_Id(componentId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Component not found"));
        component.setAmount(request.getAmount());
        return componentRepository.save(component);
    }

    @Transactional
    public void deleteComponent(Company company, Long componentId) {
        PricingComponent component = componentRepository.findByIdAndCompany_Id(componentId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Component not found"));
        componentRepository.delete(component);
    }

    @Transactional
    public void deleteVariant(Company company, Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndCompany_Id(variantId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        componentRepository.deleteAllByVariant_IdAndCompany_Id(variant.getId(), company.getId());
        variantRepository.delete(variant);
    }

    @Transactional
    public void deleteProduct(Company company, Long productId) {
        Product product = productRepository.findByIdAndCompany_Id(productId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        List<ProductVariant> variants = variantRepository.findAllByProduct_IdAndCompany_Id(productId, company.getId());
        for (ProductVariant variant : variants) {
            componentRepository.deleteAllByVariant_IdAndCompany_Id(variant.getId(), company.getId());
        }
        variantRepository.deleteAllByProduct_IdAndCompany_Id(productId, company.getId());
        productRepository.delete(product);
    }

    @Transactional
    public int bulkUpdateCategoryPricing(Company company, BulkCategoryPricingUpdateRequest request) {
        if (request == null || (!request.isApplyAllCategories() && request.getCategory() == null)) {
            throw new IllegalArgumentException("Category is required.");
        }
        if (request.getAmountPercent() == null
            && request.getSetMarkupPercent() == null
            && request.getSetWastePercent() == null) {
            throw new IllegalArgumentException("Provide at least one change.");
        }
        if (request.getAmountPercent() != null && request.getAmountPercent().compareTo(new BigDecimal("-90")) < 0) {
            throw new IllegalArgumentException("Amount change is too low.");
        }
        if (request.getSetMarkupPercent() != null && request.getSetMarkupPercent().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Markup percent must be >= 0.");
        }
        if (request.getSetWastePercent() != null && request.getSetWastePercent().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Waste percent must be >= 0.");
        }

        List<ProductCategory> categories;
        if (request.isApplyAllCategories()) {
            categories = List.of(ProductCategory.values());
        } else {
            categories = List.of(request.getCategory());
        }
        PricingComponentType type = request.getComponentType();
        PricingModel model = request.getPricingModel();

        int updated = 0;

        if (request.getAmountPercent() != null) {
            BigDecimal percent = request.getAmountPercent();
            BigDecimal multiplier = BigDecimal.ONE.add(percent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            for (ProductCategory category : categories) {
                List<PricingComponent> components = componentRepository
                    .findAllByCompanyAndCategory(company.getId(), category, type, model);
                for (PricingComponent component : components) {
                    if (component.getAmount() == null) {
                        continue;
                    }
                    component.setAmount(component.getAmount().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
                    updated++;
                }
                componentRepository.saveAll(components);
            }
        }

        if (request.getSetMarkupPercent() != null || request.getSetWastePercent() != null) {
            for (ProductCategory category : categories) {
                List<ProductVariant> variants = variantRepository.findAllByCompany_IdAndProduct_Category(company.getId(), category);
                for (ProductVariant variant : variants) {
                    if (request.getSetMarkupPercent() != null) {
                        variant.setDefaultMarkupPercent(request.getSetMarkupPercent());
                    }
                    if (request.getSetWastePercent() != null) {
                        variant.setWastePercent(request.getSetWastePercent());
                    }
                }
                variantRepository.saveAll(variants);
            }
        }

        return updated;
    }

    @Transactional(readOnly = true)
    public BulkPreviewResult previewBulkUpdate(Company company, BulkCategoryPricingUpdateRequest request, int limit) {
        List<ProductCategory> categories;
        if (request.isApplyAllCategories()) {
            categories = List.of(ProductCategory.values());
        } else {
            categories = List.of(request.getCategory());
        }

        PricingComponentType type = request.getComponentType();
        PricingModel model = request.getPricingModel();

        List<BulkComponentPreview> componentsPreview = new ArrayList<>();
        List<BulkVariantPreview> variantsPreview = new ArrayList<>();
        int componentCount = 0;
        int variantCount = 0;

        for (ProductCategory category : categories) {
            List<PricingComponent> components = componentRepository
                .findAllByCompanyAndCategory(company.getId(), category, type, model);
            componentCount += components.size();
            for (PricingComponent component : components) {
                if (componentsPreview.size() >= limit) {
                    break;
                }
                BulkComponentPreview dto = new BulkComponentPreview();
                dto.setCategory(category.name());
                dto.setProductName(component.getVariant() != null && component.getVariant().getProduct() != null
                    ? component.getVariant().getProduct().getName() : null);
                dto.setVariantName(component.getVariant() != null ? component.getVariant().getName() : null);
                dto.setComponentType(component.getType() != null ? component.getType().name() : null);
                dto.setPricingModel(component.getModel() != null ? component.getModel().name() : null);
                dto.setAmount(component.getAmount());
                dto.setNotes(component.getNotes());
                componentsPreview.add(dto);
            }

            List<ProductVariant> variants = variantRepository.findAllByCompany_IdAndProduct_Category(company.getId(), category);
            variantCount += variants.size();
            for (ProductVariant variant : variants) {
                if (variantsPreview.size() >= limit) {
                    break;
                }
                BulkVariantPreview dto = new BulkVariantPreview();
                dto.setCategory(category.name());
                dto.setProductName(variant.getProduct() != null ? variant.getProduct().getName() : null);
                dto.setVariantName(variant.getName());
                dto.setMarkupPercent(variant.getDefaultMarkupPercent());
                dto.setWastePercent(variant.getWastePercent());
                dto.setMinPrice(variant.getMinPrice());
                variantsPreview.add(dto);
            }
        }

        BulkPreviewResult result = new BulkPreviewResult();
        result.setComponentCount(componentCount);
        result.setVariantCount(variantCount);
        result.setComponents(componentsPreview);
        result.setVariants(variantsPreview);
        return result;
    }
}
