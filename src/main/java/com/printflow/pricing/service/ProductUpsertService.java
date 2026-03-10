package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.ProductSource;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.dto.ProductForm;
import com.printflow.pricing.dto.ProductImportRow;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class ProductUpsertService {

    private final ProductRepository productRepository;
    private final TenantGuard tenantGuard;
    private final ProductPricingBridgeService pricingBridgeService;

    public ProductUpsertService(ProductRepository productRepository,
                                TenantGuard tenantGuard,
                                ProductPricingBridgeService pricingBridgeService) {
        this.productRepository = productRepository;
        this.tenantGuard = tenantGuard;
        this.pricingBridgeService = pricingBridgeService;
    }

    public Product createManual(ProductForm form) {
        Company company = tenantGuard.requireCompany();
        User user = tenantGuard.getCurrentUser();
        Product product = new Product();
        applyFormToProduct(product, form, ProductSource.MANUAL);
        product.setCompany(company);
        if (user != null) {
            product.setCreatedByUserId(user.getId());
            product.setUpdatedByUserId(user.getId());
        }
        validateSkuUniqueness(company.getId(), product.getSku(), null);
        Product saved = productRepository.save(product);
        pricingBridgeService.ensureDefaultPricingStructure(saved);
        return saved;
    }

    public Product updateManual(Long productId, ProductForm form) {
        Company company = tenantGuard.requireCompany();
        User user = tenantGuard.getCurrentUser();
        Product product = productRepository.findByIdAndCompany_Id(productId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ProductSource source = form.getSource() != null ? form.getSource() : product.getSource();
        applyFormToProduct(product, form, source);
        if (user != null) {
            product.setUpdatedByUserId(user.getId());
        }
        validateSkuUniqueness(company.getId(), product.getSku(), product.getId());
        Product saved = productRepository.save(product);
        pricingBridgeService.reconcileAutoBasePrice(saved);
        return saved;
    }

    public Product upsertImportRow(Company company, ProductImportRow row, ProductSource source) {
        Product product = new Product();
        product.setCompany(company);
        applyImportRowToProduct(product, row, source);
        validateSkuUniqueness(company.getId(), product.getSku(), null);
        Product saved = productRepository.save(product);
        pricingBridgeService.ensureDefaultPricingStructure(saved);
        return saved;
    }

    public Product updateFromImport(Product existing, ProductImportRow row, ProductSource source) {
        applyImportRowToProduct(existing, row, source);
        validateSkuUniqueness(existing.getCompany().getId(), existing.getSku(), existing.getId());
        User user = tenantGuard.getCurrentUser();
        if (user != null) {
            existing.setUpdatedByUserId(user.getId());
        }
        Product saved = productRepository.save(existing);
        pricingBridgeService.reconcileAutoBasePrice(saved);
        return saved;
    }

    public Optional<Product> findBySku(Long companyId, String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return productRepository.findByCompany_IdAndSkuIgnoreCase(companyId, sku);
    }

    public Optional<Product> findByExternalId(Long companyId, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return productRepository.findByCompany_IdAndExternalIdIgnoreCase(companyId, externalId);
    }

    public void validateSkuUniqueness(Long companyId, String sku, Long ignoreId) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        boolean exists = ignoreId == null
            ? productRepository.existsByCompany_IdAndSkuIgnoreCase(companyId, sku)
            : productRepository.existsByCompany_IdAndSkuIgnoreCaseAndIdNot(companyId, sku, ignoreId);
        if (exists) {
            throw new IllegalArgumentException("SKU already exists for this company.");
        }
    }

    private void applyFormToProduct(Product product, ProductForm form, ProductSource defaultSource) {
        product.setName(require(trim(form.getName()), "Name is required"));
        product.setSku(normalizeOptional(form.getSku()));
        product.setDescription(normalizeOptional(form.getDescription()));
        product.setCategoryLabel(normalizeOptional(form.getCategory()));
        product.setUnitLabel(normalizeOptional(form.getUnit()));
        product.setBasePrice(form.getBasePrice() != null ? form.getBasePrice() : BigDecimal.ZERO);
        product.setCurrency(normalizeCurrency(form.getCurrency()));
        product.setActive(form.isActive());
        product.setExternalId(normalizeOptional(form.getExternalId()));
        product.setSource(form.getSource() != null ? form.getSource() : defaultSource);
        product.setCategory(resolveCategory(form.getCategory()));
        product.setUnitType(resolveUnitType(form.getUnit()));
    }

    private void applyImportRowToProduct(Product product, ProductImportRow row, ProductSource source) {
        product.setName(require(trim(row.getName()), "Name is required"));
        product.setSku(normalizeOptional(row.getSku()));
        product.setDescription(normalizeOptional(row.getDescription()));
        product.setCategoryLabel(normalizeOptional(row.getCategory()));
        product.setUnitLabel(normalizeOptional(row.getUnit()));
        product.setBasePrice(row.getBasePrice() != null ? row.getBasePrice() : BigDecimal.ZERO);
        product.setCurrency(normalizeCurrency(row.getCurrency()));
        product.setActive(row.getActive() == null || row.getActive());
        product.setExternalId(normalizeOptional(row.getExternalId()));
        product.setSource(source);
        product.setCategory(resolveCategory(row.getCategory()));
        product.setUnitType(resolveUnitType(row.getUnit()));
    }

    private ProductCategory resolveCategory(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return ProductCategory.OTHER;
        }
        String enumCandidate = normalized.toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
        try {
            return ProductCategory.valueOf(enumCandidate);
        } catch (IllegalArgumentException ex) {
            return ProductCategory.OTHER;
        }
    }

    private UnitType resolveUnitType(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return UnitType.PIECE;
        }
        String enumCandidate = normalized.toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace('²', '2')
            .replace("M2", "SQM")
            .replace(' ', '_');
        if ("KOM".equals(enumCandidate) || "PCS".equals(enumCandidate)) {
            enumCandidate = "PIECE";
        }
        try {
            return UnitType.valueOf(enumCandidate);
        } catch (IllegalArgumentException ex) {
            return UnitType.PIECE;
        }
    }

    private String normalizeCurrency(String currency) {
        String normalized = normalizeOptional(currency);
        if (normalized == null) {
            return "RSD";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
