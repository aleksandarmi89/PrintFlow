package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.enums.AuditAction;
import com.printflow.entity.enums.ProductSource;
import com.printflow.entity.enums.ProductSyncAuthType;
import com.printflow.pricing.dto.ProductForm;
import com.printflow.pricing.dto.ProductImportMode;
import com.printflow.pricing.dto.ProductImportResult;
import com.printflow.pricing.dto.ProductListFilter;
import com.printflow.pricing.dto.ProductSyncSettingsForm;
import com.printflow.pricing.dto.ProductSyncResult;
import com.printflow.pricing.dto.ProductSyncProbeResult;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.service.ProductImportService;
import com.printflow.pricing.service.ProductManagementService;
import com.printflow.pricing.service.ProductExternalSyncFacade;
import com.printflow.pricing.service.ProductSyncSettingsService;
import com.printflow.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.context.i18n.LocaleContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductManagementController extends BaseController {

    private static final List<Integer> ALLOWED_SIZES = List.of(10, 20, 50, 100);

    private final ProductManagementService productService;
    private final ProductImportService productImportService;
    private final ProductExternalSyncFacade productExternalSyncFacade;
    private final ProductSyncSettingsService productSyncSettingsService;
    private final AuditLogService auditLogService;

    public ProductManagementController(ProductManagementService productService,
                                       ProductImportService productImportService,
                                       ProductExternalSyncFacade productExternalSyncFacade,
                                       ProductSyncSettingsService productSyncSettingsService,
                                       AuditLogService auditLogService) {
        this.productService = productService;
        this.productImportService = productImportService;
        this.productExternalSyncFacade = productExternalSyncFacade;
        this.productSyncSettingsService = productSyncSettingsService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Boolean active,
                       @RequestParam(required = false) String source,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "name") String sortBy,
                       @RequestParam(defaultValue = "asc") String sortDir,
                       Model model) {
        ProductSource sourceFilter = parseSource(source);
        ProductListFilter filter = new ProductListFilter();
        filter.setQ(normalizeOptional(q));
        filter.setActive(active);
        filter.setSource(sourceFilter);
        filter.setPage(page);
        filter.setSize(ALLOWED_SIZES.contains(size) ? size : 20);
        filter.setSortBy(normalizeOptional(sortBy));
        filter.setSortDir(normalizeOptional(sortDir));

        Page<Product> productPage = productService.findPage(filter);
        model.addAttribute("productPage", productPage);
        model.addAttribute("pricingReadyMap", productService.pricingReadiness(productPage.getContent()));
        model.addAttribute("apiSyncConfigured", productSyncSettingsService.isSyncConfiguredForCurrentTenant());
        model.addAttribute("syncStatus", productSyncSettingsService.currentStatusView());
        model.addAttribute("sources", ProductSource.values());
        model.addAttribute("filter", filter);
        model.addAttribute("allowedSizes", ALLOWED_SIZES);
        return "products/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        model.addAttribute("product", new Product());
        model.addAttribute("isEdit", false);
        return "products/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("product", new Product());
            model.addAttribute("isEdit", false);
            return "products/form";
        }
        try {
            Product created = productService.create(form);
            auditLogService.log(AuditAction.CREATE, "Product", created.getId(), null, created.getName(),
                "Product created via product management", created.getCompany());
            redirectAttributes.addFlashAttribute("successMessage", tr("Proizvod je uspešno kreiran.", "Product created successfully."));
            return "redirect:/products";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("globalError", ex.getMessage());
            model.addAttribute("product", new Product());
            model.addAttribute("isEdit", false);
            return "products/form";
        }
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        Product product = productService.getById(id);
        model.addAttribute("product", product);
        model.addAttribute("auditUserLabels", productService.auditUserLabels(product));
        return "products/details";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Product product = productService.getById(id);
        model.addAttribute("product", product);
        model.addAttribute("productForm", productService.toForm(product));
        model.addAttribute("isEdit", true);
        return "products/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            model.addAttribute("product", productService.getById(id));
            return "products/form";
        }
        try {
            Product updated = productService.update(id, form);
            auditLogService.log(AuditAction.UPDATE, "Product", updated.getId(), null, updated.getName(),
                "Product updated via product management", updated.getCompany());
            redirectAttributes.addFlashAttribute("successMessage", tr("Proizvod je uspešno ažuriran.", "Product updated successfully."));
            return "redirect:/products/" + id;
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("globalError", ex.getMessage());
            model.addAttribute("isEdit", true);
            model.addAttribute("product", productService.getById(id));
            return "products/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Product product = productService.getById(id);
        try {
            productService.delete(id);
            auditLogService.log(AuditAction.DELETE, "Product", id, null, null,
                "Product deleted via product management", product.getCompany());
            redirectAttributes.addFlashAttribute("successMessage", tr("Proizvod je obrisan.", "Product deleted."));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/products/" + id;
        }
        return "redirect:/products";
    }

    @GetMapping("/import")
    public String importPage(Model model) {
        if (!model.containsAttribute("importMode")) {
            model.addAttribute("importMode", ProductImportMode.ADD_NEW_ONLY);
        }
        model.addAttribute("importModes", ProductImportMode.values());
        return "products/import";
    }

    @PostMapping("/import")
    public String importProducts(@RequestParam("file") MultipartFile file,
                                 @RequestParam(name = "mode", defaultValue = "ADD_NEW_ONLY") ProductImportMode mode,
                                 RedirectAttributes redirectAttributes) {
        Company company = productService.currentCompany();
        try {
            ProductImportResult result = productImportService.importFile(file, company, mode);
            redirectAttributes.addFlashAttribute("importResult", result);
            redirectAttributes.addFlashAttribute("importMode", mode);
            if (result.getFailedCount() == 0) {
                redirectAttributes.addFlashAttribute("successMessage", tr("Import je uspešno završen.", "Import finished successfully."));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                    tr("Import je završen sa greškama. Neuspešni redovi: ", "Import completed with errors. Failed rows: ")
                        + result.getFailedCount());
            }
            auditLogService.log(AuditAction.UPLOAD, "Product", null, null, mode.name(),
                "Product import finished. Imported=" + result.getImportedCount() + ", Updated=" + result.getUpdatedCount(),
                company);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("importMode", mode);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", tr("Import nije uspeo: ", "Import failed: ") + ex.getMessage());
            redirectAttributes.addFlashAttribute("importMode", mode);
        }
        return "redirect:/products/import";
    }

    @PostMapping("/sync")
    public String syncExternalProducts(RedirectAttributes redirectAttributes) {
        Company company = productService.currentCompany();
        try {
            ProductSyncResult result = productExternalSyncFacade.syncFromExternalProvider(company);
            productSyncSettingsService.markSyncSuccess(result);
            redirectAttributes.addFlashAttribute("successMessage",
                tr("API sync završen. Uvezeno: ", "API sync completed. Imported: ")
                    + result.getImportedCount()
                    + tr(", ažurirano: ", ", updated: ")
                    + result.getUpdatedCount()
                    + tr(", greške: ", ", errors: ")
                    + result.getFailedCount()
                    + tr(". Prikazani su API proizvodi.", ". API products are now filtered in list."));
            if (!result.getErrors().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", String.join(" | ", result.getErrors().stream().limit(3).toList()));
            }
            return "redirect:/products?source=API&sortBy=createdAt&sortDir=desc";
        } catch (UnsupportedOperationException ex) {
            productSyncSettingsService.markSyncFailure(ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                tr("API sinhronizacija još nije podešena za ovu kompaniju.", "External API sync is not configured for this company."));
            return "redirect:/products/sync/settings";
        } catch (Exception ex) {
            productSyncSettingsService.markSyncFailure(ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", tr("Sinhronizacija nije uspela: ", "Sync failed: ") + ex.getMessage());
        }
        return "redirect:/products";
    }

    @PostMapping("/sync-pricing")
    public String syncPricingBridge(RedirectAttributes redirectAttributes) {
        int affected = productService.syncProductsToPricingStructure();
        redirectAttributes.addFlashAttribute("successMessage",
            tr("Cenovnik/kalkulator sync završen. Obrađeno proizvoda: ", "Pricing/calculator sync completed. Products processed: ")
                + affected);
        return "redirect:/products";
    }

    @GetMapping("/sync/settings")
    public String syncSettings(Model model) {
        ProductSyncSettingsForm form = productSyncSettingsService.toForm(productSyncSettingsService.getOrCreateCurrentTenantSettings());
        model.addAttribute("syncForm", form);
        model.addAttribute("authTypes", ProductSyncAuthType.values());
        return "products/sync-settings";
    }

    @PostMapping("/sync/settings")
    public String saveSyncSettings(@Valid @ModelAttribute("syncForm") ProductSyncSettingsForm syncForm,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("authTypes", ProductSyncAuthType.values());
            return "products/sync-settings";
        }
        try {
            productSyncSettingsService.saveCurrentTenant(syncForm);
            redirectAttributes.addFlashAttribute("successMessage",
                tr("API sync podešavanja su sačuvana.", "API sync settings saved."));
            return "redirect:/products/sync/settings";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("globalError", ex.getMessage());
            model.addAttribute("authTypes", ProductSyncAuthType.values());
            return "products/sync-settings";
        }
    }

    @PostMapping("/sync/settings/test")
    public String testSyncSettingsConnection(RedirectAttributes redirectAttributes) {
        Company company = productService.currentCompany();
        try {
            ProductSyncProbeResult probe = productExternalSyncFacade.testConnection(company);
            redirectAttributes.addFlashAttribute("successMessage",
                tr("Test konekcije je uspešan. Pronađeno stavki: ", "Connection test successful. Items detected: ")
                    + probe.getDiscoveredRows()
                    + tr(". Ovo NE uvozi proizvode.", ". This does NOT import products."));
        } catch (UnsupportedOperationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                tr("API sync nije konfigurisan. Sačuvajte bar Endpoint URL u podešavanjima.", "API sync is not configured. Save at least Endpoint URL in settings."));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                tr("Test konekcije nije uspeo: ", "Connection test failed: ") + ex.getMessage());
        }
        return "redirect:/products/sync/settings";
    }

    @GetMapping("/import/template")
    @ResponseBody
    public ResponseEntity<byte[]> importTemplate() {
        byte[] payload = productImportService.templateCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("product-import-template.csv").build().toString())
            .contentType(MediaType.valueOf("text/csv"))
            .body(payload);
    }

    private String tr(String sr, String en) {
        String language = LocaleContextHolder.getLocale() != null ? LocaleContextHolder.getLocale().getLanguage() : null;
        return "sr".equalsIgnoreCase(language) ? sr : en;
    }

    private ProductSource parseSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return ProductSource.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
