package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSource;
import com.printflow.pricing.dto.ProductImportMode;
import com.printflow.pricing.dto.ProductImportResult;
import com.printflow.pricing.dto.ProductListFilter;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.service.ProductExternalSyncFacade;
import com.printflow.pricing.service.ProductImportService;
import com.printflow.pricing.service.ProductManagementService;
import com.printflow.pricing.service.ProductSyncSettingsService;
import com.printflow.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductManagementControllerTest {

    @Test
    void listAcceptsLowercaseSourceAndTrimsSearch() {
        ProductManagementService productService = mock(ProductManagementService.class);
        ProductImportService importService = mock(ProductImportService.class);
        ProductExternalSyncFacade syncFacade = mock(ProductExternalSyncFacade.class);
        ProductSyncSettingsService syncSettingsService = mock(ProductSyncSettingsService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        ProductManagementController controller = new ProductManagementController(
            productService, importService, syncFacade, syncSettingsService, auditLogService
        );

        when(productService.findPage(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(productService.pricingReadiness(any())).thenReturn(Map.of());
        when(syncSettingsService.isSyncConfiguredForCurrentTenant()).thenReturn(false);
        when(syncSettingsService.currentStatusView()).thenReturn(null);

        Model model = new ExtendedModelMap();
        String view = controller.list("  abc  ", true, "api", 0, 20, " name ", " desc ", model);

        assertEquals("products/list", view);
        ArgumentCaptor<ProductListFilter> captor = ArgumentCaptor.forClass(ProductListFilter.class);
        verify(productService).findPage(captor.capture());
        ProductListFilter filter = captor.getValue();
        assertEquals("abc", filter.getQ());
        assertEquals(ProductSource.API, filter.getSource());
        assertEquals("name", filter.getSortBy());
        assertEquals("desc", filter.getSortDir());
    }

    @Test
    void listIgnoresInvalidSource() {
        ProductManagementService productService = mock(ProductManagementService.class);
        ProductImportService importService = mock(ProductImportService.class);
        ProductExternalSyncFacade syncFacade = mock(ProductExternalSyncFacade.class);
        ProductSyncSettingsService syncSettingsService = mock(ProductSyncSettingsService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        ProductManagementController controller = new ProductManagementController(
            productService, importService, syncFacade, syncSettingsService, auditLogService
        );

        when(productService.findPage(any())).thenReturn(new PageImpl<>(List.of(new Product()), PageRequest.of(0, 20), 1));
        when(productService.pricingReadiness(any())).thenReturn(Map.of());
        when(syncSettingsService.isSyncConfiguredForCurrentTenant()).thenReturn(true);
        when(syncSettingsService.currentStatusView()).thenReturn(null);

        Model model = new ExtendedModelMap();
        String view = controller.list(null, null, "bad-source", 0, 10, "name", "asc", model);

        assertEquals("products/list", view);
        ArgumentCaptor<ProductListFilter> captor = ArgumentCaptor.forClass(ProductListFilter.class);
        verify(productService).findPage(captor.capture());
        assertNull(captor.getValue().getSource());
    }

    @Test
    void importProductsAcceptsLowercaseMode() {
        ProductManagementService productService = mock(ProductManagementService.class);
        ProductImportService importService = mock(ProductImportService.class);
        ProductExternalSyncFacade syncFacade = mock(ProductExternalSyncFacade.class);
        ProductSyncSettingsService syncSettingsService = mock(ProductSyncSettingsService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        ProductManagementController controller = new ProductManagementController(
            productService, importService, syncFacade, syncSettingsService, auditLogService
        );

        Company company = new Company();
        when(productService.currentCompany()).thenReturn(company);
        ProductImportResult result = new ProductImportResult();
        when(importService.importFile(any(), eq(company), eq(ProductImportMode.UPSERT_BY_SKU))).thenReturn(result);

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        MockMultipartFile file = new MockMultipartFile("file", "p.csv", "text/csv", "name,basePrice".getBytes());

        String view = controller.importProducts(file, "upsert_by_sku", redirectAttributes);

        assertEquals("redirect:/products/import", view);
        verify(importService).importFile(any(), eq(company), eq(ProductImportMode.UPSERT_BY_SKU));
    }

    @Test
    void importProductsRejectsInvalidModeWithoutCallingImportService() {
        ProductManagementService productService = mock(ProductManagementService.class);
        ProductImportService importService = mock(ProductImportService.class);
        ProductExternalSyncFacade syncFacade = mock(ProductExternalSyncFacade.class);
        ProductSyncSettingsService syncSettingsService = mock(ProductSyncSettingsService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        ProductManagementController controller = new ProductManagementController(
            productService, importService, syncFacade, syncSettingsService, auditLogService
        );

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        MockMultipartFile file = new MockMultipartFile("file", "p.csv", "text/csv", "name,basePrice".getBytes());

        String view = controller.importProducts(file, "bad-mode", redirectAttributes);

        assertEquals("redirect:/products/import", view);
        verify(productService, never()).currentCompany();
        verify(importService, never()).importFile(any(), any(), any());
        Object error = redirectAttributes.getFlashAttributes().get("errorMessage");
        assertTrue(error != null && error.toString().length() > 0);
    }
}
