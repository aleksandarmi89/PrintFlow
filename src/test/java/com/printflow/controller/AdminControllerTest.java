package com.printflow.controller;

import com.printflow.config.PaginationConfig;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.PublicOrderRequestRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.service.ActivityLogService;
import com.printflow.service.AuditLogService;
import com.printflow.service.ClientPortalService;
import com.printflow.service.ClientPricingProfileService;
import com.printflow.service.ClientService;
import com.printflow.service.CompanyBrandingService;
import com.printflow.service.CompanyService;
import com.printflow.service.DashboardService;
import com.printflow.service.EmailService;
import com.printflow.service.EmailTemplateService;
import com.printflow.service.ExcelImportService;
import com.printflow.service.FileStorageService;
import com.printflow.service.NotificationService;
import com.printflow.service.OrderPdfService;
import com.printflow.service.TaskService;
import com.printflow.service.TenantContextService;
import com.printflow.service.UserService;
import com.printflow.service.WorkOrderProfitService;
import com.printflow.service.WorkOrderService;
import com.printflow.shipping.ShipmentFacade;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class AdminControllerTest {

    @Test
    void parseNullableIdReturnsNullForInvalidAndTrimmedValueForValid() throws Exception {
        AdminController controller = new AdminController(
            mock(DashboardService.class),
            mock(ClientService.class),
            mock(WorkOrderService.class),
            mock(UserService.class),
            mock(ExcelImportService.class),
            mock(FileStorageService.class),
            mock(CompanyService.class),
            mock(TenantContextService.class),
            mock(TaskService.class),
            mock(AuditLogService.class),
            mock(NotificationService.class),
            mock(PaginationConfig.class),
            mock(WorkOrderItemRepository.class),
            mock(WorkOrderProfitService.class),
            mock(ClientPortalService.class),
            mock(ActivityLogService.class),
            mock(ProductVariantRepository.class),
            mock(ClientPricingProfileService.class),
            mock(EmailTemplateService.class),
            mock(EmailService.class),
            mock(CompanyBrandingService.class),
            mock(PublicOrderRequestRepository.class),
            mock(OrderPdfService.class),
            mock(ShipmentFacade.class),
            "http://localhost:8088"
        );

        Method parseNullableId = AdminController.class.getDeclaredMethod("parseNullableId", String.class);
        parseNullableId.setAccessible(true);
        Method resolveOrdersListRedirect = AdminController.class.getDeclaredMethod("resolveOrdersListRedirect", String.class);
        resolveOrdersListRedirect.setAccessible(true);
        Method resolveRequestedLocale = AdminController.class.getDeclaredMethod(
            "resolveRequestedLocale",
            String.class,
            java.util.Locale.class
        );
        resolveRequestedLocale.setAccessible(true);

        assertNull(parseNullableId.invoke(controller, (Object) null));
        assertNull(parseNullableId.invoke(controller, " "));
        assertNull(parseNullableId.invoke(controller, "not-a-number"));
        assertEquals(42L, parseNullableId.invoke(controller, " 42 "));

        assertEquals("/admin/orders", resolveOrdersListRedirect.invoke(controller, (Object) null));
        assertEquals("/admin/orders", resolveOrdersListRedirect.invoke(controller, ""));
        assertEquals("/admin/orders", resolveOrdersListRedirect.invoke(controller, "notaurl"));
        assertEquals("/admin/orders", resolveOrdersListRedirect.invoke(controller, "http://localhost:8088/admin/dashboard"));
        assertEquals("/admin/orders", resolveOrdersListRedirect.invoke(controller, "http://localhost:8088/admin/orders"));
        assertEquals("/admin/orders?status=IN_PRINT&overdueOnly=true", resolveOrdersListRedirect.invoke(
            controller, "/admin/orders?status=IN_PRINT&overdueOnly=true"));
        assertEquals("/admin/orders", resolveOrdersListRedirect.invoke(
            controller, "/admin/clients?page=2"));
        assertEquals("/admin/orders?status=NEW&page=2", resolveOrdersListRedirect.invoke(
            controller, "http://localhost:8088/admin/orders?status=NEW&page=2"));

        assertEquals("sr", ((java.util.Locale) resolveRequestedLocale.invoke(controller, "sr", java.util.Locale.ENGLISH)).getLanguage());
        assertEquals("en", ((java.util.Locale) resolveRequestedLocale.invoke(controller, "en", new java.util.Locale("sr"))).getLanguage());
        assertEquals("en", ((java.util.Locale) resolveRequestedLocale.invoke(controller, "xx", java.util.Locale.ENGLISH)).getLanguage());
        assertEquals("en", ((java.util.Locale) resolveRequestedLocale.invoke(controller, null, null)).getLanguage());
    }
}
