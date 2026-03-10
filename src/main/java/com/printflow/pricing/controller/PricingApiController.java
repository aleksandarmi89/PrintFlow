package com.printflow.pricing.controller;

import com.printflow.pricing.dto.PricingCalculateRequest;
import com.printflow.pricing.dto.PricingCalculateResponse;
import com.printflow.pricing.dto.PricingVariantRequirementsResponse;
import com.printflow.pricing.service.PricingEngineService;
import com.printflow.service.BillingAccessService;
import com.printflow.service.BillingRequiredException;
import com.printflow.service.CurrentContextService;
import com.printflow.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@RestController
@RequestMapping("/api/pricing")
public class PricingApiController {

    private final CurrentContextService currentContextService;
    private final PricingEngineService pricingEngineService;
    private final BillingAccessService billingAccessService;

    public PricingApiController(CurrentContextService currentContextService,
                                PricingEngineService pricingEngineService,
                                BillingAccessService billingAccessService) {
        this.currentContextService = currentContextService;
        this.pricingEngineService = pricingEngineService;
        this.billingAccessService = billingAccessService;
    }

    @PostMapping("/calculate")
    public PricingCalculateResponse calculate(@Valid @RequestBody PricingCalculateRequest request) {
        var company = currentContextService.currentCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        return pricingEngineService.calculate(company, request);
    }

    @GetMapping("/variants/{variantId}/requirements")
    public PricingVariantRequirementsResponse requirements(@PathVariable Long variantId) {
        var company = currentContextService.currentCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        return pricingEngineService.getVariantRequirements(company, variantId);
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
