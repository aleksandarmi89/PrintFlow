package com.printflow.config;

import com.printflow.service.BillingAccessService;
import com.printflow.service.TenantContextService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.printflow.service.PlanLimitExceededException;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.BillingRequiredException;
import org.thymeleaf.exceptions.TemplateInputException;
import java.time.format.DateTimeFormatter;

@ControllerAdvice
@Controller
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final TenantContextService tenantContextService;
    private final BillingAccessService billingAccessService;

    public GlobalExceptionHandler(TenantContextService tenantContextService,
                                  BillingAccessService billingAccessService) {
        this.tenantContextService = tenantContextService;
        this.billingAccessService = billingAccessService;
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        BindException.class,
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(Exception ex, HttpServletRequest request, Model model) {
        logClientError("BAD_REQUEST", ex, request);
        populateModel(model, HttpStatus.BAD_REQUEST, "Invalid request.");
        return "error/400";
    }

    @ExceptionHandler({
        EntityNotFoundException.class,
        NoSuchElementException.class,
        ResourceNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Exception ex, HttpServletRequest request, Model model) {
        logClientError("NOT_FOUND", ex, request);
        populateModel(model, HttpStatus.NOT_FOUND, "The requested resource was not found.");
        return "error/404";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request, Model model) {
        String path = request != null ? request.getRequestURI() : "N/A";
        String method = request != null ? request.getMethod() : "N/A";
        log.debug("[NO_RESOURCE] {} {} {}", method, path, ex.getMessage());
        populateModel(model, HttpStatus.NOT_FOUND, "The requested resource was not found.");
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Model model) {
        logClientError("NOT_FOUND", ex, request);
        populateModel(model, HttpStatus.NOT_FOUND, "The requested resource was not found.");
        return "error/404";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request, Model model) {
        logClientError("METHOD_NOT_ALLOWED", ex, request);
        populateModel(model, HttpStatus.METHOD_NOT_ALLOWED, "This action is not allowed.");
        return "error/405";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatus(ResponseStatusException ex, HttpServletRequest request, Model model) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        logByStatus("RESPONSE_STATUS", ex, request, status);
        populateModel(model, status, status.is4xxClientError()
            ? "The request could not be processed."
            : "Something went wrong. Please try again later.");
        return viewForStatus(status);
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handlePlanLimit(PlanLimitExceededException ex, HttpServletRequest request, Model model) {
        logClientError("PLAN_LIMIT", ex, request);
        String message = ex.getMessage();
        if (message != null && message.startsWith("plan.limit.")) {
            model.addAttribute("errorMessageKey", message);
        }
        populateModel(model, HttpStatus.FORBIDDEN, ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler(BillingRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleBillingRequired(BillingRequiredException ex, HttpServletRequest request, Model model) {
        logClientError("BILLING_REQUIRED", ex, request);
        Long companyId = tenantContextService.getCurrentCompanyId();
        String messageKey = "billing.notice.expired";
        String messageArg = null;
        if (companyId != null) {
            var trialEnd = billingAccessService.getTrialEnd(companyId);
            if (trialEnd != null) {
                messageKey = "billing.notice.expired_with_date";
                messageArg = trialEnd.toLocalDate().format(DateTimeFormatter.ISO_DATE);
            }
        }
        model.addAttribute("errorMessageKey", messageKey);
        model.addAttribute("errorMessageArg", messageArg);
        populateModel(model, HttpStatus.FORBIDDEN, ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnhandled(Exception ex, HttpServletRequest request, Model model) {
        logServerError("UNHANDLED", ex, request);
        populateModel(model, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
        return "error/500";
    }

    @ExceptionHandler(TemplateInputException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleTemplateInput(TemplateInputException ex, HttpServletRequest request, Model model) {
        logServerError("TEMPLATE_ERROR", ex, request);
        populateModel(model, HttpStatus.INTERNAL_SERVER_ERROR, "Template error. Please try again later.");
        return "error/500";
    }

    private void populateModel(Model model, HttpStatus status, String message) {
        model.addAttribute("status", status.value());
        model.addAttribute("errorTitle", status.getReasonPhrase());
        model.addAttribute("errorMessage", message);
        model.addAttribute("timestamp", LocalDateTime.now());
    }

    private String viewForStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "error/400";
            case FORBIDDEN -> "error/403";
            case NOT_FOUND -> "error/404";
            case METHOD_NOT_ALLOWED -> "error/405";
            default -> "error/500";
        };
    }

    private void logClientError(String tag, Exception ex, HttpServletRequest request) {
        logByStatus(tag, ex, request, HttpStatus.BAD_REQUEST);
    }

    private void logServerError(String tag, Exception ex, HttpServletRequest request) {
        logByStatus(tag, ex, request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void logByStatus(String tag, Exception ex, HttpServletRequest request, HttpStatus status) {
        Long userId = tenantContextService.getCurrentUserId();
        Long companyId = tenantContextService.getCurrentCompanyId();
        String path = request != null ? request.getRequestURI() : "N/A";
        String method = request != null ? request.getMethod() : "N/A";
        if (status.is4xxClientError()) {
            log.warn("[{}] {} {} userId={} companyId={} message={}", tag, method, path, userId, companyId, ex.toString());
        } else {
            log.error("[{}] {} {} userId={} companyId={} message={}", tag, method, path, userId, companyId, ex.toString(), ex);
        }
    }
}
