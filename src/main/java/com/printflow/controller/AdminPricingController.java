package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.enums.AuditAction;
import com.printflow.pricing.dto.BulkCategoryPricingUpdateRequest;
import com.printflow.pricing.dto.UpdateComponentAmountRequest;
import com.printflow.pricing.dto.UpdateVariantPricingRequest;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.pricing.service.PricingAdminService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/pricing")
public class AdminPricingController extends BaseController {

    private final CurrentContextService currentContextService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;
    private final AuditLogService auditLogService;
    private final TemplateSeederService templateSeederService;
    private final PricingAdminService pricingAdminService;
    private final BillingAccessService billingAccessService;

    public AdminPricingController(CurrentContextService currentContextService,
                                  ProductRepository productRepository,
                                  ProductVariantRepository variantRepository,
                                  PricingComponentRepository componentRepository,
                                  AuditLogService auditLogService,
                                  TemplateSeederService templateSeederService,
                                  PricingAdminService pricingAdminService,
                                  BillingAccessService billingAccessService) {
        this.currentContextService = currentContextService;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
        this.auditLogService = auditLogService;
        this.templateSeederService = templateSeederService;
        this.pricingAdminService = pricingAdminService;
        this.billingAccessService = billingAccessService;
    }

    @GetMapping("/products")
    public String products(Model model) {
        Company company = currentContextService.currentCompany();
        templateSeederService.ensureDefaultComponentsForCompany(company);
        List<Product> products = productRepository.findAllByCompany_Id(company.getId());
        model.addAttribute("products", products);
        java.util.Map<Long, Boolean> productHasDefault = new java.util.HashMap<>();
        for (Product product : products) {
            List<ProductVariant> variants = variantRepository.findAllByProduct_IdAndCompany_Id(product.getId(), company.getId());
            boolean hasDefault = false;
            for (ProductVariant variant : variants) {
                List<PricingComponent> components = componentRepository.findAllByVariant_IdAndCompany_Id(variant.getId(), company.getId());
                if (components.stream().anyMatch(c -> c.getNotes() != null && c.getNotes().toLowerCase().contains("default"))) {
                    hasDefault = true;
                    break;
                }
            }
            productHasDefault.put(product.getId(), hasDefault);
        }
        model.addAttribute("productHasDefault", productHasDefault);
        model.addAttribute("productForm", new Product());
        model.addAttribute("bulkForm", new BulkCategoryPricingUpdateRequest());
        model.addAttribute("categories", com.printflow.entity.enums.ProductCategory.values());
        model.addAttribute("componentTypes", com.printflow.entity.enums.PricingComponentType.values());
        model.addAttribute("pricingModels", com.printflow.entity.enums.PricingModel.values());
        return "admin/pricing/products";
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute("productForm") Product product,
                                BindingResult bindingResult,
                                Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        if (bindingResult.hasErrors()) {
            model.addAttribute("products", productRepository.findAllByCompany_Id(company.getId()));
            return "admin/pricing/products";
        }
        product.setCompany(company);
        Product saved = productRepository.save(product);
        auditLogService.log(AuditAction.CREATE, "Product", saved.getId(), null, saved.getName(),
            "Product created: " + saved.getName(), company);
        return "redirect:/admin/pricing/products";
    }

    @PostMapping("/products/templates")
    public String createFromTemplates(Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        templateSeederService.seedDefaultTemplates(company);
        return redirectWithSuccess("/admin/pricing/products", "Templates added.", model);
    }

    @PostMapping("/bulk-update")
    public String bulkUpdate(@Valid @ModelAttribute("bulkForm") BulkCategoryPricingUpdateRequest form,
                             BindingResult bindingResult,
                             Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        if (!form.isApplyAllCategories() && form.getCategory() == null) {
            bindingResult.rejectValue("category", "required", "Category is required");
        }
        if (bindingResult.hasErrors()) {
            List<Product> products = productRepository.findAllByCompany_Id(company.getId());
            model.addAttribute("products", products);
            model.addAttribute("productForm", new Product());
            model.addAttribute("categories", com.printflow.entity.enums.ProductCategory.values());
            model.addAttribute("componentTypes", com.printflow.entity.enums.PricingComponentType.values());
            model.addAttribute("pricingModels", com.printflow.entity.enums.PricingModel.values());
            return "admin/pricing/products";
        }
        try {
            pricingAdminService.bulkUpdateCategoryPricing(company, form);
            return redirectWithSuccess("/admin/pricing/products", "Bulk update applied.", model);
        } catch (IllegalArgumentException ex) {
            return redirectWithError("/admin/pricing/products", ex.getMessage(), model);
        }
    }

