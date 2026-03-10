package com.printflow.pricing.controller;

import com.printflow.entity.Company;
import com.printflow.entity.enums.AuditAction;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.service.AuditLogService;
import com.printflow.service.BillingAccessService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.TemplateSeederService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/pricing")
public class PricingPageController {

    private final CurrentContextService currentContextService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ClientRepository clientRepository;
    private final AuditLogService auditLogService;
    private final TemplateSeederService templateSeederService;
    private final BillingAccessService billingAccessService;

    public PricingPageController(CurrentContextService currentContextService,
                                 ProductRepository productRepository,
                                 ProductVariantRepository variantRepository,
                                 PricingComponentRepository componentRepository,
                                 WorkOrderRepository workOrderRepository,
                                 ClientRepository clientRepository,
                                 AuditLogService auditLogService,
                                 TemplateSeederService templateSeederService,
                                 BillingAccessService billingAccessService) {
        this.currentContextService = currentContextService;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
        this.workOrderRepository = workOrderRepository;
        this.clientRepository = clientRepository;
        this.auditLogService = auditLogService;
        this.templateSeederService = templateSeederService;
        this.billingAccessService = billingAccessService;
    }

    @GetMapping("/products")
    public String products(Model model) {
        Company company = currentContextService.currentCompany();
        List<Product> products = productRepository.findAllByCompany_Id(company.getId());
        model.addAttribute("products", products);
        model.addAttribute("productForm", new Product());
        return "pricing/products";
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute("productForm") Product product,
                                BindingResult bindingResult,
                                Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        if (bindingResult.hasErrors()) {
            model.addAttribute("products", productRepository.findAllByCompany_Id(company.getId()));
            return "pricing/products";
        }
        product.setCompany(company);
        Product saved = productRepository.save(product);
        auditLogService.log(AuditAction.CREATE, "Product", saved.getId(), null, saved.getName(),
            "Product created: " + saved.getName(), company);
        return "redirect:/pricing/products";
    }

    @GetMapping("/products/{id}/variants")
    public String variants(@PathVariable Long id, Model model) {
        Company company = currentContextService.currentCompany();
        Product product = productRepository.findByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        List<ProductVariant> variants = variantRepository.findAllByProduct_IdAndCompany_Id(id, company.getId());
        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        model.addAttribute("variantForm", new ProductVariant());
        return "pricing/variants";
    }

    @PostMapping("/products/{id}/variants")
    public String createVariant(@PathVariable Long id,
                                @Valid @ModelAttribute("variantForm") ProductVariant variant,
                                BindingResult bindingResult,
                                Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        Product product = productRepository.findByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (bindingResult.hasErrors()) {
            model.addAttribute("product", product);
            model.addAttribute("variants", variantRepository.findAllByProduct_IdAndCompany_Id(id, company.getId()));
            return "pricing/variants";
        }
        variant.setCompany(company);
        variant.setProduct(product);
        ProductVariant saved = variantRepository.save(variant);
        auditLogService.log(AuditAction.UPDATE, "ProductVariant", saved.getId(), null, saved.getName(),
            "Pricing updated for variant " + saved.getName(), company);
        return "redirect:/pricing/products/" + id + "/variants";
    }

    @GetMapping("/variants/{id}/pricing")
    public String pricingComponents(@PathVariable Long id, Model model) {
        Company company = currentContextService.currentCompany();
        ProductVariant variant = variantRepository.findByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        List<PricingComponent> components = componentRepository.findAllByVariant_IdAndCompany_Id(id, company.getId());
        model.addAttribute("variant", variant);
        model.addAttribute("components", components);
        model.addAttribute("componentForm", new PricingComponent());
        model.addAttribute("componentTypes", com.printflow.entity.enums.PricingComponentType.values());
        model.addAttribute("pricingModels", com.printflow.entity.enums.PricingModel.values());
        return "pricing/components";
    }

    @PostMapping("/variants/{id}/pricing")
    public String createComponent(@PathVariable Long id,
                                  @Valid @ModelAttribute("componentForm") PricingComponent component,
                                  BindingResult bindingResult,
                                  Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        ProductVariant variant = variantRepository.findByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        if (bindingResult.hasErrors()) {
            model.addAttribute("variant", variant);
            model.addAttribute("components", componentRepository.findAllByVariant_IdAndCompany_Id(id, company.getId()));
            model.addAttribute("componentTypes", com.printflow.entity.enums.PricingComponentType.values());
            model.addAttribute("pricingModels", com.printflow.entity.enums.PricingModel.values());
            return "pricing/components";
        }
        component.setCompany(company);
        component.setVariant(variant);
        PricingComponent saved = componentRepository.save(component);
        auditLogService.log(AuditAction.UPDATE, "PricingComponent", saved.getId(), null, saved.getType().name(),
            "Pricing updated for variant " + variant.getName(), company);
        return "redirect:/pricing/variants/" + id + "/pricing";
    }

    @PostMapping("/variants/{variantId}/pricing/{componentId}/delete")
    public String deleteComponent(@PathVariable Long variantId, @PathVariable Long componentId) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        PricingComponent component = componentRepository
            .findByIdAndVariant_IdAndCompany_Id(componentId, variantId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Component not found"));
        componentRepository.delete(component);
        auditLogService.log(AuditAction.DELETE, "PricingComponent", componentId, null, null,
            "Pricing updated for variant " + component.getVariant().getName(), company);
        return "redirect:/pricing/variants/" + variantId + "/pricing";
    }

    @GetMapping("/calculate")
    public String pricingCalculator(Model model) {
        Company company = currentContextService.currentCompany();
        templateSeederService.ensureDefaultComponentsForCompany(company);
        model.addAttribute("products", productRepository.findAllByCompany_Id(company.getId()));
        model.addAttribute("variants", variantRepository.findSelectRowsByCompanyId(company.getId()));
        model.addAttribute("workOrders", workOrderRepository.findSelectRowsByCompanyId(company.getId()));
        model.addAttribute("clients", clientRepository.findActiveSelectRowsByCompanyId(company.getId()));
        return "pricing/calculate";
    }

    private void requireBilling(Company company) {
        if (company == null || billingAccessService == null) {
            return;
        }
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
    }
}
