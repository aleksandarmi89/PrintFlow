package com.printflow.controller;

import com.printflow.dto.CompanyDTO;
import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.service.CompanyBrandingService;
import com.printflow.service.CompanyService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.MailSettingsService;
import com.printflow.service.TenantContextService;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/company")
public class AdminCompanySettingsController extends BaseController {

    private final CompanyService companyService;
    private final CompanyBrandingService companyBrandingService;
    private final CurrentContextService currentContextService;
    private final TenantContextService tenantContextService;
    private final MailSettingsService mailSettingsService;
    private final boolean emailFallbackEnabled;

    public AdminCompanySettingsController(CompanyService companyService,
                                          CompanyBrandingService companyBrandingService,
                                          CurrentContextService currentContextService,
                                          MailSettingsService mailSettingsService,
                                          TenantContextService tenantContextService,
                                          @org.springframework.beans.factory.annotation.Value("${app.notification.email.fallback-enabled:false}") boolean emailFallbackEnabled) {
        this.companyService = companyService;
        this.companyBrandingService = companyBrandingService;
        this.currentContextService = currentContextService;
        this.mailSettingsService = mailSettingsService;
        this.tenantContextService = tenantContextService;
        this.emailFallbackEnabled = emailFallbackEnabled;
    }

    @GetMapping
    public String settings(@RequestParam(required = false) String errorKey,
                           @RequestParam(required = false) String successKey,
                           Model model) {
        Company company = currentContextService.currentCompany();
        CompanyDTO dto = companyService.getCompanyById(company.getId());
        var mailSettings = mailSettingsService.getOrCreate(company);
        String smtpSource = resolveSmtpSource(company, mailSettings);
        model.addAttribute("company", dto);
        model.addAttribute("smtpPasswordSet", mailSettings.getSmtpPasswordEnc() != null && !mailSettings.getSmtpPasswordEnc().isBlank());
        model.addAttribute("smtpConfigured", !"none".equals(smtpSource));
        model.addAttribute("smtpSource", smtpSource);
        model.addAttribute("smtpFallbackEnabled", emailFallbackEnabled);
        model.addAttribute("errorKey", errorKey);
        model.addAttribute("successKey", successKey);
        return "admin/company/settings";
    }

    @PostMapping
    public String update(@RequestParam(required = false) Long id,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String phone,
                         @RequestParam(required = false) String address,
                         @RequestParam(required = false) String website,
                         @RequestParam(required = false) String primaryColor,
                         @RequestParam(required = false) String currency,
                         @RequestParam(required = false) String smtpHost,
                         @RequestParam(required = false) Integer smtpPort,
                         @RequestParam(required = false) String smtpUser,
                         @RequestParam(required = false) String smtpPassword,
                         @RequestParam(required = false) Boolean smtpTls,
                         @RequestParam(value = "logo", required = false) org.springframework.web.multipart.MultipartFile logo,
                         Model model) {
        Company company = currentContextService.currentCompany();
        CompanyDTO dto = new CompanyDTO();
        dto.setId(company.getId());
        if (tenantContextService.isSuperAdmin()) {
            dto.setName(name);
        } else {
            dto.setName(company.getName());
        }
        dto.setEmail(email);
        dto.setPhone(phone);
        dto.setAddress(address);
        dto.setWebsite(website);
        dto.setPrimaryColor(primaryColor);
        dto.setCurrency(currency);
        dto.setSmtpHost(smtpHost);
        dto.setSmtpPort(smtpPort);
        dto.setSmtpUser(smtpUser);
        dto.setSmtpPassword(smtpPassword);
        dto.setSmtpTls(smtpTls);
        dto.setActive(company.isActive());
        String validationError = validateSmtpSettings(company, smtpHost, smtpPort, smtpUser, smtpPassword);
        if (validationError != null) {
            var mailSettings = mailSettingsService.getOrCreate(company);
            model.addAttribute("company", dto);
            model.addAttribute("smtpPasswordSet", mailSettings.getSmtpPasswordEnc() != null && !mailSettings.getSmtpPasswordEnc().isBlank());
            model.addAttribute("errorKey", validationError);
            return "admin/company/settings";
        }
        try {
            companyService.updateCompany(company.getId(), dto);
            if (logo != null && !logo.isEmpty()) {
                companyService.updateLogo(company.getId(), logo);
            }
            return redirectWithSuccess("/admin/company", "company.settings.updated", model);
        } catch (Exception e) {
            var mailSettings = mailSettingsService.getOrCreate(company);
            model.addAttribute("company", dto);
            model.addAttribute("smtpPasswordSet", mailSettings.getSmtpPasswordEnc() != null && !mailSettings.getSmtpPasswordEnc().isBlank());
            model.addAttribute("errorMessage", e.getMessage() != null ? e.getMessage() : "company.settings.update_failed");
            return "admin/company/settings";
        }
    }

