package com.printflow.config;

import com.printflow.service.BillingAccessService;
import com.printflow.service.BillingRequiredException;
import com.printflow.service.TenantContextService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private TenantContextService tenantContextService;
    private BillingAccessService billingAccessService;
    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        tenantContextService = Mockito.mock(TenantContextService.class);
        billingAccessService = Mockito.mock(BillingAccessService.class);
        handler = new ApiExceptionHandler(tenantContextService, billingAccessService);
    }

    @Test
    void billingRequiredReturnsExpiredKeyWhenNoTrialEnd() {
        when(tenantContextService.getCurrentCompanyId()).thenReturn(42L);
        when(billingAccessService.getTrialEnd(42L)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pricing/calculate");
        ResponseEntity<Map<String, Object>> response =
            handler.handleBillingRequired(new BillingRequiredException("billing.notice.expired"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).containsEntry("error", "billing.notice.expired");
        assertThat(response.getBody()).containsEntry("path", "/api/pricing/calculate");
        assertThat(response.getBody()).containsEntry("status", 403);
    }

    @Test
    void billingRequiredReturnsDateKeyWhenTrialEndExists() {
        when(tenantContextService.getCurrentCompanyId()).thenReturn(7L);
        when(billingAccessService.getTrialEnd(7L)).thenReturn(LocalDateTime.now().minusDays(1));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/workorders/1/items");
        ResponseEntity<Map<String, Object>> response =
            handler.handleBillingRequired(new BillingRequiredException("billing.notice.expired"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).containsEntry("error", "billing.notice.expired_with_date");
    }

    @Test
    void accessDeniedIsMappedToNotFoundForTenantSafety() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/products/55/delete");
        ResponseEntity<Map<String, Object>> response =
            handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("error", "Not found");
    }
}
