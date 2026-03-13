package com.printflow.controller;

import com.printflow.dto.MailSettingsDTO;
import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.entity.enums.EmailOutboxStatus;
import com.printflow.service.CompanyService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.EmailService;
import com.printflow.service.EmailOutboxService;
import com.printflow.service.MailSettingsService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/settings/email")
public class EmailSettingsController extends BaseController {

    private final CurrentContextService currentContextService;
    private final MailSettingsService mailSettingsService;
    private final CompanyService companyService;
    private final EmailService emailService;
    private final EmailOutboxService emailOutboxService;

    public EmailSettingsController(CurrentContextService currentContextService,
                                   MailSettingsService mailSettingsService,
                                   CompanyService companyService,
                                   EmailService emailService,
                                   EmailOutboxService emailOutboxService) {
        this.currentContextService = currentContextService;
        this.mailSettingsService = mailSettingsService;
        this.companyService = companyService;
        this.emailService = emailService;
        this.emailOutboxService = emailOutboxService;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping
    public String settings(@RequestParam(required = false) String errorKey,
                           @RequestParam(required = false) String errorMessage,
                           @RequestParam(required = false) String successKey,
                           @RequestParam(required = false) Long cleanupCount,
                           @RequestParam(required = false) EmailOutboxStatus outboxStatus,
                           @RequestParam(defaultValue = "0") int outboxPage,
                           Model model) {
        Company company = currentContextService.currentCompany();
        MailSettings settings = mailSettingsService.getOrCreate(company);
        String smtpSource = mailSettingsService.resolveSmtpSource(company, settings);
        int safeOutboxPage = Math.max(0, outboxPage);
        Page<com.printflow.entity.EmailOutbox> outboxEntries = emailOutboxService.listForCompany(company, outboxStatus, safeOutboxPage, 20);
        model.addAttribute("company", companyService.getCompanyById(company.getId()));
        model.addAttribute("settings", mailSettingsService.toDto(settings));
        model.addAttribute("passwordSet", settings.getSmtpPasswordEnc() != null && !settings.getSmtpPasswordEnc().isBlank());
        model.addAttribute("smtpConfigured", !"none".equals(smtpSource));
        model.addAttribute("smtpSource", smtpSource);
        model.addAttribute("errorKey", errorKey);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("successKey", successKey);
        model.addAttribute("cleanupCount", cleanupCount);
        model.addAttribute("outboxStatus", outboxStatus);
        model.addAttribute("outboxEntries", outboxEntries);
        model.addAttribute("outboxStatuses", EmailOutboxStatus.values());
        model.addAttribute("outboxTotal", emailOutboxService.totalForCompany(company));
        model.addAttribute("outboxSent", emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.SENT));
        model.addAttribute("outboxFailed", emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.FAILED));
        model.addAttribute("outboxPending", emailOutboxService.countForCompanyByStatus(company, EmailOutboxStatus.PENDING));
        return "admin/settings/email";
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    public String update(@RequestParam(required = false) Boolean enabled,
                         @RequestParam(required = false) String smtpHost,
                         @RequestParam(required = false) Integer smtpPort,
                         @RequestParam(required = false) String smtpUsername,
                         @RequestParam(required = false) String smtpPassword,
                         @RequestParam(required = false) Boolean smtpUseTls,
                         @RequestParam(required = false) Boolean smtpUseSsl,
                         @RequestParam(required = false) String fromEmail,
                         @RequestParam(required = false) String fromName,
                         Model model) {
        Company company = currentContextService.currentCompany();
        MailSettingsDTO dto = new MailSettingsDTO();
        dto.setEnabled(Boolean.TRUE.equals(enabled));
        dto.setSmtpHost(smtpHost);
        dto.setSmtpPort(smtpPort);
        dto.setSmtpUsername(smtpUsername);
        dto.setSmtpPassword(smtpPassword);
        dto.setSmtpUseTls(smtpUseTls);
        dto.setSmtpUseSsl(smtpUseSsl);
        dto.setFromEmail(fromEmail);
        dto.setFromName(fromName);

        String validationError = validate(dto, company);
        if (validationError != null) {
            model.addAttribute("company", companyService.getCompanyById(company.getId()));
            model.addAttribute("settings", dto);
            model.addAttribute("passwordSet", mailSettingsService.getOrCreate(company).getSmtpPasswordEnc() != null);
            model.addAttribute("errorKey", validationError);
            return "admin/settings/email";
        }
        mailSettingsService.saveFromDto(company, dto);
        return "redirect:/settings/email?successKey=email.settings.saved";
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/test")
    public String test(@RequestParam String toEmail) {
        Company company = currentContextService.currentCompany();
        if (toEmail == null || toEmail.isBlank() || !toEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "redirect:/settings/email?errorKey=company.smtp.error.invalid_email";
        }
        MailSettings settings = mailSettingsService.getOrCreate(company);
        if (!mailSettingsService.isConfigured(settings) || !Boolean.TRUE.equals(settings.getEnabled())) {
            return "redirect:/settings/email?errorKey=company.smtp.error.not_configured";
        }
        try {
            com.printflow.dto.EmailMessage msg = new com.printflow.dto.EmailMessage();
            msg.setTo(toEmail.trim());
            msg.setSubject("SMTP test - " + (company.getName() != null ? company.getName() : "PrintFlow"));
            msg.setHtmlBody("<p>This is a test message sent from your company SMTP settings.</p>");
            emailService.sendNow(msg, company, "smtp-test");
            return "redirect:/settings/email?successKey=company.smtp.test_sent";
        } catch (Exception e) {
            String detail = extractErrorDetail(e);
            return "redirect:/settings/email?errorKey=company.smtp.test_failed&errorMessage="
                + UriUtils.encode(detail, StandardCharsets.UTF_8);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/outbox/cleanup-failed")
    public String cleanupFailed(@RequestParam(defaultValue = "30") int days) {
        Company company = currentContextService.currentCompany();
        long deleted = emailOutboxService.cleanupFailed(company, days);
        return "redirect:/settings/email?successKey=email.outbox.cleanup_success&cleanupCount=" + deleted;
    }

    private String extractErrorDetail(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return "Unknown SMTP error";
        }
        if (msg.length() > 300) {
            return msg.substring(0, 300);
        }
        return msg;
    }

    private String validate(MailSettingsDTO dto, Company company) {
        boolean anyProvided = (dto.getSmtpHost() != null && !dto.getSmtpHost().isBlank())
            || dto.getSmtpPort() != null
            || (dto.getSmtpUsername() != null && !dto.getSmtpUsername().isBlank())
            || (dto.getSmtpPassword() != null && !dto.getSmtpPassword().isBlank());
        if (!anyProvided) {
            return null;
        }
        if (dto.getSmtpHost() == null || dto.getSmtpHost().isBlank()) {
            return "company.smtp.error.host_required";
        }
        if (dto.getSmtpPort() == null || dto.getSmtpPort() < 1 || dto.getSmtpPort() > 65535) {
            return "company.smtp.error.port_invalid";
        }
        if (dto.getSmtpUsername() == null || dto.getSmtpUsername().isBlank()) {
            return "company.smtp.error.user_required";
        }
        boolean hasPassword = dto.getSmtpPassword() != null && !dto.getSmtpPassword().isBlank();
        boolean existingPassword = mailSettingsService.getOrCreate(company).getSmtpPasswordEnc() != null;
        if (!hasPassword && !existingPassword) {
            return "company.smtp.error.password_required";
        }
        if (dto.getFromEmail() != null && !dto.getFromEmail().isBlank()
            && !dto.getFromEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "company.smtp.error.invalid_email";
        }
        return null;
    }

}
