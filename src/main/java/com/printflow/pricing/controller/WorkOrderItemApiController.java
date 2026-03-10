package com.printflow.pricing.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.pricing.dto.PricingCalculateRequest;
import com.printflow.pricing.dto.PricingCalculateResponse;
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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/workorders")
public class WorkOrderItemApiController {

    private final CurrentContextService currentContextService;
    private final PricingEngineService pricingEngineService;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ClientPricingProfileService pricingProfileService;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;
    private final BillingAccessService billingAccessService;

    public WorkOrderItemApiController(CurrentContextService currentContextService,
                                      PricingEngineService pricingEngineService,
                                      WorkOrderRepository workOrderRepository,
                                      WorkOrderItemRepository workOrderItemRepository,
                                      ProductVariantRepository variantRepository,
                                      ClientPricingProfileService pricingProfileService,
                                      ActivityLogService activityLogService,
                                      ObjectMapper objectMapper,
                                      BillingAccessService billingAccessService) {
        this.currentContextService = currentContextService;
        this.pricingEngineService = pricingEngineService;
        this.workOrderRepository = workOrderRepository;
        this.workOrderItemRepository = workOrderItemRepository;
        this.variantRepository = variantRepository;
        this.pricingProfileService = pricingProfileService;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.billingAccessService = billingAccessService;
    }

    @PostMapping("/{workOrderId}/items")
    public WorkOrderItemResponse addItem(@PathVariable Long workOrderId,
                                         @Valid @RequestBody WorkOrderAddItemRequest request) {
        Company company = currentContextService.currentCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        WorkOrder workOrder = workOrderRepository.findByIdAndCompany_Id(workOrderId, company.getId())
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
        if (workOrder.getClient() != null) {
            pricingProfileService.recordPrice(workOrder.getClient(), variant, saved.getCalculatedPrice());
        }
        activityLogService.log(workOrder,
            "CALCULATION_ADDED_TO_ORDER",
            "Added item: " + variant.getName() + " x " + saved.getQuantity(),
            currentContextService.currentUser().getId());

        WorkOrderItemResponse response = new WorkOrderItemResponse();
        response.setId(saved.getId());
        response.setWorkOrderId(workOrder.getId());
        response.setVariantId(variant.getId());
        response.setQuantity(saved.getQuantity());
        response.setCalculatedCost(saved.getCalculatedCost());
        response.setCalculatedPrice(saved.getCalculatedPrice());
        response.setMarginPercent(saved.getMarginPercent());
        return response;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BillingRequiredException.class)
    public ResponseEntity<Map<String, String>> handleBillingRequired(BillingRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}
