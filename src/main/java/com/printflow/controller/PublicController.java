package com.printflow.controller;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.service.WorkOrderService;
import com.printflow.service.FileStorageService;
import com.printflow.service.RateLimitService;
import com.printflow.service.ActivityLogService;
import com.printflow.service.AuditLogService;
import com.printflow.entity.enums.AuditAction;
import com.printflow.service.BillingRequiredException;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.ClientPortalAccessRepository;
import com.printflow.repository.PasswordResetTokenRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Controller  // DODAJ OVO!
@RequestMapping("/public")
public class PublicController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);
    private static final int MAX_PUBLIC_TOKEN_LENGTH = 128;
    
    private final WorkOrderService workOrderService;
    private final FileStorageService fileStorageService;
    private final RateLimitService rateLimitService;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final WorkOrderRepository workOrderRepository;
    private final CompanyRepository companyRepository;
    private final ClientPortalAccessRepository clientPortalAccessRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ActivityLogService activityLogService;
    private final com.printflow.service.CompanyBrandingService companyBrandingService;
    private final AuditLogService auditLogService;
    private final boolean publicUploadRateLimitEnabled;
    private final int publicUploadRateLimitMax;
    private final int publicUploadRateLimitWindowSeconds;
    private final boolean publicApproveRateLimitEnabled;
    private final int publicApproveRateLimitMax;
    private final int publicApproveRateLimitWindowSeconds;
    private final boolean publicTrackRateLimitEnabled;
    private final int publicTrackRateLimitMax;
    private final int publicTrackRateLimitWindowSeconds;
    private final boolean publicGlobalRateLimitEnabled;
    private final int publicGlobalRateLimitMax;
    private final int publicGlobalRateLimitWindowSeconds;
    private final boolean publicTokenRateLimitEnabled;
    private final int publicTokenRateLimitMax;
    private final int publicTokenRateLimitWindowSeconds;
    private final String publicAllowedFileTypes;
    private final long publicMaxFileSize;
    private final int publicMaxFilesPerOrder;
    private final long publicMaxTotalSize;
    private final int publicFileMetaMaxLength;
    
    
    
    public PublicController(WorkOrderService workOrderService,
                            FileStorageService fileStorageService,
                            RateLimitService rateLimitService,
                            WorkOrderItemRepository workOrderItemRepository,
                            WorkOrderRepository workOrderRepository,
                            CompanyRepository companyRepository,
                            ClientPortalAccessRepository clientPortalAccessRepository,
                            PasswordResetTokenRepository passwordResetTokenRepository,
                            ActivityLogService activityLogService,
                            com.printflow.service.CompanyBrandingService companyBrandingService,
                            AuditLogService auditLogService,
                            @Value("${app.rate-limit.public-upload.enabled:true}") boolean publicUploadRateLimitEnabled,
                            @Value("${app.rate-limit.public-upload.max-requests:5}") int publicUploadRateLimitMax,
                            @Value("${app.rate-limit.public-upload.window-seconds:60}") int publicUploadRateLimitWindowSeconds,
                            @Value("${app.rate-limit.public-approve.enabled:true}") boolean publicApproveRateLimitEnabled,
                            @Value("${app.rate-limit.public-approve.max-requests:10}") int publicApproveRateLimitMax,
                            @Value("${app.rate-limit.public-approve.window-seconds:60}") int publicApproveRateLimitWindowSeconds,
                            @Value("${app.rate-limit.public-track.enabled:true}") boolean publicTrackRateLimitEnabled,
                            @Value("${app.rate-limit.public-track.max-requests:30}") int publicTrackRateLimitMax,
                            @Value("${app.rate-limit.public-track.window-seconds:60}") int publicTrackRateLimitWindowSeconds,
                            @Value("${app.rate-limit.public-global.enabled:true}") boolean publicGlobalRateLimitEnabled,
                            @Value("${app.rate-limit.public-global.max-requests:60}") int publicGlobalRateLimitMax,
                            @Value("${app.rate-limit.public-global.window-seconds:60}") int publicGlobalRateLimitWindowSeconds,
                            @Value("${app.rate-limit.public-token.enabled:true}") boolean publicTokenRateLimitEnabled,
                            @Value("${app.rate-limit.public-token.max-requests:20}") int publicTokenRateLimitMax,
                            @Value("${app.rate-limit.public-token.window-seconds:60}") int publicTokenRateLimitWindowSeconds,
                            @Value("${app.upload.public-allowed-file-types:.pdf,.jpg,.jpeg,.png,.svg,.ai,.psd}") String publicAllowedFileTypes,
                            @Value("${app.upload.public-max-file-size:10485760}") long publicMaxFileSize,
                            @Value("${app.upload.public-max-files-per-order:10}") int publicMaxFilesPerOrder,
                            @Value("${app.upload.public-max-total-size:52428800}") long publicMaxTotalSize,
                            @Value("${app.upload.public-file-meta-max-length:20000}") int publicFileMetaMaxLength) {
		super();
        this.workOrderService = workOrderService;
        this.fileStorageService = fileStorageService;
        this.rateLimitService = rateLimitService;
        this.workOrderItemRepository = workOrderItemRepository;
        this.workOrderRepository = workOrderRepository;
        this.companyRepository = companyRepository;
        this.clientPortalAccessRepository = clientPortalAccessRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.activityLogService = activityLogService;
        this.companyBrandingService = companyBrandingService;
        this.auditLogService = auditLogService;
        this.publicUploadRateLimitEnabled = publicUploadRateLimitEnabled;
        this.publicUploadRateLimitMax = publicUploadRateLimitMax;
        this.publicUploadRateLimitWindowSeconds = publicUploadRateLimitWindowSeconds;
        this.publicApproveRateLimitEnabled = publicApproveRateLimitEnabled;
        this.publicApproveRateLimitMax = publicApproveRateLimitMax;
        this.publicApproveRateLimitWindowSeconds = publicApproveRateLimitWindowSeconds;
        this.publicTrackRateLimitEnabled = publicTrackRateLimitEnabled;
        this.publicTrackRateLimitMax = publicTrackRateLimitMax;
        this.publicTrackRateLimitWindowSeconds = publicTrackRateLimitWindowSeconds;
        this.publicGlobalRateLimitEnabled = publicGlobalRateLimitEnabled;
        this.publicGlobalRateLimitMax = publicGlobalRateLimitMax;
        this.publicGlobalRateLimitWindowSeconds = publicGlobalRateLimitWindowSeconds;
        this.publicTokenRateLimitEnabled = publicTokenRateLimitEnabled;
        this.publicTokenRateLimitMax = publicTokenRateLimitMax;
        this.publicTokenRateLimitWindowSeconds = publicTokenRateLimitWindowSeconds;
        this.publicAllowedFileTypes = publicAllowedFileTypes;
        this.publicMaxFileSize = publicMaxFileSize;
        this.publicMaxFilesPerOrder = publicMaxFilesPerOrder;
        this.publicMaxTotalSize = publicMaxTotalSize;
        this.publicFileMetaMaxLength = publicFileMetaMaxLength;
	}

	// Glavna stranica za praćenje naloga
    @GetMapping("/order/{token}")
    public String trackOrder(@PathVariable String token,
                             @RequestParam(required = false) String uploadError,
                             @RequestParam(required = false) String uploadErrorKey,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             Model model) {
        try {
            String normalizedToken = normalizePublicToken(token);
            if (normalizedToken == null) {
                return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
            }
            if (!normalizedToken.equals(token)) {
                return "redirect:/public/order/" + normalizedToken;
            }
            if (normalizedToken != null && !normalizedToken.isBlank()) {
                try {
                    String resolvedToken = workOrderService.resolvePublicTokenFromOrderNumber(normalizedToken);
                    if (!resolvedToken.equals(normalizedToken)) {
                        return "redirect:/public/order/" + resolvedToken;
                    }
                } catch (Exception ignored) {
                    // Not an order number or not resolvable, continue with token lookup
                }
            }
            String ip = getClientIp(request);
            if (rateLimitService.isWhitelisted(ip)) {
                // Skip rate limiting for whitelisted IPs
            } else {
            if (isBanned(ip)) {
                return renderOrderNotFound(model, response, "public.error.access_denied", HttpServletResponse.SC_FORBIDDEN);
            }
            if (!checkGlobalRateLimit(ip)) {
                return renderOrderNotFound(model, response, "public.error.too_many_requests", 429);
            }
            if (!checkTokenRateLimit(normalizedToken, ip)) {
                return renderOrderNotFound(model, response, "public.error.too_many_requests_for_order", 429);
            }
            if (publicTrackRateLimitEnabled) {
                boolean allowed = rateLimitService.allow(
                    "public-track:" + ip,
                    publicTrackRateLimitMax,
                    publicTrackRateLimitWindowSeconds * 1000L
                );
                if (!allowed) {
                    return renderOrderNotFound(model, response, "public.error.too_many_requests", 429);
                }
            }
            }
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(normalizedToken);
            com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityByPublicToken(normalizedToken);
            List<com.printflow.dto.AttachmentDTO> attachments = fileStorageService.getAttachmentsByWorkOrder(order.getId());
            
            // Filtriraj samo preview fajlove za klijenta
            List<com.printflow.dto.AttachmentDTO> previewAttachments = attachments.stream()
                .filter(a -> a.getAttachmentType() == AttachmentType.DESIGN_PREVIEW)
                .collect(Collectors.toList());

            List<com.printflow.dto.AttachmentDTO> clientAttachments = attachments.stream()
                .filter(a -> a.getAttachmentType() == AttachmentType.CLIENT_FILE)
                .collect(Collectors.toList());
            
            model.addAttribute("order", order);
            model.addAttribute("previewAttachments", previewAttachments);
            model.addAttribute("clientAttachments", clientAttachments);
            model.addAttribute("token", normalizedToken);
            model.addAttribute("orderItems", workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
                order.getId(), orderEntity.getCompany().getId()));
            model.addAttribute("activityFeed", activityLogService.getForWorkOrder(order.getId(), orderEntity.getCompany()));
            model.addAttribute("companyBrand", companyBrandingService.toBranding(orderEntity.getCompany(), normalizedToken, "order"));
            model.addAttribute("companyCurrency", orderEntity.getCompany() != null && orderEntity.getCompany().getCurrency() != null
                ? orderEntity.getCompany().getCurrency() : "RSD");
            model.addAttribute("publicAllowedFileTypes", publicAllowedFileTypes);
            model.addAttribute("publicMaxFileSizeLabel", formatBytes(publicMaxFileSize));
            model.addAttribute("publicMaxFilesPerOrder", publicMaxFilesPerOrder);
            model.addAttribute("publicMaxTotalSizeLabel", formatBytes(publicMaxTotalSize));
            model.addAttribute("publicMaxTotalSizeRaw", publicMaxTotalSize);
            if (uploadErrorKey != null && !uploadErrorKey.isBlank()) {
                model.addAttribute("uploadErrorKey", uploadErrorKey);
            } else if (uploadError != null && !uploadError.isBlank()) {
                model.addAttribute("uploadError", uploadError);
            }
            
            return "public/order-tracking";
        } catch (Exception e) {
            return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
        }
    }
    
    // Odobrenje dizajna od strane klijenta
    @PostMapping("/order/{token}/approve-design")
    public String approveDesign(@PathVariable String token,
                               @RequestParam boolean approved,
                               @RequestParam(required = false) String comment,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               Model model) {
        try {
            String normalizedToken = normalizePublicToken(token);
            if (normalizedToken == null) {
                return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
            }
            String ip = getClientIp(request);
            if (rateLimitService.isWhitelisted(ip)) {
                // Skip rate limiting for whitelisted IPs
            } else {
            if (isBanned(ip)) {
                return renderOrderNotFound(model, response, "public.error.access_denied", HttpServletResponse.SC_FORBIDDEN);
            }
            if (!checkGlobalRateLimit(ip)) {
                return renderOrderNotFound(model, response, "public.error.too_many_requests", 429);
            }
            if (!checkTokenRateLimit(normalizedToken, ip)) {
                return renderOrderNotFound(model, response, "public.error.too_many_requests_for_order", 429);
            }
            if (publicApproveRateLimitEnabled) {
                boolean allowed = rateLimitService.allow(
                    "public-approve:" + ip,
                    publicApproveRateLimitMax,
                    publicApproveRateLimitWindowSeconds * 1000L
                );
                if (!allowed) {
                    return renderOrderNotFound(model, response, "public.error.too_many_requests", 429);
                }
            }
            }
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(normalizedToken);
            
            // Procesuiraj odobrenje
            workOrderService.approveDesign(order.getId(), normalizedToken, approved, comment);
            
            if (approved) {
                model.addAttribute("messageKey", "public.design_feedback.approved");
            } else {
                model.addAttribute("messageKey", "public.design_feedback.revision_requested");
            }
            
            return "public/design-feedback";
        } catch (BillingRequiredException e) {
            logBillingBlockedPublic("approve-design", token);
            return renderOrderNotFound(model, response, "public.error.request_unavailable", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.warn("Public approve-design failed for token={}: {}", token, e.toString());
            return renderOrderNotFound(model, response, "public.error.unable_to_process", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @PostMapping("/order/{token}/upload-reference")
    public String uploadReference(@PathVariable String token,
                                  @RequestParam("file") MultipartFile[] files,
                                  @RequestParam(name = "description", required = false) String[] descriptions,
                                  @RequestParam(name = "fileMetaJson", required = false) String fileMetaJson,
                                  @RequestParam(name = "lang", required = false) String lang,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Model model) {
        try {
            String normalizedToken = normalizePublicToken(token);
            if (normalizedToken == null) {
                return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
            }
            if (files == null || files.length == 0) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.select_file", lang);
            }
            String ip = getClientIp(request);
            if (rateLimitService.isWhitelisted(ip)) {
                // Skip rate limiting for whitelisted IPs
            } else {
            if (isBanned(ip)) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.access_denied", lang);
            }
            if (!checkGlobalRateLimit(ip)) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.too_many_requests", lang);
            }
            if (!checkTokenRateLimit(normalizedToken, ip)) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.too_many_requests", lang);
            }
            if (publicUploadRateLimitEnabled) {
                boolean allowed = rateLimitService.allow(
                    "public-upload:" + ip,
                    publicUploadRateLimitMax,
                    publicUploadRateLimitWindowSeconds * 1000L
                );
                if (!allowed) {
                    return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.too_many_uploads", lang);
                }
            }
            }
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(normalizedToken);
            long existingCount = fileStorageService.countClientFiles(order.getId());
            if (existingCount + files.length > publicMaxFilesPerOrder) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.limit_reached", lang);
            }
            java.util.List<FileMeta> meta = parseFileMeta(fileMetaJson);
            if (meta.isEmpty() || meta.size() != files.length) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.metadata_mismatch", lang);
            }
            boolean[] used = new boolean[files.length];
            for (int m = 0; m < meta.size(); m++) {
                FileMeta item = meta.get(m);
                String name = item != null ? item.name : null;
                long size = item != null && item.size != null ? item.size : -1L;
                long lastModified = item != null && item.lastModified != null ? item.lastModified : -1L;
                String type = item != null ? item.type : null;
                String desc = item != null ? item.description : null;
                int index = item != null && item.index != null ? item.index : -1;
                int match = -1;
                if (index >= 0 && index < files.length && !used[index]) {
                    MultipartFile candidate = files[index];
                    if (candidate != null && !candidate.isEmpty()) {
                        boolean nameOk = name == null || name.equals(candidate.getOriginalFilename());
                        boolean sizeOk = size <= 0 || size == candidate.getSize();
                        boolean typeOk = type == null || type.equals(candidate.getContentType());
                        if (nameOk && sizeOk && typeOk) {
                            match = index;
                        }
                    }
                }
                for (int i = 0; i < files.length; i++) {
                    if (used[i]) continue;
                    MultipartFile file = files[i];
                    if (file == null || file.isEmpty()) continue;
                    if ((name == null || name.equals(file.getOriginalFilename())) &&
                        (size <= 0 || size == file.getSize()) &&
                        (type == null || type.equals(file.getContentType()))) {
                        match = i;
                        break;
                    }
                }
                if (match < 0 && lastModified > 0) {
                    for (int i = 0; i < files.length; i++) {
                        if (used[i]) continue;
                        MultipartFile file = files[i];
                        if (file == null || file.isEmpty()) continue;
                        if ((name == null || name.equals(file.getOriginalFilename())) &&
                            (size <= 0 || size == file.getSize()) &&
                            (type == null || type.equals(file.getContentType()))) {
                            match = i;
                            break;
                        }
                    }
                }
                if (match < 0) {
                    return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.metadata_mismatch", lang);
                }
                used[match] = true;
                MultipartFile file = files[match];
                fileStorageService.uploadPublicFile(file, order.getId(), normalizedToken, AttachmentType.CLIENT_FILE, desc);
            }
            return "redirect:/public/order/" + normalizedToken;
        } catch (BillingRequiredException e) {
            logBillingBlockedPublic("upload-reference", token);
            return redirectWithUploadErrorKey(normalizePublicTokenOrFallback(token), "public.upload.error.unavailable", lang);
        } catch (Exception e) {
            log.warn("Public upload-reference failed for token={}: {}", token, e.toString());
            return redirectWithUploadErrorKey(normalizePublicTokenOrFallback(token), "public.upload.error.generic", lang);
        }
    }

    private String redirectWithUploadErrorKey(String token, String errorKey, String lang) {
        String safeToken = normalizePublicTokenOrFallback(token);
        if (safeToken == null || safeToken.isBlank()) {
            return "public/order-not-found";
        }
        String normalizedLang = normalizePublicLang(lang);
        if (normalizedLang == null) {
            return "redirect:/public/order/" + safeToken + "?uploadErrorKey=" + errorKey;
        }
        return "redirect:/public/order/" + safeToken + "?uploadErrorKey=" + errorKey + "&lang=" + normalizedLang;
    }

    private String normalizePublicLang(String lang) {
        if (lang == null) {
            return null;
        }
        String value = lang.trim().toLowerCase(java.util.Locale.ROOT);
        if ("sr".equals(value) || "en".equals(value)) {
            return value;
        }
        return null;
    }

    private String renderOrderNotFound(Model model, HttpServletResponse response, String errorKey, int statusCode) {
        model.addAttribute("errorKey", errorKey);
        model.addAttribute("errorHeadingKey", resolveOrderErrorHeadingKey(errorKey));
        if (response != null) {
            response.setStatus(statusCode);
        }
        return "public/order-not-found";
    }

    private String resolveOrderErrorHeadingKey(String errorKey) {
        if (errorKey != null && errorKey.startsWith("public.error.")) {
            return "public.error.heading";
        }
        return "order_not_found.heading";
    }

    private void logBillingBlockedPublic(String action, String token) {
        try {
            var order = workOrderRepository.findWithClientAndCompanyByPublicToken(token).orElse(null);
            var company = order != null ? order.getCompany() : null;
            auditLogService.log(AuditAction.UPDATE, "BillingAccess", null, null, null,
                "Blocked public action: " + action, company);
        } catch (Exception ignored) {
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isBanned(String ip) {
        return rateLimitService.isBanned(ip);
    }

    private boolean checkGlobalRateLimit(String ip) {
        if (!publicGlobalRateLimitEnabled) {
            return true;
        }
        return rateLimitService.allow(
            "public-global:" + ip,
            publicGlobalRateLimitMax,
            publicGlobalRateLimitWindowSeconds * 1000L
        );
    }

    private boolean checkTokenRateLimit(String token, String ip) {
        if (!publicTokenRateLimitEnabled || token == null || token.isBlank()) {
            return true;
        }
        boolean tokenAllowed = rateLimitService.allow(
            "public-token:" + token,
            publicTokenRateLimitMax,
            publicTokenRateLimitWindowSeconds * 1000L
        );
        if (!tokenAllowed) {
            return false;
        }
        if (ip == null || ip.isBlank()) {
            return true;
        }
        return rateLimitService.allow(
            "public-token-ip:" + token + ":" + ip,
            publicTokenRateLimitMax,
            publicTokenRateLimitWindowSeconds * 1000L
        );
    }

    private String normalizePublicToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_PUBLIC_TOKEN_LENGTH ? null : trimmed;
    }

    private String normalizePublicTokenOrFallback(String token) {
        String normalized = normalizePublicToken(token);
        return normalized != null ? normalized : token;
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        long kb = 1024L;
        long mb = kb * 1024L;
        if (bytes >= mb) {
            return String.format("%.1f MB", bytes / (double) mb);
        }
        if (bytes >= kb) {
            return String.format("%.1f KB", bytes / (double) kb);
        }
        return bytes + " B";
    }

    private java.util.List<FileMeta> parseFileMeta(String fileMetaJson) {
        if (fileMetaJson == null || fileMetaJson.isBlank()) {
            return java.util.Collections.emptyList();
        }
        if (fileMetaJson.length() > publicFileMetaMaxLength) {
            return java.util.Collections.emptyList();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(fileMetaJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<FileMeta>>() {});
        } catch (Exception ex) {
            return java.util.Collections.emptyList();
        }
    }

    private static class FileMeta {
        public Integer index;
        public String name;
        public Long size;
        public Long lastModified;
        public String type;
        public String description;
    }
    
    // Stranica za unos tracking koda
    @GetMapping("/track")
    public String trackOrderForm(@RequestParam(required = false) String token,
                                 @RequestParam(required = false) Long company,
                                 Model model) {
        addTrackCompanies(model);
        String normalizedToken = normalizePublicToken(token);
        model.addAttribute("companyParam", company);
        model.addAttribute("tokenParam", normalizedToken);
        applyTrackBranding(model, company, normalizedToken);
        return "public/track-order";
    }
    
    @PostMapping("/track")
    public String trackOrderSubmit(@RequestParam String trackingCode,
                                   @RequestParam(required = false) Long company,
                                   @RequestParam(required = false) String token,
                                   @RequestParam(required = false) String lang,
                                   HttpServletRequest request,
                                   Model model) {
        model.addAttribute("submittedTrackingCode", trackingCode);
        String ip = getClientIp(request);
        if (!rateLimitService.isWhitelisted(ip)) {
            if (isBanned(ip)) {
                return renderTrackFormError(model, "track.error.too_many_requests", company, token, trackingCode, false);
            }
            if (!checkGlobalRateLimit(ip)) {
                return renderTrackFormError(model, "track.error.too_many_requests", company, token, trackingCode, false);
            }
            if (publicTrackRateLimitEnabled) {
                boolean allowed = rateLimitService.allow(
                    "public-track-form:" + ip,
                    publicTrackRateLimitMax,
                    publicTrackRateLimitWindowSeconds * 1000L
                );
                if (!allowed) {
                    return renderTrackFormError(model, "track.error.too_many_requests", company, token, trackingCode, false);
                }
            }
        }
        if (trackingCode == null || trackingCode.trim().isEmpty()) {
            return renderTrackFormError(model, "track.error.required", company, token, trackingCode, true);
        }

        String trimmedCode = trackingCode.trim();
        if (trimmedCode.length() > MAX_PUBLIC_TOKEN_LENGTH) {
            return renderTrackFormError(model, "track.error.invalid_code", company, token, trimmedCode, true);
        }
        String resolvedToken = trimmedCode;
        Long tokenCompanyId = workOrderRepository
            .findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(trimmedCode, LocalDateTime.now())
            .orElse(null);
        if (tokenCompanyId == null) {
            try {
                resolvedToken = workOrderService.resolvePublicTokenFromOrderNumber(trimmedCode);
                tokenCompanyId = workOrderRepository
                    .findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(resolvedToken, LocalDateTime.now())
                    .orElse(null);
            } catch (Exception ignored) {
                // Not a resolvable order number; keep original input for the next page.
            }
        }

        if (company != null && tokenCompanyId != null && !company.equals(tokenCompanyId)) {
            return renderTrackFormError(model, "track.error.company_mismatch", company, token, trimmedCode, true);
        }

        String normalizedLang = normalizePublicLang(lang);
        if (normalizedLang == null) {
            return "redirect:/public/order/" + resolvedToken;
        }
        return "redirect:/public/order/" + resolvedToken + "?lang=" + normalizedLang;
    }
    
    // Landing page za PrintFlow
    @GetMapping("/")
    public String landingPage(Model model) {
        model.addAttribute("pageTitle", "PrintFlow - Order Tracking System");
        model.addAttribute("companyBrand", defaultBranding());
        return "public/landing";
    }

    @GetMapping("/companies")
    public String companiesDirectory(Model model) {
        List<com.printflow.entity.Company> companies = companyRepository.findByActiveTrue()
            .stream()
            .sorted(java.util.Comparator.comparing(
                com.printflow.entity.Company::getName,
                java.text.Collator.getInstance(new java.util.Locale("sr", "RS"))
            ))
            .toList();
        model.addAttribute("companies", companies);
        model.addAttribute("companyBrand", defaultBranding());
        return "public/companies";
    }
    
    // Contact page
    @GetMapping("/contact")
    public String contactPage(Model model) {
        model.addAttribute("companyBrand", defaultBranding());
        return "public/contact";
    }

    @GetMapping("/company-logo/{companyId}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> companyLogo(@PathVariable Long companyId,
                                                                       @RequestParam(required = false) String token,
                                                                       @RequestParam(required = false) String type) {
        try {
            if (!isLogoTokenValid(companyId, token, type)) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            byte[] data = companyBrandingService.loadLogo(companyId);
            String logoPath = companyRepository.findById(companyId).map(com.printflow.entity.Company::getLogoPath).orElse(null);
            org.springframework.http.MediaType mediaType = org.springframework.http.MediaTypeFactory
                .getMediaType(logoPath != null ? logoPath : "")
                .orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
            return org.springframework.http.ResponseEntity.ok()
                .contentType(mediaType)
                .body(data);
        } catch (Exception ex) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }

    private boolean isLogoTokenValid(Long companyId, String token, String type) {
        if (companyId == null) {
            return false;
        }
        if (token == null || token.isBlank() || type == null || type.isBlank()) {
            // Company logo is considered public branding, safe to serve without token.
            return true;
        }
        if ("order".equalsIgnoreCase(type)) {
            return workOrderRepository
                .findByPublicTokenAndCompany_IdAndPublicTokenExpiresAtAfter(token, companyId, LocalDateTime.now())
                .isPresent();
        }
        if ("portal".equalsIgnoreCase(type)) {
            return clientPortalAccessRepository.findByAccessToken(token)
                .filter(access -> access.getCompany() != null
                    && companyId.equals(access.getCompany().getId())
                    && access.getExpiresAt() != null
                    && access.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
        }
        if ("reset".equalsIgnoreCase(type)) {
            return passwordResetTokenRepository.findByToken(token)
                .filter(reset -> reset.getCompany() != null
                    && companyId.equals(reset.getCompany().getId())
                    && reset.getUsedAt() == null
                    && reset.getExpiresAt() != null
                    && reset.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
        }
        return false;
    }

    private com.printflow.dto.CompanyBrandingDTO defaultBranding() {
        return new com.printflow.dto.CompanyBrandingDTO(
            null,
            "PrintFlow",
            null,
            null,
            null,
            null,
            null,
            "#2563eb"
        );
    }

    private void addTrackCompanies(Model model) {
        List<com.printflow.entity.Company> companies = companyRepository.findByActiveTrue()
            .stream()
            .sorted(java.util.Comparator.comparing(
                com.printflow.entity.Company::getName,
                java.text.Collator.getInstance(new java.util.Locale("sr", "RS"))
            ))
            .toList();
        model.addAttribute("trackCompanies", companies);
    }

    private String renderTrackFormError(Model model,
                                        String errorKey,
                                        Long company,
                                        String token,
                                        String submittedTrackingCode,
                                        boolean contextualBranding) {
        addTrackCompanies(model);
        model.addAttribute("errorKey", errorKey);
        model.addAttribute("companyParam", company);
        String normalizedToken = normalizePublicToken(token);
        model.addAttribute("tokenParam", normalizedToken);
        if (submittedTrackingCode != null) {
            model.addAttribute("submittedTrackingCode", submittedTrackingCode);
        }
        if (contextualBranding) {
            applyTrackBranding(model, company, normalizedToken);
        } else {
            model.addAttribute("companyBrand", defaultBranding());
        }
        return "public/track-order";
    }

    private void applyTrackBranding(Model model, Long company, String normalizedToken) {
        if (normalizedToken != null) {
            workOrderRepository.findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(normalizedToken, LocalDateTime.now())
                .flatMap(companyId -> companyBrandingService.getBrandingByCompanyId(companyId, normalizedToken, "order"))
                .ifPresentOrElse(
                    brand -> model.addAttribute("companyBrand", brand),
                    () -> model.addAttribute("companyBrand", defaultBranding())
                );
            return;
        }
        if (company != null) {
            companyBrandingService.getBrandingByCompanyId(company)
                .ifPresentOrElse(
                    brand -> model.addAttribute("companyBrand", brand),
                    () -> model.addAttribute("companyBrand", defaultBranding())
                );
            return;
        }
        model.addAttribute("companyBrand", defaultBranding());
    }
}
