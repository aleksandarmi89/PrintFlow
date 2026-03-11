package com.printflow.config;

import com.printflow.service.BillingAccessService;
import com.printflow.service.BillingRequiredException;
import com.printflow.service.PlanLimitExceededException;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.TenantContextService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final TenantContextService tenantContextService;
    private final BillingAccessService billingAccessService;

    public ApiExceptionHandler(TenantContextService tenantContextService,
                               BillingAccessService billingAccessService) {
        this.tenantContextService = tenantContextService;
        this.billingAccessService = billingAccessService;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        logClientError("NOT_FOUND", ex, request);
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        logClientError("NO_RESOURCE", ex, request);
        return error(HttpStatus.NOT_FOUND, "Not found", request);
    }

    @ExceptionHandler({IllegalArgumentException.class, BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest request) {
        logClientError("BAD_REQUEST", ex, request);
        return error(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        logClientError("ILLEGAL_STATE", ex, request);
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<Map<String, Object>> handleLazyInit(LazyInitializationException ex, HttpServletRequest request) {
        logServerError("LAZY_INIT", ex, request);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", request);
    }

    @ExceptionHandler(BillingRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleBillingRequired(BillingRequiredException ex, HttpServletRequest request) {
        logClientError("BILLING_REQUIRED", ex, request);
        String message = resolveBillingErrorMessage();
        return error(HttpStatus.FORBIDDEN, message, request);
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(PlanLimitExceededException ex, HttpServletRequest request) {
        logClientError("PLAN_LIMIT", ex, request);
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        logClientError("ACCESS_DENIED", ex, request);
        // Keep 404 to avoid leaking resource existence across tenants.
        return error(HttpStatus.NOT_FOUND, "Not found", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        logClientError("DATA_INTEGRITY", ex, request);
        return error(HttpStatus.CONFLICT, "Conflict: duplicate or invalid data", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status.is5xxServerError()) {
            logServerError("RESPONSE_STATUS", ex, request);
        } else {
            logClientError("RESPONSE_STATUS", ex, request);
        }
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = status.getReasonPhrase();
        }
        return error(status, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception ex, HttpServletRequest request) {
        logServerError("UNHANDLED", ex, request);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        body.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        if (request != null) {
            body.put("path", request.getRequestURI());
        }
        return ResponseEntity.status(status).body(body);
    }

    private String resolveBillingErrorMessage() {
        Long companyId = tenantContextService.getCurrentCompanyId();
        if (companyId != null && billingAccessService.getTrialEnd(companyId) != null) {
            return "billing.notice.expired_with_date";
        }
        return "billing.notice.expired";
    }

    private void logClientError(String tag, Exception ex, HttpServletRequest request) {
        logByLevel(tag, ex, request, false);
    }

    private void logServerError(String tag, Exception ex, HttpServletRequest request) {
        logByLevel(tag, ex, request, true);
    }

    private void logByLevel(String tag, Exception ex, HttpServletRequest request, boolean serverError) {
        Long userId = tenantContextService.getCurrentUserId();
        Long companyId = tenantContextService.getCurrentCompanyId();
        String method = request != null ? request.getMethod() : "N/A";
        String path = request != null ? request.getRequestURI() : "N/A";
        if (serverError) {
            log.error("[API:{}] {} {} userId={} companyId={} message={}",
                tag, method, path, userId, companyId, ex.toString(), ex);
        } else {
            log.warn("[API:{}] {} {} userId={} companyId={} message={}",
                tag, method, path, userId, companyId, ex.toString());
        }
    }
}
