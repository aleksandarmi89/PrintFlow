package com.printflow.controller;

import com.printflow.config.PaginationConfig;
import com.printflow.dto.NotificationDTO;
import com.printflow.entity.User;
import com.printflow.service.NotificationService;
import com.printflow.service.TenantContextService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    @Test
    void listNotificationsTrimsTypeAndUsesReturnedPageNumber() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(7L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        when(paginationConfig.normalizePage(4)).thenReturn(4);
        when(paginationConfig.normalizeSize(30)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(notificationService.getNotificationsWithFilters(eq(7L), eq("TASK"), eq(Boolean.FALSE), any()))
            .thenReturn(new PageImpl<>(List.of(new NotificationDTO()), PageRequest.of(0, 20), 1));

        Model model = new ExtendedModelMap();
        String view = controller.listNotifications("  TASK  ", false, 4, 30, model);

        assertEquals("notifications/list", view);
        assertEquals("TASK", model.getAttribute("type"));
        assertEquals(0, model.getAttribute("currentPage"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotificationsWithFilters(eq(7L), eq("TASK"), eq(Boolean.FALSE), pageableCaptor.capture());
        assertEquals(4, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void listNotificationsConvertsBlankTypeToNull() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(8L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(null)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(notificationService.getNotificationsWithFilters(eq(8L), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listNotifications("   ", null, 0, null, model);

        assertEquals("notifications/list", view);
        assertEquals(null, model.getAttribute("type"));
        verify(notificationService).getNotificationsWithFilters(eq(8L), isNull(), isNull(), any());
    }

    @Test
    void deleteSelectedNotificationsUsesI18nFlashKeys() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(9L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String emptyView = controller.deleteSelectedNotifications(List.of(), redirect);
        assertEquals("redirect:/notifications", emptyView);
        assertEquals("notifications.flash.select_one", redirect.getFlashAttributes().get("errorMessage"));
        verifyNoInteractions(notificationService);

        redirect = new RedirectAttributesModelMap();
        String successView = controller.deleteSelectedNotifications(List.of(1L, 2L), redirect);
        assertEquals("redirect:/notifications", successView);
        assertEquals("notifications.flash.deleted_selected", redirect.getFlashAttributes().get("successMessage"));
        verify(notificationService).deleteMultipleNotifications(List.of(1L, 2L), 9L);
    }
}
