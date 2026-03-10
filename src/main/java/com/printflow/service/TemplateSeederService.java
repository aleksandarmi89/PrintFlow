package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TemplateSeederService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;

    public TemplateSeederService(ProductRepository productRepository,
                                 ProductVariantRepository variantRepository,
                                 PricingComponentRepository componentRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
    }

    @Transactional
    public void seedDefaultTemplates(Company company) {
        if (company == null) {
            return;
        }
        List<Product> existing = productRepository.findAllByCompany_Id(company.getId());
        if (existing != null && !existing.isEmpty()) {
            return;
        }

        createBannerTemplate(company);
        createTshirtTemplate(company);
        createBusinessCardTemplate(company);
    }

    @Transactional
    public void ensureDefaultComponentsForCompany(Company company) {
        if (company == null) {
            return;
        }
        List<ProductVariant> variants = variantRepository.findAllByCompany_Id(company.getId());
        for (ProductVariant variant : variants) {
            if (componentRepository.existsByVariant_IdAndCompany_Id(variant.getId(), company.getId())) {
                continue;
            }
            createDefaultComponentsForVariant(company, variant);
        }
    }

    private void createBannerTemplate(Company company) {
        Product product = new Product();
        product.setCompany(company);
        product.setName("Banner");
        product.setCategory(ProductCategory.BANNER);
        product.setUnitType(UnitType.SQM);
        Product savedProduct = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setCompany(company);
        variant.setProduct(savedProduct);
        variant.setName("Banner 510");
        variant.setDefaultMarkupPercent(new BigDecimal("20.00"));
        variant.setWastePercent(new BigDecimal("5.00"));
        ProductVariant savedVariant = variantRepository.save(variant);

        saveComponent(company, savedVariant, PricingComponentType.MATERIAL, PricingModel.PER_SQM, new BigDecimal("2.00"), "Default: Material per sqm");
        saveComponent(company, savedVariant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"), "Default: Setup");
    }

    private void createTshirtTemplate(Company company) {
        Product product = new Product();
        product.setCompany(company);
        product.setName("T-Shirt");
        product.setCategory(ProductCategory.APPAREL);
        product.setUnitType(UnitType.PIECE);
        Product savedProduct = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setCompany(company);
        variant.setProduct(savedProduct);
        variant.setName("Cotton Tee");
        variant.setDefaultMarkupPercent(new BigDecimal("20.00"));
        variant.setWastePercent(BigDecimal.ZERO);
        ProductVariant savedVariant = variantRepository.save(variant);

        saveComponent(company, savedVariant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("3.00"), "Default: Blank shirt");
        saveComponent(company, savedVariant, PricingComponentType.PRINT, PricingModel.PER_COLOR, new BigDecimal("1.00"), "Default: Per color print");
        saveComponent(company, savedVariant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"), "Default: Setup");
    }

    private void createBusinessCardTemplate(Company company) {
        Product product = new Product();
        product.setCompany(company);
        product.setName("Business Cards");
        product.setCategory(ProductCategory.OTHER);
        product.setUnitType(UnitType.PIECE);
        Product savedProduct = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setCompany(company);
        variant.setProduct(savedProduct);
        variant.setName("Standard 350gsm");
        variant.setDefaultMarkupPercent(new BigDecimal("25.00"));
        variant.setWastePercent(new BigDecimal("2.00"));
        ProductVariant savedVariant = variantRepository.save(variant);

        saveComponent(company, savedVariant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("0.10"), "Default: Paper per card");
        saveComponent(company, savedVariant, PricingComponentType.PRINT, PricingModel.PER_SIDE, new BigDecimal("0.05"), "Default: Print per side");
        saveComponent(company, savedVariant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("3.00"), "Default: Setup");
    }

    private void createDefaultComponentsForVariant(Company company, ProductVariant variant) {
        ProductCategory category = variant.getProduct() != null ? variant.getProduct().getCategory() : ProductCategory.OTHER;
        UnitType unitType = variant.getProduct() != null ? variant.getProduct().getUnitType() : UnitType.PIECE;

        switch (category) {
            case BANNER -> {
                saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_SQM, new BigDecimal("2.50"), "Default: Material per sqm");
                saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_SQM, new BigDecimal("1.20"), "Default: Print per sqm");
                saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"), "Default: Setup");
            }
            case APPAREL -> {
                saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("3.00"), "Default: Blank apparel");
                saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_COLOR, new BigDecimal("1.00"), "Default: Per color print");
                saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"), "Default: Setup");
            }
            case MUG -> {
                saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("2.50"), "Default: Blank mug");
                saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_COLOR, new BigDecimal("1.00"), "Default: Per color print");
                saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"), "Default: Setup");
            }
            case CAP -> {
                saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("2.00"), "Default: Blank cap");
                saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_COLOR, new BigDecimal("1.00"), "Default: Per color print");
                saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"), "Default: Setup");
            }
            case PAPER -> {
                saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("0.05"), "Default: Paper per sheet");
                saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_COLOR, new BigDecimal("0.02"), "Default: Print per color");
                saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("2.00"), "Default: Setup");
            }
            case STICKER -> {
                if (unitType == UnitType.SQM) {
                    saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_SQM, new BigDecimal("1.50"), "Default: Sticker material per sqm");
                    saveComponent(company, variant, PricingComponentType.FINISHING, PricingModel.PER_SQM, new BigDecimal("0.50"), "Default: Cut/finish per sqm");
                } else {
                    saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("0.10"), "Default: Sticker per piece");
                    saveComponent(company, variant, PricingComponentType.FINISHING, PricingModel.PER_UNIT, new BigDecimal("0.05"), "Default: Cut/finish per piece");
                }
                saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("3.00"), "Default: Setup");
            }
            case OTHER -> {
                switch (unitType) {
                    case SQM -> {
                        saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_SQM, new BigDecimal("1.50"), "Default: Material per sqm");
                        saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_SQM, new BigDecimal("0.80"), "Default: Print per sqm");
                        saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("4.00"), "Default: Setup");
                    }
                    case METER -> {
                        saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_METER, new BigDecimal("1.50"), "Default: Material per meter");
                        saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_METER, new BigDecimal("0.80"), "Default: Print per meter");
                        saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("4.00"), "Default: Setup");
                    }
                    case SET -> {
                        saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("1.00"), "Default: Material per set");
                        saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("4.00"), "Default: Setup");
                    }
                    default -> {
                        saveComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("0.50"), "Default: Material per piece");
                        saveComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_UNIT, new BigDecimal("0.30"), "Default: Print per piece");
                        saveComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("3.00"), "Default: Setup");
                    }
                }
            }
        }
    }

    private void saveComponent(Company company,
                               ProductVariant variant,
                               PricingComponentType type,
                               PricingModel model,
                               BigDecimal amount,
                               String notes) {
        PricingComponent component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(variant);
        component.setType(type);
        component.setModel(model);
        component.setAmount(amount);
        component.setNotes(notes);
        componentRepository.save(component);
    }
}