    @PostMapping("/test-smtp")
    public String testSmtp(@RequestParam String toEmail, Model model) {
        Company company = currentContextService.currentCompany();
        if (toEmail == null || toEmail.isBlank() || !toEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "redirect:/admin/company?errorKey=company.smtp.error.invalid_email";
        }
        if (!isSmtpConfigured(company)) {
            return "redirect:/admin/company?errorKey=company.smtp.error.not_configured";
        }
        try {
            companyService.sendTestSmtpEmail(company.getId(), toEmail.trim());
            return "redirect:/admin/company?successKey=company.smtp.test_sent";
        } catch (Exception e) {
            return "redirect:/admin/company?errorKey=company.smtp.test_failed";
        }
    }

    private String validateSmtpSettings(Company company, String host, Integer port, String user, String password) {
        boolean anyProvided = (host != null && !host.isBlank())
            || port != null
            || (user != null && !user.isBlank())
            || (password != null && !password.isBlank());
        if (!anyProvided) {
            return null;
        }
        if (host == null || host.isBlank()) {
            return "company.smtp.error.host_required";
        }
        if (port == null || port < 1 || port > 65535) {
            return "company.smtp.error.port_invalid";
        }
        if (user == null || user.isBlank()) {
            return "company.smtp.error.user_required";
        }
        boolean hasPassword = password != null && !password.isBlank();
        var mailSettings = mailSettingsService.getOrCreate(company);
        boolean existingPassword = mailSettings.getSmtpPasswordEnc() != null
            && !mailSettings.getSmtpPasswordEnc().isBlank();
        if (!hasPassword && !existingPassword) {
            return "company.smtp.error.password_required";
        }
        return null;
    }

    private boolean isSmtpConfigured(Company company) {
        if (company == null) {
            return false;
        }
        return !"none".equals(resolveSmtpSource(company, mailSettingsService.getOrCreate(company)));
    }

    private String resolveSmtpSource(Company company, MailSettings settings) {
        if (company == null) {
            return "none";
        }
        if (mailSettingsService.isConfigured(settings)) {
            return "mail_settings";
        }
        boolean legacyConfigured = company.getSmtpHost() != null && !company.getSmtpHost().isBlank()
            && company.getSmtpPort() != null
            && company.getSmtpUser() != null && !company.getSmtpUser().isBlank()
            && company.getSmtpPassword() != null && !company.getSmtpPassword().isBlank();
        return legacyConfigured ? "legacy_company" : "none";
    }

    @GetMapping("/logo")
    @ResponseBody
    public ResponseEntity<byte[]> currentCompanyLogo() {
        Company company = currentContextService.currentCompany();
        if (company == null || company.getLogoPath() == null || company.getLogoPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] data = companyBrandingService.loadLogo(company.getId());
            MediaType contentType = MediaTypeFactory.getMediaType(company.getLogoPath())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().contentType(contentType).body(data);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
