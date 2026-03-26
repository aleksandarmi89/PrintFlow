package com.printflow.pricing.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.pricing.dto.PricingCalculateRequest;
import com.printflow.pricing.dto.PricingCalculateResponse;
import com.printflow.pricing.dto.PricingVariantRequirementsResponse;
import com.printflow.pricing.dto.WorkOrderAddItemRequest;
import com.printflow.pricing.dto.WorkOrderItemResponse;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.pricing.service.PricingEngineService;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.service.ActivityLogService;
import com.printflow.service.BillingAccessService;
import com.printflow.service.BillingRequiredException;
import com.printflow.service.ClientPricingProfileService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.AuditLogService;
import com.printflow.service.WorkOrderTotalsSyncService;
import com.printflow.entity.enums.AuditAction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/workorders")
public class WorkOrderItemApiController {
    private static final Logger log = LoggerFactory.getLogger(WorkOrderItemApiController.class);

    private final CurrentContextService currentContextService;
    private final PricingEngineService pricingEngineService;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ClientPricingProfileService pricingProfileService;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;
    private final BillingAccessService billingAccessService;
    private final WorkOrderTotalsSyncService workOrderTotalsSyncService;
    private final AuditLogService auditLogService;

    public WorkOrderItemApiController(CurrentContextService currentContextService,
                                      PricingEngineService pricingEngineService,
                                      WorkOrderRepository workOrderRepository,
                                      WorkOrderItemRepository workOrderItemRepository,
                                      ProductVariantRepository variantRepository,
                                      ClientPricingProfileService pricingProfileService,
                                      ActivityLogService activityLogService,
                                      ObjectMapper objectMapper,
                                      BillingAccessService billingAccessService,
                                      WorkOrderTotalsSyncService workOrderTotalsSyncService,
                                      AuditLogService auditLogService) {
        this.currentContextService = currentContextService;
        this.pricingEngineService = pricingEngineService;
        this.workOrderRepository = workOrderRepository;
        this.workOrderItemRepository = workOrderItemRepository;
        this.variantRepository = variantRepository;
        this.pricingProfileService = pricingProfileService;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.billingAccessService = billingAccessService;
        this.workOrderTotalsSyncService = workOrderTotalsSyncService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/{workOrderId}/items")
    public WorkOrderItemResponse addItem(@PathVariable Long workOrderId,
                                         @Valid @RequestBody WorkOrderAddItemRequest request) {
        Company company = currentContextService.currentCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        WorkOrder workOrder = workOrderRepository.findWithRelationsByIdAndCompany_Id(workOrderId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Work order not found"));

        PricingCalculateRequest calcRequest = new PricingCalculateRequest();
        calcRequest.setVariantId(request.getVariantId());
        calcRequest.setQuantity(request.getQuantity());
        calcRequest.setWidthMm(request.getWidthMm());
        calcRequest.setHeightMm(request.getHeightMm());
        calcRequest.setAttributes(request.getAttributes());
        if (workOrder.getClient() != null) {
            calcRequest.setClientId(workOrder.getClient().getId());
        }
        validateRequiredFields(request, calcRequest);

        PricingCalculateResponse calcResponse = pricingEngineService.calculate(company, calcRequest);

        ProductVariant variant = variantRepository.findByIdAndCompany_Id(request.getVariantId(), company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        WorkOrderItem item = new WorkOrderItem();
        item.setCompany(company);
        item.setWorkOrder(workOrder);
        item.setVariant(variant);
        item.setQuantity(nonNullQuantity(request.getQuantity()));
        item.setWidthMm(request.getWidthMm());
        item.setHeightMm(request.getHeightMm());
        item.setAttributesJson(writeJsonSafe(request.getAttributes()));
        item.setCalculatedCost(calcResponse.getTotalCost());
        item.setCalculatedPrice(calcResponse.getTotalPrice());
        item.setProfitAmount(calcResponse.getProfitAmount());
        item.setMarginPercent(calcResponse.getMarginPercent());
        item.setCurrency(calcResponse.getCurrency());
        item.setBreakdownJson(writeBreakdown(calcResponse));
        item.setPricingSnapshotJson(writeSnapshot(calcRequest, calcResponse));
        item.setPriceLocked(true);
        item.setPriceCalculatedAt(java.time.LocalDateTime.now());

        WorkOrderItem saved = workOrderItemRepository.save(item);
        workOrderTotalsSyncService.syncFromItems(workOrder);
        if (workOrder.getClient() != null) {
            pricingProfileService.recordPrice(workOrder.getClient(), variant, saved.getCalculatedPrice());
        }
        Long actorUserId = null;
        try {
            actorUserId = currentContextService.currentUser().getId();
        } catch (Exception ignored) {
            // Keep operation successful even if actor resolution fails.
        }
        activityLogService.log(workOrder,
            "CALCULATION_ADDED_TO_ORDER",
            "Added item: " + safeVariantName(variant) + " x " + saved.getQuantity(),
            actorUserId);
        auditLogService.log(
            AuditAction.CREATE,
            "WorkOrderItem",
            saved.getId(),
            null,
            saved.getCalculatedPrice() != null ? saved.getCalculatedPrice().toPlainString() : null,
            "Added pricing item to work order #" + workOrder.getOrderNumber(),
            company
        );

        WorkOrderItemResponse response = new WorkOrderItemResponse();
        response.setId(saved.getId());
        response.setWorkOrderId(workOrder.getId());
        response.setVariantId(variant.getId());
        response.setQuantity(saved.getQuantity());
        response.setCalculatedCost(saved.getCalculatedCost());
        response.setCalculatedPrice(saved.getCalculatedPrice());
        response.setMarginPercent(saved.getMarginPercent());
        OrderTotals totals = resolveCurrentOrderTotals(workOrder, company);
        response.setWorkOrderTotalPrice(totals.totalPrice());
        response.setWorkOrderTotalCost(totals.totalCost());
        response.setCurrency(resolveCurrency(workOrder));
        return response;
    }

    @DeleteMapping("/{workOrderId}/items/{itemId}")
    public ResponseEntity<Map<String, Object>> removeItem(@PathVariable Long workOrderId,
                                                          @PathVariable Long itemId) {
        Company company = currentContextService.currentCompany();
        WorkOrder workOrder = workOrderRepository.findWithRelationsByIdAndCompany_Id(workOrderId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Work order not found"));
        WorkOrderItem item = workOrderItemRepository.findByIdAndCompany_Id(itemId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Work order item not found"));
        if (item.getWorkOrder() == null || !workOrderId.equals(item.getWorkOrder().getId())) {
            throw new ResourceNotFoundException("Work order item not found");
        }

        String variantName = safeVariantName(item.getVariant());
        workOrderItemRepository.delete(item);
        workOrderTotalsSyncService.syncFromItems(workOrder);

        Long actorUserId = null;
        try {
            actorUserId = currentContextService.currentUser().getId();
        } catch (Exception ignored) {
            // Keep operation successful even if actor resolution fails.
        }
        activityLogService.log(workOrder,
            "CALCULATION_REMOVED_FROM_ORDER",
            "Removed item: " + variantName + " x " + item.getQuantity(),
            actorUserId);
        auditLogService.log(
            AuditAction.DELETE,
            "WorkOrderItem",
            item.getId(),
            item.getCalculatedPrice() != null ? item.getCalculatedPrice().toPlainString() : null,
            null,
            "Removed pricing item from work order #" + workOrder.getOrderNumber(),
            company
        );

        OrderTotals totals = resolveCurrentOrderTotals(workOrder, company);
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "workOrderId", workOrderId,
            "itemId", itemId,
            "workOrderTotalPrice", totals.totalPrice() != null ? totals.totalPrice().doubleValue() : 0.0d,
            "workOrderTotalCost", totals.totalCost() != null ? totals.totalCost().doubleValue() : 0.0d,
            "currency", resolveCurrency(workOrder)
        ));
    }

    private OrderTotals resolveCurrentOrderTotals(WorkOrder workOrder, Company company) {
        if (workOrder == null || workOrder.getId() == null) {
            return new OrderTotals(null, null);
        }
        Long orderId = workOrder.getId();
        Long companyId = company != null ? company.getId() : null;
        java.util.List<Long> ids = java.util.List.of(orderId);

        BigDecimal totalPrice = null;
        BigDecimal totalCost = null;
        java.util.List<Object[]> priceRows = companyId != null
            ? workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(ids, companyId)
            : workOrderItemRepository.sumPriceByWorkOrderIds(ids);
        java.util.List<Object[]> costRows = companyId != null
            ? workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(ids, companyId)
            : workOrderItemRepository.sumCostByWorkOrderIds(ids);

        if (priceRows != null && !priceRows.isEmpty() && priceRows.get(0) != null && priceRows.get(0).length > 1 && priceRows.get(0)[1] instanceof Number n) {
            totalPrice = BigDecimal.valueOf(n.doubleValue());
        }
        if (costRows != null && !costRows.isEmpty() && costRows.get(0) != null && costRows.get(0).length > 1 && costRows.get(0)[1] instanceof Number n) {
            totalCost = BigDecimal.valueOf(n.doubleValue());
        }
        if (totalPrice == null) {
            totalPrice = BigDecimal.ZERO;
        }
        if (totalCost == null) {
            totalCost = BigDecimal.ZERO;
        }
        return new OrderTotals(totalPrice, totalCost);
    }

    private record OrderTotals(BigDecimal totalPrice, BigDecimal totalCost) {
    }

    private String resolveCurrency(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getCompany() == null || workOrder.getCompany().getCurrency() == null) {
            return "RSD";
        }
        String currency = workOrder.getCompany().getCurrency().trim();
        return currency.isEmpty() ? "RSD" : currency.toUpperCase(Locale.ROOT);
    }

    private String safeVariantName(ProductVariant variant) {
        try {
            if (variant == null || variant.getName() == null || variant.getName().isBlank()) {
                return "variant";
            }
            return variant.getName().trim();
        } catch (RuntimeException ex) {
            // Defensive guard for detached/lazy variant proxies during delete flow.
            return "variant";
        }
    }

    private String writeJsonSafe(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String writeBreakdown(PricingCalculateResponse response) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("breakdown", response.getBreakdown());
        payload.put("appliedRules", response.getAppliedRules());
        payload.put("warnings", response.getWarnings());
        return writeJsonSafe(payload);
    }

    private String writeSnapshot(PricingCalculateRequest request, PricingCalculateResponse response) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("request", request);
        payload.put("response", response);
        payload.put("breakdown", response.getBreakdown());
        payload.put("appliedRules", response.getAppliedRules());
        payload.put("warnings", response.getWarnings());
        return writeJsonSafe(payload);
    }

    private BigDecimal nonNullQuantity(BigDecimal quantity) {
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    private void validateRequiredFields(WorkOrderAddItemRequest request, PricingCalculateRequest calcRequest) {
        Company company = currentContextService.currentCompany();
        PricingVariantRequirementsResponse requirements = pricingEngineService.getVariantRequirements(company, request.getVariantId());
        if (requirements.isRequiresDimensions()) {
            Integer width = calcRequest.getWidthMm();
            Integer height = calcRequest.getHeightMm();
            if (width == null || width <= 0 || height == null || height <= 0) {
                throw new IllegalArgumentException("pricing.calculate.validation.dimensions_required");
            }
        }
        if (requirements.isRequiresMeters()) {
            BigDecimal meters = extractPositiveDecimal(calcRequest.getAttributes(), "meters");
            if (meters == null || meters.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("pricing.calculate.validation.meters_required");
            }
        }
        if (requirements.isRequiresMinutes()) {
            BigDecimal minutes = extractPositiveDecimal(calcRequest.getAttributes(), "minutes");
            if (minutes == null || minutes.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("pricing.calculate.validation.minutes_required");
            }
        }
    }

    private BigDecimal extractPositiveDecimal(Map<String, Object> attributes, String key) {
        if (attributes == null || key == null || key.isBlank()) {
            return null;
        }
        Object raw = attributes.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage().trim() : "";
        if (message.startsWith("pricing.calculate.validation.") || message.startsWith("pricing.calculate.")) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", message,
                "messageKey", message
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", message.isEmpty() ? "Bad request" : message));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
            ? ex.getMessage()
            : "Not Found";
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "error", message,
            "messageKey", "api.error.not_found"
        ));
    }

    @ExceptionHandler(BillingRequiredException.class)
    public ResponseEntity<Map<String, String>> handleBillingRequired(BillingRequiredException ex) {
        String messageKey = ex.getMessage() != null && !ex.getMessage().isBlank()
            ? ex.getMessage()
            : "pricing.calculate.billing_required";
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
            "error", "pricing.calculate.billing_required",
            "messageKey", messageKey
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unexpected error while adding pricing item to work order", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "error", "pricing.calculate.add_failed",
                "messageKey", "pricing.calculate.add_failed"
            ));
    }
}
