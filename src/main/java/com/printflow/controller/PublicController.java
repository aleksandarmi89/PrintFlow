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
import com.printflow.service.OrderPdfService;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/public")
public class PublicController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);
    private static final int MAX_PUBLIC_TOKEN_LENGTH = 128;
    private static final int MAX_PUBLIC_ERROR_MESSAGE_LENGTH = 300;
    private static final int MAX_PUBLIC_COMMENT_LENGTH = 500;
    private static final int MAX_PUBLIC_MESSAGE_KEY_LENGTH = 120;
    // Supports order-number style codes and url-safe public tokens.
    private static final java.util.regex.Pattern TRACKING_CODE_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final java.util.regex.Pattern MESSAGE_KEY_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9._-]+$");
    
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
    private final OrderPdfService orderPdfService;
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
                            OrderPdfService orderPdfService,
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
        this.orderPdfService = orderPdfService;
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

    @GetMapping("/order/{token}/pdf/quote")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.ByteArrayResource> downloadPublicQuotePdf(
        @PathVariable String token,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestParam(required = false) String lng,
        @RequestParam(required = false) String language,
        java.util.Locale requestLocale,
        HttpServletRequest request,
        HttpServletResponse response) {
        String normalizedToken = normalizePublicToken(token);
        if (normalizedToken == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        if (!normalizedToken.equals(token)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        try {
            String ip = getClientIp(request);
            if (!rateLimitService.isWhitelisted(ip)) {
                if (isBanned(ip)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return org.springframework.http.ResponseEntity.status(HttpServletResponse.SC_FORBIDDEN).build();
                }
                if (!checkGlobalRateLimit(ip) || !checkTokenRateLimit(normalizedToken, ip)) {
                    response.setStatus(429);
                    return org.springframework.http.ResponseEntity.status(429).build();
                }
            }
            if (normalizedToken != null && !normalizedToken.isBlank()) {
                try {
                    String resolvedToken = workOrderService.resolvePublicTokenFromOrderNumber(normalizedToken);
                    if (resolvedToken != null && !resolvedToken.equals(normalizedToken)) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        return org.springframework.http.ResponseEntity.notFound().build();
                    }
                } catch (Exception ignored) {
                    // Continue with token as-is.
                }
            }
            com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityByPublicToken(normalizedToken);
            workOrderService.ensureTotalsSyncedForEntity(orderEntity);
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(normalizedToken);
            if (!isQuotePdfPubliclyAvailable(order)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            Long companyId = orderEntity.getCompany() != null ? orderEntity.getCompany().getId() : null;
            if (companyId == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            List<com.printflow.entity.WorkOrderItem> items = workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
                order.getId(), companyId);
            byte[] pdf = orderPdfService.generateQuotePdf(
                order,
                orderEntity.getCompany(),
                items,
                resolveRequestedLocale(resolvePublicLang(lang, locale, lng, language), requestLocale)
            );
            auditLogService.log(AuditAction.DOWNLOAD, "WorkOrder", order.getId(),
                null, null, "Public quote PDF downloaded", orderEntity.getCompany());
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(pdf);
            String fileName = "quote-" + (order.getOrderNumber() != null ? order.getOrderNumber() : order.getId()) + ".pdf";
            return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(resource);
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("work order not found")) {
                log.info("Public quote PDF token not found/expired: {}", token);
            } else {
                log.warn("Public quote PDF download failed token={}: {}", token, ex.toString());
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }

    // Legacy compatibility for old quote download links.
    @GetMapping({"/order/{token}/quote", "/order/{token}/quote-pdf", "/order/{token}/quote.pdf", "/quote/{token}", "/quote.php/{token}"})
    public String legacyPublicQuotePdfRoute(@PathVariable String token,
                                            @RequestParam(required = false) String lang,
                                            @RequestParam(required = false) String locale,
                                            @RequestParam(required = false) String lng,
                                            @RequestParam(required = false) String language) {
        String normalizedToken = normalizePublicToken(token);
        if (normalizedToken == null) {
            return "redirect:/public/track";
        }
        StringBuilder redirect = new StringBuilder("redirect:/public/order/")
            .append(URLEncoder.encode(normalizedToken, StandardCharsets.UTF_8))
            .append("/pdf/quote");
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        if (normalizedLang != null) {
            redirect.append("?lang=").append(normalizedLang);
        }
        return redirect.toString();
    }

    // Legacy compatibility for query-based quote links.
    @GetMapping({"/quote", "/quote.php"})
    public String legacyPublicQuoteQueryRoute(@RequestParam(required = false) String token,
                                              @RequestParam(required = false, name = "t") String tokenShort,
                                              @RequestParam(required = false) String publicToken,
                                              @RequestParam(required = false, name = "public_token") String publicTokenSnake,
                                              @RequestParam(required = false) String trackingCode,
                                              @RequestParam(required = false, name = "tracking_code") String trackingCodeSnake,
                                              @RequestParam(required = false) String tracking,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) String orderNumber,
                                              @RequestParam(required = false, name = "order_number") String orderNumberSnake,
                                              @RequestParam(required = false) String order,
                                              @RequestParam(required = false) String lang,
                                              @RequestParam(required = false) String locale,
                                              @RequestParam(required = false) String lng,
                                              @RequestParam(required = false) String language,
                                              @RequestParam(required = false) Long company,
                                              @RequestParam(required = false) Long companyId,
                                              @RequestParam(required = false, name = "company_id") Long companyIdSnake) {
        String normalizedToken = resolveTrackingToken(
            token, tokenShort, publicToken, publicTokenSnake, trackingCode, trackingCodeSnake, tracking, code, orderNumber, orderNumberSnake, order);
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        if (normalizedToken == null) {
            StringBuilder redirect = new StringBuilder("redirect:/public/track");
            boolean hasQuery = false;
            if (selectedCompany != null) {
                redirect.append(hasQuery ? '&' : '?').append("company=").append(selectedCompany);
                hasQuery = true;
            }
            if (normalizedLang != null) {
                redirect.append(hasQuery ? '&' : '?').append("lang=").append(normalizedLang);
            }
            return redirect.toString();
        }
        StringBuilder redirect = new StringBuilder("redirect:/public/order/")
            .append(URLEncoder.encode(normalizedToken, StandardCharsets.UTF_8))
            .append("/pdf/quote");
        if (normalizedLang != null) {
            redirect.append("?lang=").append(normalizedLang);
        }
        return redirect.toString();
    }

    // Legacy compatibility for older status URLs.
    @GetMapping({"/order-status/{token}", "/status/{token}", "/order-status.php/{token}", "/status.php/{token}"})
    public String legacyPublicStatusRoute(@PathVariable String token,
                                          @RequestParam(required = false) String lang,
                                          @RequestParam(required = false) String locale,
                                          @RequestParam(required = false) String lng,
                                          @RequestParam(required = false) String language) {
        String normalizedToken = normalizePublicToken(token);
        if (normalizedToken == null) {
            return "redirect:/public/track";
        }
        return redirectToPublicOrder(normalizedToken, resolvePublicLang(lang, locale, lng, language));
    }

    // Legacy compatibility for query-param based status URLs.
    @GetMapping({"/order-status", "/status", "/order-status.php", "/status.php"})
    public String legacyPublicStatusQueryRoute(@RequestParam(required = false) String token,
                                               @RequestParam(required = false, name = "t") String tokenShort,
                                               @RequestParam(required = false) String publicToken,
                                               @RequestParam(required = false, name = "public_token") String publicTokenSnake,
                                               @RequestParam(required = false) String trackingCode,
                                               @RequestParam(required = false, name = "tracking_code") String trackingCodeSnake,
                                               @RequestParam(required = false) String tracking,
                                               @RequestParam(required = false) String code,
                                               @RequestParam(required = false) String orderNumber,
                                               @RequestParam(required = false, name = "order_number") String orderNumberSnake,
                                               @RequestParam(required = false) String order,
                                               @RequestParam(required = false) String lang,
                                               @RequestParam(required = false) String locale,
                                               @RequestParam(required = false) String lng,
                                               @RequestParam(required = false) String language,
                                               @RequestParam(required = false) Long company,
                                               @RequestParam(required = false) Long companyId,
                                               @RequestParam(required = false, name = "company_id") Long companyIdSnake) {
        String normalizedToken = resolveTrackingToken(
            token, tokenShort, publicToken, publicTokenSnake, trackingCode, trackingCodeSnake, tracking, code, orderNumber, orderNumberSnake, order);
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        if (normalizedToken == null) {
            StringBuilder redirect = new StringBuilder("redirect:/public/track");
            boolean hasQuery = false;
            if (selectedCompany != null) {
                redirect.append(hasQuery ? '&' : '?').append("company=").append(selectedCompany);
                hasQuery = true;
            }
            if (normalizedLang != null) {
                redirect.append(hasQuery ? '&' : '?').append("lang=").append(normalizedLang);
            }
            return redirect.toString();
        }
        return redirectToPublicOrder(normalizedToken, normalizedLang);
    }

    // Legacy compatibility for old nested tracking/status paths.
    @GetMapping({"/order/{token}/tracking", "/order/{token}/status"})
    public String legacyOrderNestedStatusRoute(@PathVariable String token,
                                               @RequestParam(required = false) String lang,
                                               @RequestParam(required = false) String locale,
                                               @RequestParam(required = false) String lng,
                                               @RequestParam(required = false) String language) {
        String normalizedToken = normalizePublicToken(token);
        if (normalizedToken == null) {
            return "redirect:/public/track";
        }
        return redirectToPublicOrder(normalizedToken, resolvePublicLang(lang, locale, lng, language));
    }

	// Glavna stranica za praćenje naloga
    @GetMapping({"/order", "/order.php"})
    public String trackOrderLegacyQuery(@RequestParam(required = false) String token,
                                        @RequestParam(required = false, name = "t") String tokenShort,
                                        @RequestParam(required = false) String publicToken,
                                        @RequestParam(required = false, name = "public_token") String publicTokenSnake,
                                        @RequestParam(required = false) String trackingCode,
                                        @RequestParam(required = false, name = "tracking_code") String trackingCodeSnake,
                                        @RequestParam(required = false) String tracking,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(required = false) String orderNumber,
                                        @RequestParam(required = false, name = "order_number") String orderNumberSnake,
                                        @RequestParam(required = false) String order,
                                        @RequestParam(required = false) Long company,
                                        @RequestParam(required = false) Long companyId,
                                        @RequestParam(required = false, name = "company_id") Long companyIdSnake,
                                        @RequestParam(required = false) String lang,
                                        @RequestParam(required = false) String locale,
                                        @RequestParam(required = false) String lng,
                                        @RequestParam(required = false) String language) {
        String normalizedToken = resolveTrackingToken(
            token, tokenShort, publicToken, publicTokenSnake, trackingCode, trackingCodeSnake, tracking, code, orderNumber, orderNumberSnake, order);
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        if (normalizedToken == null) {
            StringBuilder redirect = new StringBuilder("redirect:/public/track");
            boolean hasQuery = false;
            if (selectedCompany != null) {
                redirect.append(hasQuery ? '&' : '?').append("company=").append(selectedCompany);
                hasQuery = true;
            }
            if (normalizedLang != null) {
                redirect.append(hasQuery ? '&' : '?').append("lang=").append(normalizedLang);
            }
            return redirect.toString();
        }
        return redirectToPublicOrder(normalizedToken, normalizedLang);
    }

    @GetMapping("/order/{token}")
    public String trackOrder(@PathVariable String token,
                             @RequestParam(required = false) String lang,
                             @RequestParam(required = false) String locale,
                             @RequestParam(required = false) String lng,
                             @RequestParam(required = false) String language,
                             @RequestParam(required = false) String uploadError,
                             @RequestParam(required = false) String uploadErrorKey,
                             @RequestParam(required = false) String approveErrorKey,
                             @RequestParam(required = false) String approveDraftDecision,
                             @RequestParam(required = false) String approveDraftComment,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             Model model) {
        try {
            String normalizedToken = normalizePublicToken(token);
            String requestedLang = resolvePublicLang(lang, locale, lng, language);
            if (normalizedToken == null) {
                return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
            }
            if (!normalizedToken.equals(token)) {
                return redirectToPublicOrder(normalizedToken, requestedLang);
            }
            if (normalizedToken != null && !normalizedToken.isBlank()) {
                try {
                    String resolvedToken = workOrderService.resolvePublicTokenFromOrderNumber(normalizedToken);
                    if (resolvedToken != null && !resolvedToken.equals(normalizedToken)) {
                        return redirectToPublicOrder(resolvedToken, requestedLang);
                    }
                } catch (Exception ignored) {
                    // Not an order number or not resolvable, continue with token lookup
                }
            }
            String rawLang = lang != null ? lang.trim() : null;
            boolean hasCanonicalLangParam = rawLang != null && rawLang.equals(requestedLang);
            if (requestedLang != null && !hasCanonicalLangParam) {
                return redirectToPublicOrderWithParams(
                    normalizedToken,
                    requestedLang,
                    uploadError,
                    uploadErrorKey,
                    approveErrorKey,
                    approveDraftDecision,
                    approveDraftComment
                );
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
            com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityByPublicToken(normalizedToken);
            if (orderEntity.getCompany() == null || orderEntity.getCompany().getId() == null) {
                return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
            }
            workOrderService.ensureTotalsSyncedForEntity(orderEntity);
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(normalizedToken);
            List<com.printflow.dto.AttachmentDTO> attachments = safeList(fileStorageService.getAttachmentsByWorkOrder(order.getId()));
            
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
            List<com.printflow.entity.WorkOrderItem> orderItems = safeList(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
                order.getId(), orderEntity.getCompany().getId()));
            model.addAttribute("orderItems", orderItems);
            model.addAttribute("displayOrderPrice", resolveDisplayTotalPrice(orderItems, order.getPrice()));
            model.addAttribute("displayOrderCost", resolveDisplayTotalCost(orderItems, order.getCost()));
            model.addAttribute("activityFeed", safeList(activityLogService.getForWorkOrder(order.getId(), orderEntity.getCompany())));
            model.addAttribute("companyBrand", companyBrandingService.toBranding(orderEntity.getCompany(), normalizedToken, "order"));
            model.addAttribute("orderCompany", orderEntity.getCompany());
            model.addAttribute("companyCurrency", orderEntity.getCompany() != null && orderEntity.getCompany().getCurrency() != null
                ? orderEntity.getCompany().getCurrency() : "RSD");
            if (orderEntity.getCompany() != null) {
                model.addAttribute("publicCompanyId", orderEntity.getCompany().getId());
                model.addAttribute("publicCompanyName", orderEntity.getCompany().getName());
            }
            model.addAttribute("publicAllowedFileTypes", publicAllowedFileTypes);
            model.addAttribute("publicMaxFileSizeLabel", formatBytes(publicMaxFileSize));
            model.addAttribute("publicMaxFilesPerOrder", publicMaxFilesPerOrder);
            model.addAttribute("publicMaxTotalSizeLabel", formatBytes(publicMaxTotalSize));
            model.addAttribute("publicMaxTotalSizeRaw", publicMaxTotalSize);
            String safeUploadErrorKey = sanitizeMessageKey(uploadErrorKey, MAX_PUBLIC_MESSAGE_KEY_LENGTH);
            if (safeUploadErrorKey != null) {
                model.addAttribute("uploadErrorKey", safeUploadErrorKey);
            } else {
                String safeUploadError = trimAndCap(uploadError, MAX_PUBLIC_ERROR_MESSAGE_LENGTH);
                if (safeUploadError != null) {
                    model.addAttribute("uploadError", safeUploadError);
                }
            }
            String safeApproveErrorKey = sanitizeMessageKey(approveErrorKey, MAX_PUBLIC_MESSAGE_KEY_LENGTH);
            if (safeApproveErrorKey != null) {
                model.addAttribute("approveErrorKey", safeApproveErrorKey);
            }
            String safeApproveDraftDecision = normalizeDecision(approveDraftDecision);
            if (safeApproveDraftDecision != null) {
                model.addAttribute("approveDraftDecision", safeApproveDraftDecision);
            }
            String safeApproveDraftComment = trimAndCap(approveDraftComment, MAX_PUBLIC_COMMENT_LENGTH);
            if (safeApproveDraftComment != null) {
                model.addAttribute("approveDraftComment", safeApproveDraftComment);
            }
            
            return "public/order-tracking";
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("work order not found")) {
                log.info("Public track-order token not found/expired: {}", token);
            } else {
                log.warn("Public track-order failed for token={}: {}", token, e.toString(), e);
            }
            return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private static <T> java.util.List<T> safeList(java.util.List<T> value) {
        return value != null ? value : java.util.Collections.emptyList();
    }

    private Double resolveDisplayTotalPrice(List<com.printflow.entity.WorkOrderItem> items, Double fallback) {
        if (items == null || items.isEmpty()) {
            return fallback != null ? fallback : 0.0d;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (com.printflow.entity.WorkOrderItem item : items) {
            if (item == null || item.getCalculatedPrice() == null) {
                continue;
            }
            total = total.add(item.getCalculatedPrice());
        }
        return total.doubleValue();
    }

    private Double resolveDisplayTotalCost(List<com.printflow.entity.WorkOrderItem> items, Double fallback) {
        if (items == null || items.isEmpty()) {
            return fallback != null ? fallback : 0.0d;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (com.printflow.entity.WorkOrderItem item : items) {
            if (item == null || item.getCalculatedCost() == null) {
                continue;
            }
            total = total.add(item.getCalculatedCost());
        }
        return total.doubleValue();
    }
    
    // Odobrenje dizajna od strane klijenta
    @PostMapping("/order/{token}/approve-design")
    public String approveDesign(@PathVariable String token,
                               @RequestParam(required = false) Boolean approved,
                               @RequestParam(required = false) String comment,
                               @RequestParam(required = false) String lang,
                               @RequestParam(required = false) String locale,
                               @RequestParam(required = false) String lng,
                               @RequestParam(required = false) String language,
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
            com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityByPublicToken(normalizedToken);
            String normalizedLang = resolvePublicLang(lang, locale, lng, language);
            String normalizedComment = comment != null ? comment.trim() : null;
            if (normalizedComment != null && normalizedComment.isEmpty()) {
                normalizedComment = null;
            }
            model.addAttribute("langParam", normalizedLang != null ? normalizedLang : "sr");
            model.addAttribute("publicOrderToken", normalizedToken);
            model.addAttribute("publicOrderNumber", order.getOrderNumber());
            if (orderEntity.getCompany() != null) {
                model.addAttribute("publicCompanyId", orderEntity.getCompany().getId());
                model.addAttribute("publicCompanyName", orderEntity.getCompany().getName());
            }

            if (approved == null) {
                return redirectWithApproveErrorKey(normalizedToken, "order_tracking.design_decision_required", normalizedLang, null, normalizedComment);
            }
            if (!approved) {
                if (normalizedComment == null) {
                    return redirectWithApproveErrorKey(normalizedToken, "order_tracking.design_comment_required", normalizedLang, "false", null);
                }
            }
            
            // Procesuiraj odobrenje
            workOrderService.approveDesign(order.getId(), normalizedToken, approved, normalizedComment);
            
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
                                  @RequestParam(name = "locale", required = false) String locale,
                                  @RequestParam(name = "lng", required = false) String lng,
                                  @RequestParam(name = "language", required = false) String language,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Model model) {
        try {
            String normalizedToken = normalizePublicToken(token);
            String normalizedLang = resolvePublicLang(lang, locale, lng, language);
            if (normalizedToken == null) {
                return renderOrderNotFound(model, response, "order_not_found.message", HttpServletResponse.SC_NOT_FOUND);
            }
            if (files == null || files.length == 0) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.select_file", normalizedLang);
            }
            String ip = getClientIp(request);
            if (rateLimitService.isWhitelisted(ip)) {
                // Skip rate limiting for whitelisted IPs
            } else {
            if (isBanned(ip)) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.access_denied", normalizedLang);
            }
            if (!checkGlobalRateLimit(ip)) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.too_many_requests", normalizedLang);
            }
            if (!checkTokenRateLimit(normalizedToken, ip)) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.too_many_requests", normalizedLang);
            }
            if (publicUploadRateLimitEnabled) {
                boolean allowed = rateLimitService.allow(
                    "public-upload:" + ip,
                    publicUploadRateLimitMax,
                    publicUploadRateLimitWindowSeconds * 1000L
                );
                if (!allowed) {
                    return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.too_many_uploads", normalizedLang);
                }
            }
            }
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(normalizedToken);
            long existingCount = fileStorageService.countClientFiles(order.getId());
            if (existingCount + files.length > publicMaxFilesPerOrder) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.limit_reached", normalizedLang);
            }
            java.util.List<FileMeta> meta = parseFileMeta(fileMetaJson);
            if (meta.isEmpty() || meta.size() != files.length) {
                return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.metadata_mismatch", normalizedLang);
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
                    return redirectWithUploadErrorKey(normalizedToken, "public.upload.error.metadata_mismatch", normalizedLang);
                }
                used[match] = true;
                MultipartFile file = files[match];
                fileStorageService.uploadPublicFile(file, order.getId(), normalizedToken, AttachmentType.CLIENT_FILE, desc);
            }
            return redirectToPublicOrder(normalizedToken, normalizedLang);
        } catch (BillingRequiredException e) {
            logBillingBlockedPublic("upload-reference", token);
            return redirectWithUploadErrorKey(
                normalizePublicTokenOrFallback(token),
                "public.upload.error.unavailable",
                resolvePublicLang(lang, locale, lng, language)
            );
        } catch (Exception e) {
            log.warn("Public upload-reference failed for token={}: {}", token, e.toString());
            return redirectWithUploadErrorKey(
                normalizePublicTokenOrFallback(token),
                "public.upload.error.generic",
                resolvePublicLang(lang, locale, lng, language)
            );
        }
    }

    private String redirectWithUploadErrorKey(String token, String errorKey, String lang) {
        String safeToken = normalizePublicTokenOrFallback(token);
        if (safeToken == null || safeToken.isBlank()) {
            return "public/order-not-found";
        }
        String safeErrorKey = sanitizeMessageKey(errorKey, MAX_PUBLIC_MESSAGE_KEY_LENGTH);
        String encodedErrorKey = encodeQueryValue(safeErrorKey != null ? safeErrorKey : "public.upload.error.generic");
        String normalizedLang = normalizePublicLang(lang);
        if (normalizedLang == null) {
            return "redirect:/public/order/" + safeToken + "?uploadErrorKey=" + encodedErrorKey;
        }
        return "redirect:/public/order/" + safeToken + "?uploadErrorKey=" + encodedErrorKey + "&lang=" + encodeQueryValue(normalizedLang);
    }

    private String redirectWithApproveErrorKey(String token, String errorKey, String lang, String draftDecision, String draftComment) {
        String safeToken = normalizePublicTokenOrFallback(token);
        if (safeToken == null || safeToken.isBlank()) {
            return "public/order-not-found";
        }
        String normalizedLang = normalizePublicLang(lang);
        String safeErrorKey = sanitizeMessageKey(errorKey, MAX_PUBLIC_MESSAGE_KEY_LENGTH);
        StringBuilder query = new StringBuilder("approveErrorKey=")
            .append(encodeQueryValue(safeErrorKey != null ? safeErrorKey : "order_tracking.design_error"));
        if (normalizedLang != null) {
            query.append("&lang=").append(encodeQueryValue(normalizedLang));
        }
        String safeDraftDecision = normalizeDecision(draftDecision);
        if (safeDraftDecision != null) {
            query.append("&approveDraftDecision=").append(encodeQueryValue(safeDraftDecision));
        }
        String safeDraftComment = trimAndCap(draftComment, MAX_PUBLIC_COMMENT_LENGTH);
        if (safeDraftComment != null) {
            query.append("&approveDraftComment=").append(encodeQueryValue(safeDraftComment));
        }
        return "redirect:/public/order/" + safeToken + "?" + query + "#design-approval-section";
    }

    private String encodeQueryValue(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String redirectToPublicOrder(String token, String lang) {
        String normalizedLang = normalizePublicLang(lang);
        if (normalizedLang == null) {
            return "redirect:/public/order/" + token;
        }
        return "redirect:/public/order/" + token + "?lang=" + normalizedLang;
    }

    private String redirectToPublicOrderWithParams(String token,
                                                   String lang,
                                                   String uploadError,
                                                   String uploadErrorKey,
                                                   String approveErrorKey,
                                                   String approveDraftDecision,
                                                   String approveDraftComment) {
        StringBuilder redirect = new StringBuilder("redirect:/public/order/").append(token);
        boolean hasQuery = false;
        String normalizedLang = normalizePublicLang(lang);
        if (normalizedLang != null) {
            redirect.append(hasQuery ? '&' : '?').append("lang=").append(encodeQueryValue(normalizedLang));
            hasQuery = true;
        }
        if (uploadError != null && !uploadError.isBlank()) {
            String safeUploadError = trimAndCap(uploadError, MAX_PUBLIC_ERROR_MESSAGE_LENGTH);
            if (safeUploadError != null) {
                redirect.append(hasQuery ? '&' : '?').append("uploadError=").append(encodeQueryValue(safeUploadError));
                hasQuery = true;
            }
        }
        if (uploadErrorKey != null && !uploadErrorKey.isBlank()) {
            String safeUploadErrorKey = sanitizeMessageKey(uploadErrorKey, MAX_PUBLIC_MESSAGE_KEY_LENGTH);
            if (safeUploadErrorKey != null) {
                redirect.append(hasQuery ? '&' : '?').append("uploadErrorKey=").append(encodeQueryValue(safeUploadErrorKey));
                hasQuery = true;
            }
        }
        if (approveErrorKey != null && !approveErrorKey.isBlank()) {
            String safeApproveErrorKey = sanitizeMessageKey(approveErrorKey, MAX_PUBLIC_MESSAGE_KEY_LENGTH);
            if (safeApproveErrorKey != null) {
                redirect.append(hasQuery ? '&' : '?').append("approveErrorKey=").append(encodeQueryValue(safeApproveErrorKey));
                hasQuery = true;
            }
        }
        String safeApproveDraftDecision = normalizeDecision(approveDraftDecision);
        if (safeApproveDraftDecision != null) {
            redirect.append(hasQuery ? '&' : '?').append("approveDraftDecision=").append(encodeQueryValue(safeApproveDraftDecision));
            hasQuery = true;
        }
        String safeApproveDraftComment = trimAndCap(approveDraftComment, MAX_PUBLIC_COMMENT_LENGTH);
        if (safeApproveDraftComment != null) {
            redirect.append(hasQuery ? '&' : '?').append("approveDraftComment=").append(encodeQueryValue(safeApproveDraftComment));
        }
        return redirect.toString();
    }

    private String trimAndCap(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (maxLength <= 0 || trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String normalizeDecision(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(normalized) || "false".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String sanitizeMessageKey(String value, int maxLength) {
        String trimmed = trimAndCap(value, maxLength);
        if (trimmed == null) {
            return null;
        }
        if (!MESSAGE_KEY_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    private String normalizePublicLang(String lang) {
        if (lang == null) {
            return null;
        }
        String value = lang.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if ("sr".equals(value) || "en".equals(value)) {
            return value;
        }
        String compact = value.replace('_', '-');
        int dashIdx = compact.indexOf('-');
        if (dashIdx > 0) {
            String base = compact.substring(0, dashIdx);
            if ("sr".equals(base) || "en".equals(base)) {
                return base;
            }
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
        String trimmed = token.trim().replaceAll("\\s+", "");
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
    @GetMapping({"/track", "/track.php"})
    public String trackOrderForm(@RequestParam(required = false) String token,
                                 @RequestParam(required = false, name = "t") String tokenShort,
                                 @RequestParam(required = false) String publicToken,
                                 @RequestParam(required = false, name = "public_token") String publicTokenSnake,
                                 @RequestParam(required = false) String trackingCode,
                                 @RequestParam(required = false, name = "tracking_code") String trackingCodeSnake,
                                 @RequestParam(required = false) String tracking,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String orderNumber,
                                 @RequestParam(required = false, name = "order_number") String orderNumberSnake,
                                 @RequestParam(required = false) String order,
                                 @RequestParam(required = false) String lang,
                                 @RequestParam(required = false) String locale,
                                 @RequestParam(required = false) String lng,
                                 @RequestParam(required = false) String language,
                                 @RequestParam(required = false) Long company,
                                 @RequestParam(required = false) Long companyId,
                                 @RequestParam(required = false, name = "company_id") Long companyIdSnake,
                                 Model model) {
        String normalizedToken = resolveTrackingToken(
            token, tokenShort, publicToken, publicTokenSnake, trackingCode, trackingCodeSnake, tracking, code, orderNumber, orderNumberSnake, order);
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        String rawLang = lang != null ? lang.trim() : null;
        boolean hasCanonicalLangParam = rawLang != null && rawLang.equals(normalizedLang);
        if (normalizedLang != null && !hasCanonicalLangParam) {
            StringBuilder redirect = new StringBuilder("redirect:/public/track?lang=").append(normalizedLang);
            if (selectedCompany != null) {
                redirect.append("&company=").append(selectedCompany);
            }
            if (normalizedToken != null) {
                redirect.append("&token=").append(URLEncoder.encode(normalizedToken, StandardCharsets.UTF_8));
            }
            return redirect.toString();
        }
        addTrackCompanies(model);
        model.addAttribute("companyParam", selectedCompany);
        model.addAttribute("tokenParam", normalizedToken);
        applyTrackBranding(model, selectedCompany, normalizedToken);
        applyTrackCompanyDetails(model, selectedCompany, normalizedToken);
        return "public/track-order";
    }

    // Legacy compatibility: older public routes should resolve to the current tracking form.
    @GetMapping({"/track-order", "/order-tracking", "/tracking", "/track-order.html", "/order-tracking.html", "/tracking.html", "/track-order.php", "/order-tracking.php", "/tracking.php"})
    public String legacyTrackForm(@RequestParam(required = false) String token,
                                  @RequestParam(required = false, name = "t") String tokenShort,
                                  @RequestParam(required = false) String publicToken,
                                  @RequestParam(required = false, name = "public_token") String publicTokenSnake,
                                  @RequestParam(required = false) String trackingCode,
                                  @RequestParam(required = false, name = "tracking_code") String trackingCodeSnake,
                                  @RequestParam(required = false) String tracking,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String orderNumber,
                                  @RequestParam(required = false, name = "order_number") String orderNumberSnake,
                                  @RequestParam(required = false) String order,
                                  @RequestParam(required = false) String lang,
                                  @RequestParam(required = false) String locale,
                                  @RequestParam(required = false) String lng,
                                  @RequestParam(required = false) String language,
                                  @RequestParam(required = false) Long company,
                                  @RequestParam(required = false) Long companyId,
                                  @RequestParam(required = false, name = "company_id") Long companyIdSnake) {
        String normalizedToken = resolveTrackingToken(
            token, tokenShort, publicToken, publicTokenSnake, trackingCode, trackingCodeSnake, tracking, code, orderNumber, orderNumberSnake, order);
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);

        StringBuilder redirect = new StringBuilder("redirect:/public/track");
        boolean hasQuery = false;
        if (normalizedToken != null) {
            redirect.append(hasQuery ? '&' : '?').append("token=")
                .append(URLEncoder.encode(normalizedToken, StandardCharsets.UTF_8));
            hasQuery = true;
        }
        if (selectedCompany != null) {
            redirect.append(hasQuery ? '&' : '?').append("company=").append(selectedCompany);
            hasQuery = true;
        }
        if (normalizedLang != null) {
            redirect.append(hasQuery ? '&' : '?').append("lang=").append(normalizedLang);
        }
        return redirect.toString();
    }

    // Legacy compatibility: old path-based tracking routes with token in path.
    @GetMapping({"/track-order/{token}", "/order-tracking/{token}", "/tracking/{token}", "/track-order.php/{token}", "/order-tracking.php/{token}", "/tracking.php/{token}"})
    public String legacyTrackFormWithPathToken(@PathVariable String token,
                                               @RequestParam(required = false) String lang,
                                               @RequestParam(required = false) String locale,
                                               @RequestParam(required = false) String lng,
                                               @RequestParam(required = false) String language,
                                               @RequestParam(required = false) Long company,
                                               @RequestParam(required = false) Long companyId,
                                               @RequestParam(required = false, name = "company_id") Long companyIdSnake) {
        String normalizedToken = normalizePublicToken(token);
        if (normalizedToken == null) {
            return "redirect:/public/track";
        }
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        StringBuilder redirect = new StringBuilder("redirect:/public/track?token=")
            .append(URLEncoder.encode(normalizedToken, StandardCharsets.UTF_8));
        if (selectedCompany != null) {
            redirect.append("&company=").append(selectedCompany);
        }
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        if (normalizedLang != null) {
            redirect.append("&lang=").append(normalizedLang);
        }
        return redirect.toString();
    }

    // Legacy compatibility: older links can target /public/track/{token}.
    @GetMapping({"/track/{token}", "/track.php/{token}"})
    public String trackOrderLegacyPath(@PathVariable String token,
                                       @RequestParam(required = false) String lang,
                                       @RequestParam(required = false) String locale,
                                       @RequestParam(required = false) String lng,
                                       @RequestParam(required = false) String language,
                                       @RequestParam(required = false) Long company,
                                       @RequestParam(required = false) Long companyId,
                                       @RequestParam(required = false, name = "company_id") Long companyIdSnake) {
        String normalizedToken = normalizePublicTokenOrFallback(token);
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        String redirect = "redirect:/public/track?token=" + URLEncoder.encode(normalizedToken, StandardCharsets.UTF_8);
        if (selectedCompany != null) {
            redirect += "&company=" + selectedCompany;
        }
        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        if (normalizedLang != null) {
            redirect += "&lang=" + normalizedLang;
        }
        return redirect;
    }
    
    @PostMapping("/track")
    public String trackOrderSubmit(@RequestParam(required = false) String trackingCode,
                                   @RequestParam(required = false, name = "tracking_code") String trackingCodeSnake,
                                   @RequestParam(required = false) String token,
                                   @RequestParam(required = false, name = "t") String tokenShort,
                                   @RequestParam(required = false) String publicToken,
                                   @RequestParam(required = false, name = "public_token") String publicTokenSnake,
                                   @RequestParam(required = false) String tracking,
                                   @RequestParam(required = false) String code,
                                   @RequestParam(required = false) String orderNumber,
                                   @RequestParam(required = false, name = "order_number") String orderNumberSnake,
                                   @RequestParam(required = false) String order,
                                   @RequestParam(required = false) Long company,
                                   @RequestParam(required = false) Long companyId,
                                   @RequestParam(required = false, name = "company_id") Long companyIdSnake,
                                   @RequestParam(required = false) String lang,
                                   @RequestParam(required = false) String locale,
                                   @RequestParam(required = false) String lng,
                                   @RequestParam(required = false) String language,
                                   HttpServletRequest request,
                                   Model model) {
        String submittedCode = firstNonBlank(
            trackingCode, trackingCodeSnake, token, tokenShort, publicToken, publicTokenSnake, tracking, code, orderNumber, orderNumberSnake, order);
        Long selectedCompany = resolveCompanyParam(company, companyId, companyIdSnake);
        model.addAttribute("submittedTrackingCode", submittedCode);
        String ip = getClientIp(request);
        if (!rateLimitService.isWhitelisted(ip)) {
            if (isBanned(ip)) {
                return renderTrackFormError(model, "track.error.too_many_requests", selectedCompany, submittedCode, submittedCode, false);
            }
            if (!checkGlobalRateLimit(ip)) {
                return renderTrackFormError(model, "track.error.too_many_requests", selectedCompany, submittedCode, submittedCode, false);
            }
            if (publicTrackRateLimitEnabled) {
                boolean allowed = rateLimitService.allow(
                    "public-track-form:" + ip,
                    publicTrackRateLimitMax,
                    publicTrackRateLimitWindowSeconds * 1000L
                );
                if (!allowed) {
                    return renderTrackFormError(model, "track.error.too_many_requests", selectedCompany, submittedCode, submittedCode, false);
                }
            }
        }
        if (submittedCode == null) {
            return renderTrackFormError(model, "track.error.required", selectedCompany, submittedCode, submittedCode, true);
        }

        String trimmedCode = submittedCode.trim().replaceAll("\\s+", "");
        if (trimmedCode.length() > MAX_PUBLIC_TOKEN_LENGTH) {
            return renderTrackFormError(model, "track.error.invalid_code", selectedCompany, trimmedCode, trimmedCode, true);
        }
        if (!TRACKING_CODE_PATTERN.matcher(trimmedCode).matches()) {
            return renderTrackFormError(model, "track.error.invalid_code", selectedCompany, trimmedCode, trimmedCode, true);
        }
        String resolvedToken = trimmedCode;
        Long tokenCompanyId = workOrderRepository
            .findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(trimmedCode, LocalDateTime.now())
            .orElse(null);
        if (tokenCompanyId == null) {
            try {
                String resolvedByOrder = workOrderService.resolvePublicTokenFromOrderNumber(trimmedCode);
                if (resolvedByOrder != null && !resolvedByOrder.isBlank()) {
                    resolvedToken = resolvedByOrder;
                    tokenCompanyId = workOrderRepository
                        .findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(resolvedToken, LocalDateTime.now())
                        .orElse(null);
                }
            } catch (Exception ignored) {
                // Not a resolvable order number; keep original input for the next page.
            }
        }

        if (selectedCompany != null && tokenCompanyId != null && !selectedCompany.equals(tokenCompanyId)) {
            return renderTrackFormError(model, "track.error.company_mismatch", selectedCompany, trimmedCode, trimmedCode, true, tokenCompanyId);
        }

        String normalizedLang = resolvePublicLang(lang, locale, lng, language);
        if (normalizedLang == null) {
            return "redirect:/public/order/" + resolvedToken;
        }
        return "redirect:/public/order/" + resolvedToken + "?lang=" + normalizedLang;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String resolvePublicLang(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizePublicLang(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String resolveTrackingToken(String... candidates) {
        return normalizePublicToken(firstNonBlank(candidates));
    }

    private Long resolveCompanyParam(Long... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Long value : candidates) {
            if (value != null) {
                return value;
            }
        }
        return null;
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
        return renderTrackFormError(model, errorKey, company, token, submittedTrackingCode, contextualBranding, null);
    }

    private String renderTrackFormError(Model model,
                                        String errorKey,
                                        Long company,
                                        String token,
                                        String submittedTrackingCode,
                                        boolean contextualBranding,
                                        Long suggestedCompanyId) {
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
        if ("track.error.company_mismatch".equals(errorKey) && suggestedCompanyId != null) {
            model.addAttribute("mismatchSuggestedCompanyId", suggestedCompanyId);
            companyRepository.findById(suggestedCompanyId)
                .map(com.printflow.entity.Company::getName)
                .ifPresent(name -> model.addAttribute("mismatchSuggestedCompanyName", name));
        }
        applyTrackCompanyDetails(model, company, normalizedToken);
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

    private void applyTrackCompanyDetails(Model model, Long company, String normalizedToken) {
        Long resolvedCompanyId = null;
        if (normalizedToken != null) {
            resolvedCompanyId = workOrderRepository
                .findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(normalizedToken, LocalDateTime.now())
                .orElse(null);
        }
        if (resolvedCompanyId == null) {
            resolvedCompanyId = company;
        }
        if (resolvedCompanyId == null) {
            model.addAttribute("trackCompany", null);
            return;
        }
        model.addAttribute("trackCompany", companyRepository.findById(resolvedCompanyId).orElse(null));
    }

    private java.util.Locale resolveRequestedLocale(String lang, java.util.Locale fallback) {
        String normalizedLang = normalizePublicLang(lang);
        if ("sr".equals(normalizedLang)) {
            return new java.util.Locale("sr");
        }
        if ("en".equals(normalizedLang)) {
            return java.util.Locale.ENGLISH;
        }
        String fallbackLanguage = fallback != null ? normalizePublicLang(fallback.getLanguage()) : null;
        if ("sr".equals(fallbackLanguage)) {
            return new java.util.Locale("sr");
        }
        if ("en".equals(fallbackLanguage)) {
            return java.util.Locale.ENGLISH;
        }
        return java.util.Locale.ENGLISH;
    }

    private boolean isQuotePdfPubliclyAvailable(WorkOrderDTO order) {
        if (order == null || order.getQuoteStatus() == null) {
            return false;
        }
        String status = order.getQuoteStatus().name();
        return "READY".equals(status) || "SENT".equals(status) || "APPROVED".equals(status);
    }
}
