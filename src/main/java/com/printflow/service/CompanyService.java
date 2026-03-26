package com.printflow.service;

import com.printflow.dto.CompanyDTO;
import com.printflow.dto.EmailMessage;
import com.printflow.entity.Company;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.printflow.util.SlugUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final WorkOrderRepository workOrderRepository;
    private final com.printflow.storage.FileStorage fileStorage;
    private final int trialDays;
    private final TemplateSeederService templateSeederService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BillingAccessService billingAccessService;

    @org.springframework.beans.factory.annotation.Autowired
    public CompanyService(CompanyRepository companyRepository,
                          UserRepository userRepository,
                          ClientRepository clientRepository,
                          WorkOrderRepository workOrderRepository,
                          com.printflow.storage.FileStorage fileStorage,
                          @Value("${app.billing.trial-days:14}") int trialDays,
                          TemplateSeederService templateSeederService,
                          NotificationService notificationService,
                          EmailService emailService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.workOrderRepository = workOrderRepository;
        this.fileStorage = fileStorage;
        this.trialDays = trialDays;
        this.templateSeederService = templateSeederService;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    // Backward-compatible constructor used by older tests that do not need outbound email sending.
    public CompanyService(CompanyRepository companyRepository,
                          UserRepository userRepository,
                          ClientRepository clientRepository,
                          WorkOrderRepository workOrderRepository,
                          com.printflow.storage.FileStorage fileStorage,
                          int trialDays,
                          TemplateSeederService templateSeederService,
                          NotificationService notificationService) {
        this(companyRepository, userRepository, clientRepository, workOrderRepository, fileStorage, trialDays,
            templateSeederService, notificationService, null);
    }

    public List<CompanyDTO> getCompanies(String search) {
        String normalizedSearch = search == null ? "" : search.trim();
        List<Company> companies;
        if (!normalizedSearch.isEmpty()) {
            companies = companyRepository.findByNameContainingIgnoreCase(normalizedSearch);
        } else {
            companies = companyRepository.findAll();
        }
        return companies.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Page<CompanyDTO> getCompanies(String search, Pageable pageable) {
        String normalizedSearch = search == null ? "" : search.trim();
        Page<Company> companies;
        if (!normalizedSearch.isEmpty()) {
            companies = companyRepository.findByNameContainingIgnoreCase(normalizedSearch, pageable);
        } else {
            companies = companyRepository.findAll(pageable);
        }
        return companies.map(this::toDTO);
    }

    public Page<CompanyDTO> getCompanies(String search,
                                         com.printflow.entity.enums.PlanTier plan,
                                         Boolean overrideActive,
                                         Pageable pageable) {
        String normalizedSearch = search == null ? "" : search.trim();
        boolean hasSearch = !normalizedSearch.isEmpty();
        if (hasSearch && plan != null && overrideActive != null) {
            return companyRepository.findByNameContainingIgnoreCaseAndPlanAndBillingOverrideActive(normalizedSearch, plan, overrideActive, pageable)
                .map(this::toDTO);
        }
        if (hasSearch && plan != null) {
            return companyRepository.findByNameContainingIgnoreCaseAndPlan(normalizedSearch, plan, pageable)
                .map(this::toDTO);
        }
        if (hasSearch && overrideActive != null) {
            return companyRepository.findByNameContainingIgnoreCaseAndBillingOverrideActive(normalizedSearch, overrideActive, pageable)
                .map(this::toDTO);
        }
        if (plan != null && overrideActive != null) {
            return companyRepository.findByPlanAndBillingOverrideActive(plan, overrideActive, pageable)
                .map(this::toDTO);
        }
        if (plan != null) {
            return companyRepository.findByPlan(plan, pageable).map(this::toDTO);
        }
        if (overrideActive != null) {
            return companyRepository.findByBillingOverrideActive(overrideActive, pageable).map(this::toDTO);
        }
        return getCompanies(normalizedSearch, pageable);
    }

    public CompanyDTO getCompanyById(Long id) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        return toDTO(company);
    }

    public CompanyDTO createCompany(CompanyDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("Company name is required");
        }
        if (companyRepository.existsByNormalizedName(dto.getName().trim())) {
            throw new RuntimeException("Company name already exists");
        }
        Company company = new Company();
        String companyName = dto.getName().trim();
        company.setName(companyName);
        company.setSlug(generateUniqueSlug(companyName, null));
        company.setEmail(normalizeNullable(dto.getEmail()));
        company.setPhone(normalizeNullable(dto.getPhone()));
        company.setAddress(normalizeNullable(dto.getAddress()));
        company.setWebsite(normalizeNullable(dto.getWebsite()));
        company.setLegalName(normalizeNullable(dto.getLegalName()));
        company.setTaxId(normalizeNullable(dto.getTaxId()));
        company.setRegistrationNumber(normalizeNullable(dto.getRegistrationNumber()));
        company.setBankAccount(normalizeNullable(dto.getBankAccount()));
        company.setBankName(normalizeNullable(dto.getBankName()));
        company.setBillingEmail(normalizeNullable(dto.getBillingEmail()));
        company.setPrimaryColor(normalizeNullable(dto.getPrimaryColor()));
        company.setCurrency(normalizeCurrency(dto.getCurrency()));
        company.setActive(dto.isActive());
        LocalDateTime now = LocalDateTime.now();
        company.setTrialStart(now);
        company.setTrialEnd(now.plusDays(Math.max(0, trialDays)));
        Company saved = companyRepository.save(company);
        templateSeederService.seedDefaultTemplates(saved);
        return toDTO(saved);
    }

    public CompanyDTO updateCompany(Long id, CompanyDTO dto) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));

        String newName = dto.getName() != null ? dto.getName().trim() : "";
        if (newName.isEmpty()) {
            throw new RuntimeException("Company name is required");
        }
        if (companyRepository.existsByNormalizedNameAndIdNot(newName, company.getId())) {
            throw new RuntimeException("Company name already exists");
        }

        company.setName(newName);
        company.setSlug(generateUniqueSlug(newName, company.getId()));
        company.setEmail(normalizeNullable(dto.getEmail()));
        company.setPhone(normalizeNullable(dto.getPhone()));
        company.setAddress(normalizeNullable(dto.getAddress()));
        company.setWebsite(normalizeNullable(dto.getWebsite()));
        company.setLegalName(normalizeNullable(dto.getLegalName()));
        company.setTaxId(normalizeNullable(dto.getTaxId()));
        company.setRegistrationNumber(normalizeNullable(dto.getRegistrationNumber()));
        company.setBankAccount(normalizeNullable(dto.getBankAccount()));
        company.setBankName(normalizeNullable(dto.getBankName()));
        company.setBillingEmail(normalizeNullable(dto.getBillingEmail()));
        company.setPrimaryColor(normalizeNullable(dto.getPrimaryColor()));
        company.setCurrency(normalizeCurrency(dto.getCurrency()));
        company.setSmtpHost(normalizeNullable(dto.getSmtpHost()));
        company.setSmtpPort(dto.getSmtpPort());
        company.setSmtpUser(normalizeNullable(dto.getSmtpUser()));
        if (dto.getSmtpPassword() != null && !dto.getSmtpPassword().isBlank()) {
            company.setSmtpPassword(dto.getSmtpPassword());
        }
        if (dto.getSmtpTls() != null) {
            company.setSmtpTls(dto.getSmtpTls());
        }
        company.setActive(dto.isActive());
        company.setBillingOverrideActive(dto.isBillingOverrideActive());
        company.setBillingOverrideUntil(dto.isBillingOverrideActive() ? dto.getBillingOverrideUntil() : null);
        Company saved = companyRepository.save(company);
        invalidateBillingCache(saved.getId());
        return toDTO(saved);
    }

    public CompanyDTO setBillingOverride(Long id, boolean active, LocalDateTime until) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        company.setBillingOverrideActive(active);
        company.setBillingOverrideUntil(active ? until : null);
        if (active) {
            company.setPlan(com.printflow.entity.enums.PlanTier.PRO);
            company.setPlanUpdatedAt(LocalDateTime.now());
        }
        Company saved = companyRepository.save(company);
        invalidateBillingCache(saved.getId());
        return toDTO(saved);
    }

    public CompanyDTO activateProTrial(Long id, int days) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        LocalDateTime now = LocalDateTime.now();
        company.setPlan(com.printflow.entity.enums.PlanTier.PRO);
        company.setPlanUpdatedAt(now);
        company.setTrialStart(now);
        company.setTrialEnd(now.plusDays(Math.max(0, days)));
        company.setBillingOverrideActive(false);
        company.setBillingOverrideUntil(null);
        Company saved = companyRepository.save(company);
        invalidateBillingCache(saved.getId());
        return toDTO(saved);
    }

    public CompanyDTO activateProOverrideForDays(Long id, int days) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        LocalDateTime now = LocalDateTime.now();
        company.setPlan(com.printflow.entity.enums.PlanTier.PRO);
        company.setPlanUpdatedAt(now);
        company.setBillingOverrideActive(true);
        company.setBillingOverrideUntil(now.plusDays(Math.max(0, days)));
        Company saved = companyRepository.save(company);
        invalidateBillingCache(saved.getId());
        return toDTO(saved);
    }

    public void sendTestSmtpEmail(Long companyId, String toEmail) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        notificationService.sendCompanySmtpTest(company, toEmail);
    }

    public void sendSuperAdminCompanyMessage(Long companyId,
                                             String toEmail,
                                             String subject,
                                             String body,
                                             String messageType) {
        sendSuperAdminCompanyMessage(companyId, toEmail, subject, body, messageType, null, null, null);
    }

    public void sendSuperAdminCompanyMessage(Long companyId,
                                             String toEmail,
                                             String subject,
                                             String body,
                                             String messageType,
                                             String invoiceNumber,
                                             String invoiceAmount,
                                             String invoiceDueDate) {
        if (emailService == null) {
            throw new RuntimeException("Email service is not configured");
        }
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        String normalizedTo = normalizeNullable(toEmail);
        String normalizedSubject = normalizeNullable(subject);
        String normalizedBody = normalizeNullable(body);
        String normalizedType = normalizeMessageType(normalizeNullable(messageType));
        String normalizedInvoiceNumber = normalizeNullable(invoiceNumber);
        String normalizedInvoiceAmount = normalizeNullable(invoiceAmount);
        String normalizedInvoiceDueDate = normalizeNullable(invoiceDueDate);
        if (normalizedTo == null) {
            throw new RuntimeException("Company recipient email is required");
        }
        if (!normalizedTo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new RuntimeException("Company recipient email is invalid");
        }
        if (normalizedSubject == null) {
            throw new RuntimeException("Email subject is required");
        }
        if (normalizedBody == null) {
            throw new RuntimeException("Email body is required");
        }
        EmailMessage message = new EmailMessage();
        message.setTo(normalizedTo);
        message.setSubject(normalizedSubject);
        String plainBody = normalizedBody + buildInvoiceDetailsPlain(normalizedType, normalizedInvoiceNumber, normalizedInvoiceAmount, normalizedInvoiceDueDate);
        message.setTextBody(plainBody);
        message.setHtmlBody(buildSuperAdminHtmlMessage(company, normalizedBody, normalizedType,
            normalizedInvoiceNumber, normalizedInvoiceAmount, normalizedInvoiceDueDate));
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>();
        metadata.put("type", normalizedType != null ? normalizedType : "general");
        if (normalizedInvoiceNumber != null) {
            metadata.put("invoiceNumber", normalizedInvoiceNumber);
        }
        if (normalizedInvoiceAmount != null) {
            metadata.put("invoiceAmount", normalizedInvoiceAmount);
        }
        if (normalizedInvoiceDueDate != null) {
            metadata.put("invoiceDueDate", normalizedInvoiceDueDate);
        }
        message.setMetadata(metadata);
        emailService.sendNow(message, null, "superadmin-company-message");
    }

    public void updateLogo(Long id, org.springframework.web.multipart.MultipartFile logoFile) throws java.io.IOException {
        if (logoFile == null || logoFile.isEmpty()) {
            return;
        }
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));

        String original = logoFile.getOriginalFilename() != null ? logoFile.getOriginalFilename() : "";
        String lower = original.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg"))) {
            throw new RuntimeException("Logo must be PNG, JPG or SVG");
        }
        com.printflow.storage.StoredFile stored = fileStorage.store(logoFile, "/company_" + id + "/branding", false);
        if (company.getLogoPath() != null && !company.getLogoPath().isBlank()) {
            fileStorage.deleteIfExists(company.getLogoPath());
        }
        company.setLogoPath(stored.getFilePath());
        companyRepository.save(company);
    }

    public void disableCompany(Long id) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        company.setActive(false);
        companyRepository.save(company);
        invalidateBillingCache(company.getId());
    }

    public void enableCompany(Long id) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        company.setActive(true);
        companyRepository.save(company);
        invalidateBillingCache(company.getId());
    }

    private void invalidateBillingCache(Long companyId) {
        if (billingAccessService != null) {
            billingAccessService.invalidateCompanyCache(companyId);
        }
    }

    private CompanyDTO toDTO(Company company) {
        long usersCount = userRepository.countByCompany_Id(company.getId());
        long activeUsersCount = userRepository.countByCompany_IdAndActiveTrue(company.getId());
        long clientsCount = clientRepository.countByCompany_Id(company.getId());
        long ordersCount = workOrderRepository.countByCompany_Id(company.getId());
        CompanyDTO dto = new CompanyDTO(
            company.getId(),
            company.getName(),
            company.isActive(),
            company.getCreatedAt(),
            company.getUpdatedAt(),
            usersCount,
            activeUsersCount,
            clientsCount,
            ordersCount
        );
        dto.setEmail(company.getEmail());
        dto.setPhone(company.getPhone());
        dto.setAddress(company.getAddress());
        dto.setWebsite(company.getWebsite());
        dto.setLegalName(company.getLegalName());
        dto.setTaxId(company.getTaxId());
        dto.setRegistrationNumber(company.getRegistrationNumber());
        dto.setBankAccount(company.getBankAccount());
        dto.setBankName(company.getBankName());
        dto.setBillingEmail(company.getBillingEmail());
        dto.setPrimaryColor(company.getPrimaryColor());
        dto.setLogoPath(company.getLogoPath());
        dto.setCurrency(company.getCurrency());
        dto.setSmtpHost(company.getSmtpHost());
        dto.setSmtpPort(company.getSmtpPort());
        dto.setSmtpUser(company.getSmtpUser());
        dto.setSmtpPassword(null);
        dto.setSmtpTls(company.getSmtpTls());
        dto.setPlan(company.getPlan());
        dto.setBillingOverrideActive(company.isBillingOverrideActive());
        dto.setBillingOverrideUntil(company.getBillingOverrideUntil());
        return dto;
    }

    private String generateUniqueSlug(String name, Long companyIdToIgnore) {
        String base = SlugUtil.toSlug(name);
        String slug = base;
        int i = 2;
        while (true) {
            var existing = companyRepository.findBySlug(slug);
            if (existing.isEmpty() || (companyIdToIgnore != null && companyIdToIgnore.equals(existing.get().getId()))) {
                return slug;
            }
            slug = base + "-" + i++;
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "RSD";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildSuperAdminHtmlMessage(Company company,
                                              String body,
                                              String messageType,
                                              String invoiceNumber,
                                              String invoiceAmount,
                                              String invoiceDueDate) {
        String safeBody = escapeHtml(body).replace("\n", "<br/>");
        String invoiceHtml = buildInvoiceDetailsHtml(messageType, invoiceNumber, invoiceAmount, invoiceDueDate);
        String typeLabel = messageType != null ? messageType.toUpperCase(Locale.ROOT) : "GENERAL";
        String companyName = company.getName() != null ? company.getName() : "Company";
        return """
            <div style="font-family:Arial,sans-serif;color:#111827;">
              <p style="margin:0 0 8px 0;font-size:12px;color:#6b7280;">PrintFlow Super Admin • %s</p>
              <h2 style="margin:0 0 12px 0;">%s</h2>
              <div style="line-height:1.6;">%s</div>
              %s
            </div>
            """.formatted(typeLabel, companyName, safeBody, invoiceHtml);
    }

    private String buildInvoiceDetailsPlain(String messageType,
                                            String invoiceNumber,
                                            String invoiceAmount,
                                            String invoiceDueDate) {
        if (!"invoice".equalsIgnoreCase(messageType)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (invoiceNumber != null || invoiceAmount != null || invoiceDueDate != null) {
            sb.append("\n\n--- Invoice details ---");
            if (invoiceNumber != null) {
                sb.append("\nInvoice number: ").append(invoiceNumber);
            }
            if (invoiceAmount != null) {
                sb.append("\nAmount: ").append(invoiceAmount);
            }
            if (invoiceDueDate != null) {
                sb.append("\nDue date: ").append(invoiceDueDate);
            }
        }
        return sb.toString();
    }

    private String buildInvoiceDetailsHtml(String messageType,
                                           String invoiceNumber,
                                           String invoiceAmount,
                                           String invoiceDueDate) {
        if (!"invoice".equalsIgnoreCase(messageType)) {
            return "";
        }
        if (invoiceNumber == null && invoiceAmount == null && invoiceDueDate == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin-top:16px;padding:12px;border:1px solid #e5e7eb;border-radius:8px;background:#f9fafb;\">");
        sb.append("<p style=\"margin:0 0 8px 0;font-weight:600;\">Invoice details</p>");
        if (invoiceNumber != null) {
            sb.append("<p style=\"margin:0 0 4px 0;\">Invoice number: ").append(escapeHtml(invoiceNumber)).append("</p>");
        }
        if (invoiceAmount != null) {
            sb.append("<p style=\"margin:0 0 4px 0;\">Amount: ").append(escapeHtml(invoiceAmount)).append("</p>");
        }
        if (invoiceDueDate != null) {
            sb.append("<p style=\"margin:0;\">Due date: ").append(escapeHtml(invoiceDueDate)).append("</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String normalizeMessageType(String rawType) {
        if (rawType == null) {
            return "general";
        }
        String normalizedType = rawType.toLowerCase(java.util.Locale.ROOT);
        return switch (normalizedType) {
            case "invoice", "billing_notice", "general" -> normalizedType;
            default -> "general";
        };
    }
}
