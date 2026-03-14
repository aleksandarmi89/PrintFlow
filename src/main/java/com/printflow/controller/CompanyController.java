package com.printflow.controller;

import com.printflow.dto.CompanyDTO;
import com.printflow.service.CompanyService;
import com.printflow.service.TenantContextService;
import com.printflow.service.AuditLogService;
import com.printflow.entity.enums.AuditAction;
import com.printflow.config.PaginationConfig;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/companies")
public class CompanyController extends BaseController {

    private final CompanyService companyService;
    private final PaginationConfig paginationConfig;
    private final com.printflow.service.CompanyBrandingService companyBrandingService;
    private final TenantContextService tenantContextService;
    private final AuditLogService auditLogService;

    public CompanyController(CompanyService companyService,
                             PaginationConfig paginationConfig,
                             com.printflow.service.CompanyBrandingService companyBrandingService,
                             TenantContextService tenantContextService,
                             AuditLogService auditLogService) {
        this.companyService = companyService;
        this.paginationConfig = paginationConfig;
        this.companyBrandingService = companyBrandingService;
        this.tenantContextService = tenantContextService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String listCompanies(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String plan,
                                @RequestParam(required = false) String override,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(required = false) Integer size,
                                Model model) {
        String normalizedSearch = (search != null ? search.trim() : null);
        if (normalizedSearch != null && normalizedSearch.isBlank()) {
            normalizedSearch = null;
        }
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
        String normalizedPlan = (plan != null ? plan.trim() : null);
        com.printflow.entity.enums.PlanTier planTier = null;
        if (normalizedPlan != null && !normalizedPlan.isBlank()) {
            planTier = parsePlanTier(normalizedPlan);
        }
        String normalizedOverride = (override != null ? override.trim() : null);
        Boolean overrideActive = null;
        if (normalizedOverride != null && !normalizedOverride.isBlank()) {
            if ("on".equalsIgnoreCase(normalizedOverride)) {
                overrideActive = true;
            } else if ("off".equalsIgnoreCase(normalizedOverride)) {
                overrideActive = false;
            }
        }

        org.springframework.data.domain.Page<CompanyDTO> companiesPage =
            companyService.getCompanies(normalizedSearch, planTier, overrideActive, pageable);
        if (safePage >= companiesPage.getTotalPages() && companiesPage.getTotalPages() > 0) {
            safePage = companiesPage.getTotalPages() - 1;
            pageable = org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
            companiesPage = companyService.getCompanies(normalizedSearch, planTier, overrideActive, pageable);
        }
        model.addAttribute("companies", companiesPage.getContent());
        model.addAttribute("companiesPage", companiesPage);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("plan", normalizedPlan);
        model.addAttribute("override", normalizedOverride);
        model.addAttribute("planOptions", com.printflow.entity.enums.PlanTier.values());
        model.addAttribute("currentPage", companiesPage.getNumber());
        model.addAttribute("totalPages", companiesPage.getTotalPages());
        model.addAttribute("totalItems", companiesPage.getTotalElements());
        model.addAttribute("lastPage", Math.max(0, companiesPage.getTotalPages() - 1));
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        model.addAttribute("totalCompanies", companiesPage.getTotalElements());
        return "admin/companies/list";
    }

    @GetMapping("/create")
    public String createCompanyForm(Model model) {
        model.addAttribute("company", new CompanyDTO());
        return "admin/companies/create";
    }

    @PostMapping("/create")
    public String createCompany(@ModelAttribute CompanyDTO companyDTO, Model model) {
        try {
            companyService.createCompany(companyDTO);
            return redirectWithSuccess("/admin/companies", "admin.companies.flash.created", model);
        } catch (RuntimeException e) {
            model.addAttribute("company", companyDTO);
            model.addAttribute("errorMessage", mapCompanyErrorToKey(e.getMessage()));
            return "admin/companies/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String editCompanyForm(@PathVariable Long id, Model model) {
        try {
            CompanyDTO company = companyService.getCompanyById(id);
            model.addAttribute("company", company);
            model.addAttribute("isSuperAdmin", tenantContextService.isSuperAdmin());
            List<com.printflow.entity.AuditLog> overrideLogs = auditLogService.getByEntity("Company", id)
                .stream()
                .filter(log -> {
                    String desc = log.getDescription() != null ? log.getDescription().toLowerCase() : "";
                    String newValue = log.getNewValue() != null ? log.getNewValue().toLowerCase() : "";
                    return desc.contains("override")
                        || desc.contains("trial")
                        || newValue.contains("override")
                        || newValue.contains("trial");
                })
                .limit(10)
                .collect(Collectors.toList());
            model.addAttribute("overrideLogs", overrideLogs);
            return "admin/companies/edit";
        } catch (RuntimeException e) {
            return redirectWithError("/admin/companies", "admin.companies.flash.not_found", model);
        }
    }

    @PostMapping("/edit/{id}")
    public String updateCompany(@PathVariable Long id,
                                @ModelAttribute CompanyDTO companyDTO,
                                @RequestParam(value = "logo", required = false) org.springframework.web.multipart.MultipartFile logo,
                                Model model) {
        try {
            if (!tenantContextService.isSuperAdmin()) {
                CompanyDTO existing = companyService.getCompanyById(id);
                companyDTO.setBillingOverrideActive(existing.isBillingOverrideActive());
                companyDTO.setBillingOverrideUntil(existing.getBillingOverrideUntil());
            }
            companyService.updateCompany(id, companyDTO);
            if (logo != null && !logo.isEmpty()) {
                companyService.updateLogo(id, logo);
            }
            return redirectWithSuccess("/admin/companies", "admin.companies.flash.updated", model);
        } catch (IOException | RuntimeException e) {
            model.addAttribute("company", companyDTO);
            model.addAttribute("errorMessage", mapCompanyErrorToKey(e.getMessage()));
            return "admin/companies/edit";
        }
    }

    @PostMapping("/disable/{id}")
    public String disableCompany(@PathVariable Long id, Model model) {
        if (!tenantContextService.isSuperAdmin()) {
            return redirectWithError("/admin/companies", "billing.override.forbidden", model);
        }
        try {
            companyService.disableCompany(id);
            return redirectWithSuccess("/admin/companies", "admin.companies.flash.disabled", model);
        } catch (RuntimeException e) {
            return redirectWithError("/admin/companies", mapCompanyErrorToKey(e.getMessage()), model);
        }
    }

    @PostMapping("/enable/{id}")
    public String enableCompany(@PathVariable Long id, Model model) {
        if (!tenantContextService.isSuperAdmin()) {
            return redirectWithError("/admin/companies", "billing.override.forbidden", model);
        }
        try {
            companyService.enableCompany(id);
            return redirectWithSuccess("/admin/companies", "admin.companies.flash.enabled", model);
        } catch (RuntimeException e) {
            return redirectWithError("/admin/companies", mapCompanyErrorToKey(e.getMessage()), model);
        }
    }

    @PostMapping("/edit/{id}/billing-override")
    public String setBillingOverride(@PathVariable Long id,
                                     @RequestParam(defaultValue = "false") boolean active,
                                     @RequestParam(required = false) String until,
                                     Model model) {
        if (!tenantContextService.isSuperAdmin()) {
            return redirectWithError("/admin/companies/edit/" + id, "billing.override.forbidden", model);
        }
        java.time.LocalDateTime untilDate = null;
        if (until != null && !until.isBlank()) {
            try {
                untilDate = java.time.LocalDateTime.parse(until);
            } catch (DateTimeParseException ex) {
                return redirectWithError("/admin/companies/edit/" + id, "admin.companies.flash.invalid_date_format", model);
            }
        }
        companyService.setBillingOverride(id, active, untilDate);
        auditLogService.log(AuditAction.UPDATE, "Company", id, null, active ? "billing_override_on" : "billing_override_off",
            "Updated billing override");
        return redirectWithSuccess("/admin/companies/edit/" + id, "billing.override.updated", model);
    }

    @PostMapping("/edit/{id}/billing-pro-30")
    public String activateProTrial(@PathVariable Long id, Model model) {
        if (!tenantContextService.isSuperAdmin()) {
            return redirectWithError("/admin/companies/edit/" + id, "billing.override.forbidden", model);
        }
        companyService.activateProTrial(id, 30);
        auditLogService.log(AuditAction.UPDATE, "Company", id, null, "trial_pro_30",
            "Activated PRO trial for 30 days");
        return redirectWithSuccess("/admin/companies/edit/" + id, "billing.override.updated", model);
    }

    @PostMapping("/edit/{id}/billing-pro-365")
    public String activateProOneYear(@PathVariable Long id, Model model) {
        if (!tenantContextService.isSuperAdmin()) {
            return redirectWithError("/admin/companies/edit/" + id, "billing.override.forbidden", model);
        }
        companyService.activateProOverrideForDays(id, 365);
        auditLogService.log(AuditAction.UPDATE, "Company", id, null, "override_pro_365",
            "Activated PRO override for 1 year");
        return redirectWithSuccess("/admin/companies/edit/" + id, "billing.override.updated", model);
    }

    @GetMapping("/{id}/logo")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> companyLogo(@PathVariable Long id) {
        try {
            byte[] data = companyBrandingService.loadLogo(id);
            return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
        } catch (IOException | RuntimeException ex) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }

    private String mapCompanyErrorToKey(String message) {
        if (message == null || message.isBlank()) {
            return "admin.companies.error.generic";
        }
        String normalized = message.trim();
        return switch (normalized) {
            case "Company not found" -> "admin.companies.flash.not_found";
            case "Company name is required" -> "admin.companies.error.name_required";
            case "Company name already exists" -> "admin.companies.error.name_exists";
            case "Logo must be PNG, JPG or SVG" -> "admin.companies.error.logo_type";
            default -> "admin.companies.error.generic";
        };
    }

    private com.printflow.entity.enums.PlanTier parsePlanTier(String plan) {
        if (plan == null || plan.isBlank()) {
            return null;
        }
        for (com.printflow.entity.enums.PlanTier value : com.printflow.entity.enums.PlanTier.values()) {
            if (value.name().equalsIgnoreCase(plan.trim())) {
                return value;
            }
        }
        return null;
    }
}
