package com.printflow.pricing.service;

import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ProductPricingBridgeService {

    private static final String AUTO_VARIANT_NAME = "Standard";
    private static final String AUTO_COMPONENT_NOTE = "AUTO_BASE_PRICE_BRIDGE";

    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;

    public ProductPricingBridgeService(ProductVariantRepository variantRepository,
                                       PricingComponentRepository componentRepository) {
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
    }

    public void ensureDefaultPricingStructure(Product product) {
        if (product == null || product.getId() == null || product.getCompany() == null) {
            return;
        }
        ProductVariant variant = firstVariant(product);
        if (variant == null) {
            variant = createDefaultVariant(product);
        }
        ensureDefaultComponent(product, variant);
    }

    public void reconcileAutoBasePrice(Product product) {
        if (product == null || product.getId() == null || product.getCompany() == null) {
            return;
        }
        ProductVariant variant = firstVariant(product);
        if (variant == null) {
            ensureDefaultPricingStructure(product);
            return;
        }
        List<PricingComponent> components = componentRepository
            .findAllByVariant_IdAndCompany_Id(variant.getId(), product.getCompany().getId());
        if (components.size() != 1) {
            return;
        }
        PricingComponent only = components.get(0);
        if (!AUTO_COMPONENT_NOTE.equalsIgnoreCase(only.getNotes())) {
            return;
        }
        only.setAmount(safePrice(product.getBasePrice()));
        only.setModel(resolveModel(product.getUnitType()));
        componentRepository.save(only);
    }

    private ProductVariant firstVariant(Product product) {
        List<ProductVariant> variants = variantRepository
            .findAllByProduct_IdAndCompany_Id(product.getId(), product.getCompany().getId());
        return variants.isEmpty() ? null : variants.get(0);
    }

    private ProductVariant createDefaultVariant(Product product) {
        ProductVariant variant = new ProductVariant();
        variant.setCompany(product.getCompany());
        variant.setProduct(product);
        variant.setName(AUTO_VARIANT_NAME);
        variant.setDefaultMarkupPercent(new BigDecimal("20.00"));
        variant.setWastePercent(BigDecimal.ZERO);
        variant.setActive(product.isActive());
        return variantRepository.save(variant);
    }

    private void ensureDefaultComponent(Product product, ProductVariant variant) {
        List<PricingComponent> components = componentRepository
            .findAllByVariant_IdAndCompany_Id(variant.getId(), product.getCompany().getId());
        if (!components.isEmpty()) {
            return;
        }
        PricingComponent component = new PricingComponent();
        component.setCompany(product.getCompany());
        component.setVariant(variant);
        component.setType(PricingComponentType.MATERIAL);
        component.setModel(resolveModel(product.getUnitType()));
        component.setAmount(safePrice(product.getBasePrice()));
        component.setNotes(AUTO_COMPONENT_NOTE);
        componentRepository.save(component);
    }

    private PricingModel resolveModel(UnitType unitType) {
        if (unitType == null) {
            return PricingModel.PER_UNIT;
        }
        return switch (unitType) {
            case SQM -> PricingModel.PER_SQM;
            case METER -> PricingModel.PER_METER;
            default -> PricingModel.PER_UNIT;
        };
    }

    private BigDecimal safePrice(BigDecimal basePrice) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return basePrice;
    }
}
