package com.printflow.pricing.controller;

import com.printflow.entity.Company;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.entity.enums.AuditAction;
import com.printflow.service.AuditLogService;
import com.printflow.service.BillingAccessService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductAdminApiController {

    private final CurrentContextService currentContextService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;
    private final AuditLogService auditLogService;
    private final BillingAccessService billingAccessService;

    public ProductAdminApiController(CurrentContextService currentContextService,
                                     ProductRepository productRepository,
                                     ProductVariantRepository variantRepository,
                                     PricingComponentRepository componentRepository,
                                     AuditLogService auditLogService,
                                     BillingAccessService billingAccessService) {
        this.currentContextService = currentContextService;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
        this.auditLogService = auditLogService;
        this.billingAccessService = billingAccessService;
    }

    @GetMapping("/products")
    public List<Product> listProducts() {
        Company company = currentContextService.currentCompany();
        return productRepository.findAllByCompany_Id(company.getId());
    }

    @PostMapping("/products")
    public Product createProduct(@Valid @RequestBody Product product) {
        Company company = currentContextService.currentCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        product.setCompany(company);
        Product saved = productRepository.save(product);
        auditLogService.log(AuditAction.CREATE, "Product", saved.getId(), null, saved.getName(),
            "Product created: " + saved.getName(), company);
        return saved;
    }

    @GetMapping("/products/{productId}/variants")
    public List<ProductVariant> listVariants(@PathVariable Long productId) {
        Company company = currentContextService.currentCompany();
        productRepository.findByIdAndCompany_Id(productId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return variantRepository.findAllByProduct_IdAndCompany_Id(productId, company.getId());
    }

    @PostMapping("/products/{productId}/variants")
    public ProductVariant createVariant(@PathVariable Long productId,
                                        @Valid @RequestBody ProductVariant variant) {
        Company company = currentContextService.currentCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        Product product = productRepository.findByIdAndCompany_Id(productId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        variant.setCompany(company);
        variant.setProduct(product);
        ProductVariant saved = variantRepository.save(variant);
        auditLogService.log(AuditAction.UPDATE, "ProductVariant", saved.getId(), null, saved.getName(),
            "Pricing updated for variant " + saved.getName(), company);
        return saved;
    }

    @GetMapping("/variants/{variantId}/components")
    public List<PricingComponent> listComponents(@PathVariable Long variantId) {
        Company company = currentContextService.currentCompany();
        variantRepository.findByIdAndCompany_Id(variantId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        return componentRepository.findAllByVariant_IdAndCompany_Id(variantId, company.getId());
    }

    @PostMapping("/variants/{variantId}/components")
    public PricingComponent createComponent(@PathVariable Long variantId,
                                            @Valid @RequestBody PricingComponent component) {
        Company company = currentContextService.currentCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        ProductVariant variant = variantRepository.findByIdAndCompany_Id(variantId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        component.setCompany(company);
        component.setVariant(variant);
        PricingComponent saved = componentRepository.save(component);
        auditLogService.log(AuditAction.UPDATE, "PricingComponent", saved.getId(), null, saved.getType().name(),
            "Pricing updated for variant " + variant.getName(), company);
        return saved;
    }

}
