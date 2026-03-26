package com.printflow.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printflow.service.ActivityLogService;
import com.printflow.service.AuditLogService;
import com.printflow.service.BillingAccessService;
import com.printflow.service.BillingRequiredException;
import com.printflow.service.ClientPricingProfileService;
import com.printflow.service.CurrentContextService;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.WorkOrderTotalsSyncService;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.pricing.service.PricingEngineService;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class WorkOrderItemApiControllerTest {

    @Test
    void handleBillingRequiredFallsBackToStableMessageKeyWhenExceptionMessageBlank() {
        WorkOrderItemApiController controller = controller();

        ResponseEntity<Map<String, String>> response =
            controller.handleBillingRequired(new BillingRequiredException(" "));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals("pricing.calculate.billing_required", response.getBody().get("error"));
        assertEquals("pricing.calculate.billing_required", response.getBody().get("messageKey"));
    }

    @Test
    void handleNotFoundReturnsStableMessageKey() {
        WorkOrderItemApiController controller = controller();

        ResponseEntity<Map<String, String>> response =
            controller.handleNotFound(new ResourceNotFoundException("Work order item not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Work order item not found", response.getBody().get("error"));
        assertEquals("api.error.not_found", response.getBody().get("messageKey"));
    }

    @Test
    void handleUnexpectedReturnsStableServerErrorKey() {
        WorkOrderItemApiController controller = controller();

        ResponseEntity<Map<String, String>> response =
            controller.handleUnexpected(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("pricing.calculate.add_failed", response.getBody().get("error"));
        assertEquals("pricing.calculate.add_failed", response.getBody().get("messageKey"));
    }

    private WorkOrderItemApiController controller() {
        return new WorkOrderItemApiController(
            mock(CurrentContextService.class),
            mock(PricingEngineService.class),
            mock(WorkOrderRepository.class),
            mock(WorkOrderItemRepository.class),
            mock(ProductVariantRepository.class),
            mock(ClientPricingProfileService.class),
            mock(ActivityLogService.class),
            new ObjectMapper(),
            mock(BillingAccessService.class),
            mock(WorkOrderTotalsSyncService.class),
            mock(AuditLogService.class)
        );
    }
}
