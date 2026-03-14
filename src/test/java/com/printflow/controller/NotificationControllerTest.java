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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationControllerTest {

    @Test
    void markNotificationAsReadReturnsGenericErrorPayload() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(11L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        doThrow(new RuntimeException("db failure")).when(notificationService).markAsRead(55L, 11L);

        ResponseEntity<java.util.Map<String, Object>> response = controller.markNotificationAsRead(55L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("error", response.getBody().get("status"));
        assertEquals("notifications.error", response.getBody().get("message"));
    }

    @Test
    void markAllAsReadReturnsSuccessPayload() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(12L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);

        ResponseEntity<java.util.Map<String, Object>> response = controller.markAllNotificationsAsRead();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", response.getBody().get("status"));
        verify(notificationService).markAllAsRead(12L);
    }

    @Test
    void markReadReturnsForbiddenWhenCurrentUserMissing() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);
        when(tenantContextService.getCurrentUser()).thenReturn(null);

        ResponseEntity<java.util.Map<String, Object>> response = controller.markNotificationAsRead(77L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("error", response.getBody().get("status"));
        assertEquals("notifications.error", response.getBody().get("message"));
        verifyNoInteractions(notificationService);
    }

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
        assertEquals(1, model.getAttribute("displayTotalPages"));
        assertEquals(0, model.getAttribute("lastPage"));
        verify(notificationService).getNotificationsWithFilters(eq(8L), isNull(), isNull(), any());
    }

    @Test
    void listNotificationsSetsLastPageForMultiPageResults() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(10L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        when(paginationConfig.normalizePage(2)).thenReturn(2);
        when(paginationConfig.normalizeSize(20)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(notificationService.getNotificationsWithFilters(eq(10L), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 20), 85));

        Model model = new ExtendedModelMap();
        String view = controller.listNotifications(null, null, 2, 20, model);

        assertEquals("notifications/list", view);
        assertEquals(5, model.getAttribute("totalPages"));
        assertEquals(5, model.getAttribute("displayTotalPages"));
        assertEquals(4, model.getAttribute("lastPage"));
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

    @Test
    void listNotificationsThrowsForbiddenWhenCurrentUserMissing() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);
        when(tenantContextService.getCurrentUser()).thenReturn(null);

        Model model = new ExtendedModelMap();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.listNotifications(null, null, 0, null, model));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
