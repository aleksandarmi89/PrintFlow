package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSource;
import com.printflow.pricing.dto.ProductForm;
import com.printflow.pricing.dto.ProductListFilter;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.UserRepository;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.TenantGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;

@Service
@Transactional
public class ProductManagementService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;
    private final ProductUpsertService productUpsertService;
    private final TenantGuard tenantGuard;
    private final ProductPricingBridgeService pricingBridgeService;
    private final UserRepository userRepository;

    public ProductManagementService(ProductRepository productRepository,
                                    ProductVariantRepository variantRepository,
                                    PricingComponentRepository componentRepository,
                                    ProductUpsertService productUpsertService,
                                    TenantGuard tenantGuard,
                                    ProductPricingBridgeService pricingBridgeService,
                                    UserRepository userRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
        this.productUpsertService = productUpsertService;
        this.tenantGuard = tenantGuard;
        this.pricingBridgeService = pricingBridgeService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<Product> findPage(ProductListFilter filter) {
        Long companyId = tenantGuard.requireCompanyId();
        int page = Math.max(0, filter.getPage());
        int size = filter.getSize() > 0 ? Math.min(filter.getSize(), 100) : 20;
        Sort sort = resolveSort(filter.getSortBy(), filter.getSortDir());
        Specification<Product> spec = tenantSpec(companyId)
            .and(searchSpec(filter.getQ()))
            .and(activeSpec(filter.getActive()))
            .and(sourceSpec(filter.getSource()));
        return productRepository.findAll(spec, PageRequest.of(page, size, sort));
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        Long companyId = tenantGuard.requireCompanyId();
        return productRepository.findByIdAndCompany_Id(id, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    public Product create(ProductForm form) {
        return productUpsertService.createManual(form);
    }

    public Product update(Long id, ProductForm form) {
        return productUpsertService.updateManual(id, form);
    }

    public void delete(Long id) {
        Product product = getById(id);
        try {
            productRepository.delete(product);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Product cannot be deleted because it is referenced by existing records.");
        }
    }

    public int syncProductsToPricingStructure() {
        Long companyId = tenantGuard.requireCompanyId();
        int affected = 0;
        for (Product product : productRepository.findAllByCompany_Id(companyId)) {
            pricingBridgeService.ensureDefaultPricingStructure(product);
            affected++;
        }
        return affected;
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> pricingReadiness(List<Product> products) {
        Map<Long, Boolean> readiness = new HashMap<>();
        if (products == null || products.isEmpty()) {
            return readiness;
        }
        Long companyId = tenantGuard.requireCompanyId();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            List<ProductVariant> variants = variantRepository.findAllByProduct_IdAndCompany_Id(product.getId(), companyId);
            boolean ready = variants.stream()
                .filter(v -> v.getId() != null)
                .anyMatch(v -> componentRepository.existsByVariant_IdAndCompany_Id(v.getId(), companyId));
            readiness.put(product.getId(), ready);
        }
        return readiness;
    }

    @Transactional(readOnly = true)
    public ProductForm toForm(Product product) {
        ProductForm form = new ProductForm();
        form.setName(product.getName());
        form.setSku(product.getSku());
        form.setDescription(product.getDescription());
        form.setCategory(product.getCategoryLabel());
        form.setUnit(product.getUnitLabel());
        form.setBasePrice(product.getBasePrice());
        form.setCurrency(product.getCurrency());
        form.setActive(product.isActive());
        form.setSource(product.getSource());
        form.setExternalId(product.getExternalId());
        return form;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> auditUserLabels(Product product) {
        Map<Long, String> labels = new HashMap<>();
        if (product == null) {
            return labels;
        }
        Long companyId = tenantGuard.requireCompanyId();
        List<Long> ids = new ArrayList<>();
        if (product.getCreatedByUserId() != null) {
            ids.add(product.getCreatedByUserId());
        }
        if (product.getUpdatedByUserId() != null && !Objects.equals(product.getUpdatedByUserId(), product.getCreatedByUserId())) {
            ids.add(product.getUpdatedByUserId());
        }
        if (ids.isEmpty()) {
            return labels;
        }
        userRepository.findByCompany_IdAndIdIn(companyId, ids).forEach(user -> {
            String label = user.getEmail();
            if (label == null || label.isBlank()) {
                label = user.getUsername();
            }
            if (label == null || label.isBlank()) {
                label = user.getFullName();
            }
            labels.put(user.getId(), label);
        });
        return labels;
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String field = switch (sortBy == null ? "" : sortBy) {
            case "createdAt" -> "createdAt";
            case "basePrice" -> "basePrice";
            default -> "name";
        };
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private Specification<Product> tenantSpec(Long companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }

    private Specification<Product> searchSpec(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")), like),
            cb.like(cb.lower(cb.coalesce(root.get("sku"), "")), like)
        );
    }

    private Specification<Product> activeSpec(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    private Specification<Product> sourceSpec(ProductSource source) {
        if (source == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("source"), source);
    }

    @Transactional(readOnly = true)
    public Company currentCompany() {
        return tenantGuard.requireCompany();
    }
}