    @PostMapping("/bulk-preview")
    public String bulkPreview(@ModelAttribute("bulkForm") BulkCategoryPricingUpdateRequest form,
                              Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        if (!form.isApplyAllCategories() && form.getCategory() == null) {
            return redirectWithError("/admin/pricing/products", "Category is required.", model);
        }
        var preview = pricingAdminService.previewBulkUpdate(company, form, 200);
        model.addAttribute("preview", preview);
        model.addAttribute("bulkForm", form);
        return "admin/pricing/bulk-preview";
    }

    @GetMapping("/bulk-export")
    public void bulkExport(@RequestParam(required = false) com.printflow.entity.enums.ProductCategory category,
                           @RequestParam(required = false) com.printflow.entity.enums.PricingComponentType componentType,
                           @RequestParam(required = false) com.printflow.entity.enums.PricingModel pricingModel,
                           @RequestParam(defaultValue = "false") boolean applyAllCategories,
                           jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        BulkCategoryPricingUpdateRequest req = new BulkCategoryPricingUpdateRequest();
        req.setCategory(category);
        req.setComponentType(componentType);
        req.setPricingModel(pricingModel);
        req.setApplyAllCategories(applyAllCategories);

        var preview = pricingAdminService.previewBulkUpdate(company, req, Integer.MAX_VALUE);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=bulk_pricing_preview.csv");
        var writer = response.getWriter();
        writer.println("Category,Product,Variant,ComponentType,Model,Amount,Notes,Markup%,Waste%,MinPrice");
        for (var c : preview.getComponents()) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,,,%n",
                safeCsv(c.getCategory()),
                safeCsv(c.getProductName()),
                safeCsv(c.getVariantName()),
                safeCsv(c.getComponentType()),
                safeCsv(c.getPricingModel()),
                safeCsv(c.getAmount()),
                safeCsv(c.getNotes()));
        }
        for (var v : preview.getVariants()) {
            writer.printf("%s,%s,%s,,,,,%s,%s,%s%n",
                safeCsv(v.getCategory()),
                safeCsv(v.getProductName()),
                safeCsv(v.getVariantName()),
                safeCsv(v.getMarkupPercent()),
                safeCsv(v.getWastePercent()),
                safeCsv(v.getMinPrice()));
        }
        writer.flush();
    }

    private String safeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replace("\"", "\"\"");
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text + "\"";
        }
        return text;
    }

    @GetMapping("/products/{id}")
    public String productDetails(@PathVariable Long id, Model model) {
        Company company = currentContextService.currentCompany();
        Product product = productRepository.findByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        List<ProductVariant> variants = variantRepository.findAllByProductIdAndCompanyIdFetchProduct(id, company.getId())
            .stream()
            .filter(java.util.Objects::nonNull)
            .toList();
        populateProductEditModel(company, product, variants, model);
        return "admin/pricing/product-edit";
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
            List<ProductVariant> variants = variantRepository.findAllByProductIdAndCompanyIdFetchProduct(id, company.getId())
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
            populateProductEditModel(company, product, variants, model);
            return "admin/pricing/product-edit";
        }
        variant.setCompany(company);
        variant.setProduct(product);
        ProductVariant saved = variantRepository.save(variant);
        auditLogService.log(AuditAction.UPDATE, "ProductVariant", saved.getId(), null, saved.getName(),
            "Pricing updated for variant " + saved.getName(), company);
        return "redirect:/admin/pricing/products/" + id;
    }

    @PostMapping("/variants/{id}")
    public String updateVariantPricing(@PathVariable Long id,
                                       @Valid @ModelAttribute("form") UpdateVariantPricingRequest form,
                                       BindingResult bindingResult,
                                       @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                       Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        ProductVariant variant = variantRepository.findWithProductByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        if (bindingResult.hasErrors()) {
            java.util.Map<String, String> fieldErrors = new java.util.HashMap<>();
            bindingResult.getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
            model.addAttribute("variant", variant);
            model.addAttribute("form", form);
            model.addAttribute("saved", false);
            model.addAttribute("fieldErrors", fieldErrors);
            model.addAttribute("pricingError", "pricing.validation_error");
            model.addAttribute("variantHasDefault", java.util.Collections.singletonMap(variant.getId(), false));
            if (hxRequest != null) {
                return "admin/pricing/product-edit :: variantRow";
            }
            List<ProductVariant> variants = variantRepository.findAllByProductIdAndCompanyIdFetchProduct(variant.getProduct().getId(), company.getId())
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
            populateProductEditModel(company, variant.getProduct(), variants, model);
            @SuppressWarnings("unchecked")
            Map<Long, UpdateVariantPricingRequest> variantForms =
                (Map<Long, UpdateVariantPricingRequest>) model.getAttribute("variantForms");
            if (variantForms != null) {
                variantForms.put(variant.getId(), form);
            }
            java.util.Map<Long, java.util.Map<String, String>> variantFieldErrors = new java.util.HashMap<>();
            variantFieldErrors.put(variant.getId(), fieldErrors);
            model.addAttribute("variantFieldErrors", variantFieldErrors);
            model.addAttribute("pricingError", "pricing.validation_error");
            return "admin/pricing/product-edit";
        }
        ProductVariant updated = pricingAdminService.updateVariantPricing(company, id, form);
        model.addAttribute("variant", updated);
        model.addAttribute("form", UpdateVariantPricingRequest.fromVariant(updated));
        model.addAttribute("saved", true);
        model.addAttribute("variantHasDefault", java.util.Collections.singletonMap(updated.getId(), false));
        if (hxRequest != null) {
            return "admin/pricing/product-edit :: variantRow";
        }
        return "redirect:/admin/pricing/products/" + updated.getProduct().getId();
    }

    private void populateProductEditModel(Company company, Product product, List<ProductVariant> variants, Model model) {
        List<ProductVariant> safeVariants = (variants == null ? java.util.List.<ProductVariant>of() : variants).stream()
            .filter(v -> v != null && v.getId() != null)
            .toList();
        Map<Long, UpdateVariantPricingRequest> variantForms = new HashMap<>();
        Map<Long, Boolean> variantHasDefault = new HashMap<>();
        for (ProductVariant variant : safeVariants) {
            variantForms.put(variant.getId(), UpdateVariantPricingRequest.fromVariant(variant));
            List<PricingComponent> components = componentRepository.findAllByVariant_IdAndCompany_Id(variant.getId(), company.getId());
            boolean hasDefault = components.stream()
                .anyMatch(c -> c.getNotes() != null && c.getNotes().toLowerCase().contains("default"));
            variantHasDefault.put(variant.getId(), Boolean.TRUE.equals(hasDefault));
        }
        model.addAttribute("product", product);
        model.addAttribute("variants", safeVariants);
        ProductVariant variantForm = new ProductVariant();
        variantForm.setProduct(product);
        model.addAttribute("variantForm", variantForm);
        model.addAttribute("variantForms", variantForms);
        model.addAttribute("variantHasDefault", variantHasDefault);
        model.addAttribute("updateVariantFallback", new UpdateVariantPricingRequest());
    }

    private void requireBilling(Company company) {
        if (company == null || billingAccessService == null) {
            return;
        }
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        Product product = productRepository.findByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        pricingAdminService.deleteProduct(company, id);
        auditLogService.log(AuditAction.DELETE, "Product", id, null, product.getName(),
            "Product deleted: " + product.getName(), company);
        return "redirect:/admin/pricing/products";
    }

    @PostMapping("/variants/{id}/delete")
    public String deleteVariant(@PathVariable Long id) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        ProductVariant variant = variantRepository.findWithProductByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        pricingAdminService.deleteVariant(company, id);
        auditLogService.log(AuditAction.DELETE, "ProductVariant", id, null, variant.getName(),
            "Variant deleted: " + variant.getName(), company);
        return "redirect:/admin/pricing/products/" + variant.getProduct().getId();
    }

    @GetMapping("/variants/{id}")
    public String variantDetails(@PathVariable Long id, Model model) {
        Company company = currentContextService.currentCompany();
        ProductVariant variant = variantRepository.findWithProductByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        List<PricingComponent> components = componentRepository.findAllByVariant_IdAndCompany_Id(id, company.getId())
            .stream()
            .filter(java.util.Objects::nonNull)
            .toList();
        Map<Long, UpdateComponentAmountRequest> componentForms = new HashMap<>();
        for (PricingComponent component : components) {
            UpdateComponentAmountRequest form = new UpdateComponentAmountRequest();
            form.setAmount(component.getAmount());
            componentForms.put(component.getId(), form);
        }
        model.addAttribute("variant", variant);
        model.addAttribute("productName", variant.getProduct() != null ? variant.getProduct().getName() : "");
        model.addAttribute("productId", variant.getProduct() != null ? variant.getProduct().getId() : null);
        model.addAttribute("components", components);
        model.addAttribute("componentForm", new PricingComponent());
        model.addAttribute("componentForms", componentForms);
        model.addAttribute("updateComponentFallback", new UpdateComponentAmountRequest());
        model.addAttribute("componentTypes", com.printflow.entity.enums.PricingComponentType.values());
        model.addAttribute("pricingModels", com.printflow.entity.enums.PricingModel.values());
        return "admin/pricing/variant-edit";
    }

    @PostMapping("/variants/{id}/components")
    public String createComponent(@PathVariable Long id,
                                  @Valid @ModelAttribute("componentForm") PricingComponent component,
                                  BindingResult bindingResult,
                                  Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        ProductVariant variant = variantRepository.findWithProductByIdAndCompany_Id(id, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        if (bindingResult.hasErrors()) {
            model.addAttribute("variant", variant);
            model.addAttribute("productName", variant.getProduct() != null ? variant.getProduct().getName() : "");
            model.addAttribute("productId", variant.getProduct() != null ? variant.getProduct().getId() : null);
            model.addAttribute("components", componentRepository.findAllByVariant_IdAndCompany_Id(id, company.getId())
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList());
            model.addAttribute("componentTypes", com.printflow.entity.enums.PricingComponentType.values());
            model.addAttribute("pricingModels", com.printflow.entity.enums.PricingModel.values());
            return "admin/pricing/variant-edit";
        }
        component.setCompany(company);
        component.setVariant(variant);
        PricingComponent saved = componentRepository.save(component);
        auditLogService.log(AuditAction.UPDATE, "PricingComponent", saved.getId(), null, saved.getType().name(),
            "Pricing updated for variant " + variant.getName(), company);
        return "redirect:/admin/pricing/variants/" + id;
    }

    @PostMapping("/components/{componentId}")
    public String updateComponent(@PathVariable Long componentId,
                                  @Valid @ModelAttribute("form") UpdateComponentAmountRequest form,
                                  BindingResult bindingResult,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                  Model model) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        PricingComponent component = componentRepository.findByIdAndCompany_Id(componentId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Component not found"));
        if (bindingResult.hasErrors()) {
            java.util.Map<String, String> fieldErrors = new java.util.HashMap<>();
            bindingResult.getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
            model.addAttribute("component", component);
            model.addAttribute("form", form);
            model.addAttribute("saved", false);
            model.addAttribute("fieldErrors", fieldErrors);
            model.addAttribute("pricingError", "pricing.validation_error");
            if (hxRequest != null) {
                return "admin/pricing/variant-edit :: componentRow";
            }
            return "redirect:/admin/pricing/variants/" + component.getVariant().getId();
        }
        PricingComponent updated = pricingAdminService.updateComponentAmount(company, componentId, form);
        model.addAttribute("component", updated);
        UpdateComponentAmountRequest refreshed = new UpdateComponentAmountRequest();
        refreshed.setAmount(updated.getAmount());
        model.addAttribute("form", refreshed);
        model.addAttribute("saved", true);
        if (hxRequest != null) {
            return "admin/pricing/variant-edit :: componentRow";
        }
        return "redirect:/admin/pricing/variants/" + updated.getVariant().getId();
    }

    @PostMapping("/variants/{variantId}/components/{componentId}/delete")
    public String deleteComponent(@PathVariable Long variantId, @PathVariable Long componentId) {
        Company company = currentContextService.currentCompany();
        requireBilling(company);
        PricingComponent component = componentRepository
            .findWithVariantByIdAndVariant_IdAndCompany_Id(componentId, variantId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Component not found"));
        String variantName = component.getVariant() != null ? component.getVariant().getName() : null;
        pricingAdminService.deleteComponent(company, componentId);
        auditLogService.log(AuditAction.DELETE, "PricingComponent", componentId, null, null,
            "Pricing updated for variant " + variantName, company);
        return "redirect:/admin/pricing/variants/" + variantId;
    }
}
