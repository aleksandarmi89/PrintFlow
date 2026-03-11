package com.printflow.service;

import com.printflow.dto.EmailMessage;
import com.printflow.dto.PublicOrderRequestForm;
import com.printflow.entity.Company;
import com.printflow.entity.PublicOrderRequest;
import com.printflow.entity.PublicOrderRequestAttachment;
import com.printflow.entity.User;
import com.printflow.entity.enums.PublicOrderRequestSourceChannel;
import com.printflow.entity.enums.PublicOrderRequestStatus;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.PublicOrderRequestAttachmentRepository;
import com.printflow.repository.PublicOrderRequestRepository;
import com.printflow.repository.UserRepository;
import com.printflow.storage.FileStorage;
import com.printflow.storage.StoredFile;
import com.printflow.util.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class PublicOrderRequestService {
    private static final Logger log = LoggerFactory.getLogger(PublicOrderRequestService.class);

    private final PublicOrderRequestRepository requestRepository;
    private final PublicOrderRequestAttachmentRepository attachmentRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TenantGuard tenantGuard;
    private final FileStorage fileStorage;
    private final RateLimitService rateLimitService;
    private final EmailService emailService;
    private final int publicMaxFiles;
    private final long publicMaxFileBytes;
    private final boolean rateLimitEnabled;
    private final int rateLimitMax;
    private final int rateLimitWindowSeconds;
    private final String baseUrl;

    public PublicOrderRequestService(PublicOrderRequestRepository requestRepository,
                                     PublicOrderRequestAttachmentRepository attachmentRepository,
                                     CompanyRepository companyRepository,
                                     UserRepository userRepository,
                                     TenantGuard tenantGuard,
                                     FileStorage fileStorage,
                                     RateLimitService rateLimitService,
                                     EmailService emailService,
                                     @Value("${app.public-request.max-files:5}") int publicMaxFiles,
                                     @Value("${app.public-request.max-file-bytes:15728640}") long publicMaxFileBytes,
                                     @Value("${app.public-request.rate-limit.enabled:true}") boolean rateLimitEnabled,
                                     @Value("${app.public-request.rate-limit.max-requests:10}") int rateLimitMax,
                                     @Value("${app.public-request.rate-limit.window-seconds:300}") int rateLimitWindowSeconds,
                                     @Value("${app.base-url:http://localhost:8088}") String baseUrl) {
        this.requestRepository = requestRepository;
        this.attachmentRepository = attachmentRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.tenantGuard = tenantGuard;
        this.fileStorage = fileStorage;
        this.rateLimitService = rateLimitService;
        this.emailService = emailService;
        this.publicMaxFiles = Math.max(1, publicMaxFiles);
        this.publicMaxFileBytes = Math.max(1024L, publicMaxFileBytes);
        this.rateLimitEnabled = rateLimitEnabled;
        this.rateLimitMax = Math.max(1, rateLimitMax);
        this.rateLimitWindowSeconds = Math.max(10, rateLimitWindowSeconds);
        this.baseUrl = baseUrl;
    }

    public Company requireActiveCompanyBySlug(String companySlug) {
        return companyRepository.findBySlugAndActiveTrue(companySlug)
            .orElseThrow(() -> new PublicOrderRequestException("public.order.error.company_not_found"));
    }

    public Company requireActiveCompanyById(Long companyId) {
        return companyRepository.findById(companyId)
            .filter(Company::isActive)
            .orElseThrow(() -> new PublicOrderRequestException("public.order.error.company_not_found"));
    }

    public String ensureCompanySlug(Company company) {
        if (company == null) {
            throw new PublicOrderRequestException("public.order.error.company_not_found");
        }
        if (company.getSlug() != null && !company.getSlug().isBlank()) {
            return company.getSlug();
        }
        String base = SlugUtil.toSlug(company.getName());
        if (base == null || base.isBlank()) {
            base = "company";
        }
        String candidate = base;
        if (companyRepository.existsBySlug(candidate)) {
            candidate = base + "-" + company.getId();
        }
        company.setSlug(candidate);
        companyRepository.save(company);
        return candidate;
    }

    public PublicOrderRequest createPublicRequest(String companySlug,
                                                  PublicOrderRequestForm form,
                                                  List<MultipartFile> files,
                                                  String remoteIp) {
        Company company = requireActiveCompanyBySlug(companySlug);
        enforceRateLimit(companySlug, remoteIp);

        PublicOrderRequest request = new PublicOrderRequest();
        request.setCompany(company);
        request.setCustomerName(trimOrNull(form.getCustomerName()));
        request.setCustomerEmail(trimOrNull(form.getCustomerEmail()));
        request.setCustomerPhone(trimOrNull(form.getCustomerPhone()));
        request.setCustomerCompanyName(trimOrNull(form.getCustomerCompanyName()));
        request.setProductType(trimOrNull(form.getProductType()));
        request.setQuantity(form.getQuantity());
        request.setDimensions(trimOrNull(form.getDimensions()));
        request.setMaterial(trimOrNull(form.getMaterial()));
        request.setFinishing(trimOrNull(form.getFinishing()));
        request.setDeadline(form.getDeadline());
        request.setNotes(trimOrNull(form.getNotes()));
        request.setStatus(PublicOrderRequestStatus.NEW);
        request.setSourceChannel(PublicOrderRequestSourceChannel.PUBLIC_FORM);
        PublicOrderRequest saved = requestRepository.save(request);
        log.info("public_request_created requestId={} companyId={} source={} ip={}",
            saved.getId(), company.getId(), saved.getSourceChannel(), remoteIp);

        storeAttachments(saved, company, files);
        sendSubmitEmails(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<PublicOrderRequest> listForCurrentTenant(PublicOrderRequestStatus status, String search, Pageable pageable) {
        Long companyId = tenantGuard.requireCompanyId();
        if (search != null && !search.isBlank()) {
            if (status != null) {
                return requestRepository.searchByCompanyAndStatus(companyId, status, search.trim(), pageable);
            }
            return requestRepository.searchByCompany(companyId, search.trim(), pageable);
        }
        if (status != null) {
            return requestRepository.findByCompany_IdAndStatusOrderByCreatedAtDesc(companyId, status, pageable);
        }
        return requestRepository.findByCompany_IdOrderByCreatedAtDesc(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public PublicOrderRequest getForCurrentTenant(Long id) {
        return requestRepository.findDetailedByIdAndCompanyId(id, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new PublicOrderRequestException("public.order.error.request_not_found"));
    }

    public PublicOrderRequest updateStatus(Long id, PublicOrderRequestStatus status) {
        PublicOrderRequest request = getForCurrentTenant(id);
        request.setStatus(status);
        return requestRepository.save(request);
    }

    public void deleteForCurrentTenant(Long id) {
        PublicOrderRequest request = getForCurrentTenant(id);
        List<PublicOrderRequestAttachment> attachments = attachmentRepository.findByRequest_IdOrderByCreatedAtAsc(request.getId());
        for (PublicOrderRequestAttachment attachment : attachments) {
            if (attachment.getFilePath() != null && !attachment.getFilePath().isBlank()) {
                fileStorage.deleteIfExists(attachment.getFilePath());
            }
        }
        attachmentRepository.deleteAll(attachments);
        requestRepository.delete(request);
    }

    @Transactional(readOnly = true)
    public PublicOrderRequestAttachment getAttachmentForCurrentTenant(Long attachmentId) {
        return attachmentRepository.findByIdAndCompany_Id(attachmentId, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new PublicOrderRequestException("public.order.error.file_not_found"));
    }

    @Transactional(readOnly = true)
    public List<PublicOrderRequestAttachment> getAttachmentsForCurrentTenantRequest(Long requestId) {
        PublicOrderRequest request = getForCurrentTenant(requestId);
        return attachmentRepository.findByRequest_IdOrderByCreatedAtAsc(request.getId());
    }

    public byte[] loadAttachmentContent(PublicOrderRequestAttachment attachment) throws IOException {
        return fileStorage.load(attachment.getFilePath());
    }

    public String resolveAttachmentMimeType(PublicOrderRequestAttachment attachment) {
        String fromDb = attachment.getMimeType();
        if (fromDb != null && !fromDb.isBlank()) {
            return fromDb;
        }
        return MediaTypeFactory.getMediaType(attachment.getFileName()).map(Object::toString).orElse("application/octet-stream");
    }

    private void enforceRateLimit(String companySlug, String remoteIp) {
        if (!rateLimitEnabled) {
            return;
        }
        String key = "public-request:" + companySlug + ":" + (remoteIp != null ? remoteIp : "unknown");
        if (!rateLimitService.allow(key, rateLimitMax, rateLimitWindowSeconds * 1000L)) {
            log.warn("public_request_rate_limited companySlug={} ip={} windowSec={} max={}",
                companySlug, remoteIp, rateLimitWindowSeconds, rateLimitMax);
            throw new PublicOrderRequestException("public.order.error.too_many_requests");
        }
    }

    private void storeAttachments(PublicOrderRequest request, Company company, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<MultipartFile> nonEmpty = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (nonEmpty.isEmpty()) {
            return;
        }
        if (nonEmpty.size() > publicMaxFiles) {
            throw new PublicOrderRequestException("public.order.error.max_files", publicMaxFiles);
        }
        List<PublicOrderRequestAttachment> toSave = new ArrayList<>();
        for (MultipartFile file : nonEmpty) {
            if (file.getSize() > publicMaxFileBytes) {
                throw new PublicOrderRequestException("public.order.error.file_too_large", file.getOriginalFilename());
            }
            String ext = extension(file.getOriginalFilename()).toLowerCase();
            if (!List.of(".pdf", ".jpg", ".jpeg", ".png", ".svg", ".ai", ".psd").contains(ext)) {
                throw new PublicOrderRequestException("public.order.error.file_type_not_allowed", file.getOriginalFilename());
            }
            try {
                StoredFile stored = fileStorage.store(file, "/company_" + company.getId() + "/public_requests/" + request.getId(), false);
                PublicOrderRequestAttachment att = new PublicOrderRequestAttachment();
                att.setRequest(request);
                att.setCompany(company);
                att.setFileName(stored.getFileName());
                att.setFilePath(stored.getFilePath());
                att.setOriginalFileName(file.getOriginalFilename());
                att.setMimeType(file.getContentType());
                att.setFileSize(file.getSize());
                toSave.add(att);
            } catch (IOException ex) {
                throw new PublicOrderRequestException("public.order.error.upload_failed", file.getOriginalFilename());
            }
        }
        attachmentRepository.saveAll(toSave);
        log.info("public_request_attachments_saved requestId={} companyId={} count={}",
            request.getId(), company.getId(), toSave.size());
    }

    private void sendSubmitEmails(PublicOrderRequest request) {
        Company company = request.getCompany();
        boolean serbian = isSerbianCompany(company);

        if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
            EmailMessage customer = new EmailMessage();
            customer.setTo(request.getCustomerEmail());
            customer.setSubject(serbian ? "Zahtev je primljen" : "Request received");
            String html = serbian
                ? "<p>Vaš zahtev za narudžbinu je uspešno primljen.</p><p>Naš tim će ga uskoro pregledati i kontaktirati vas.</p>"
                : "<p>Your order request has been received.</p><p>Our team will review it and contact you shortly.</p>";
            customer.setHtmlBody(html);
            customer.setTextBody(serbian ? "Vaš zahtev je primljen." : "Your request has been received.");
            try {
                emailService.send(customer, company, "public-order-request-received");
            } catch (Exception ignored) {
                log.warn("public_request_customer_email_failed requestId={} companyId={} to={}",
                    request.getId(), company.getId(), request.getCustomerEmail());
                // submission flow must not fail if email fails
            }
        }

        List<User> admins = userRepository.findByCompany_IdAndRoleInAndActiveTrue(
            company.getId(),
            List.of(User.Role.ADMIN, User.Role.MANAGER));
        for (User admin : admins) {
            if (admin.getEmail() == null || admin.getEmail().isBlank()) {
                continue;
            }
            EmailMessage internal = new EmailMessage();
            internal.setTo(admin.getEmail());
            internal.setSubject(serbian ? "Novi javni zahtev za narudžbinu" : "New public order request");
            String detailsUrl = baseUrl + "/admin/public-requests/" + request.getId();
            String html = (serbian
                ? "<p>Stigao je novi javni zahtev od klijenta <strong>%s</strong>.</p><p><a href=\"%s\">Otvori zahtev</a></p>"
                : "<p>A new public request was submitted by <strong>%s</strong>.</p><p><a href=\"%s\">Open request</a></p>")
                .formatted(request.getCustomerName(), detailsUrl);
            internal.setHtmlBody(html);
            internal.setTextBody((serbian ? "Novi javni zahtev: " : "New public request: ") + detailsUrl);
            try {
                emailService.send(internal, company, "public-order-request-admin");
            } catch (Exception ignored) {
                log.warn("public_request_admin_email_failed requestId={} companyId={} to={}",
                    request.getId(), company.getId(), admin.getEmail());
                // submission flow must not fail if email fails
            }
        }
    }

    private boolean isSerbianCompany(Company company) {
        if (company == null) {
            return false;
        }
        if ("RSD".equalsIgnoreCase(company.getCurrency())) {
            return true;
        }
        String address = company.getAddress();
        if (address == null) {
            return false;
        }
        String lower = address.toLowerCase();
        return lower.contains("srbija") || lower.contains("serbia");
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "" : fileName.substring(dot);
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
