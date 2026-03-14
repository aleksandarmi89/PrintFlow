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
import static org.mockito.Mockito.times;
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
        verify(notificationService, times(2))
            .getNotificationsWithFilters(eq(7L), eq("TASK"), eq(Boolean.FALSE), pageableCaptor.capture());
        List<Pageable> capturedPageables = pageableCaptor.getAllValues();
        assertEquals(4, capturedPageables.get(0).getPageNumber());
        assertEquals(0, capturedPageables.get(1).getPageNumber());
        assertEquals(20, capturedPageables.get(0).getPageSize());
    }

    @Test
    void listNotificationsNormalizesLowercaseTypeToUppercase() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(71L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(20)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(notificationService.getNotificationsWithFilters(eq(71L), eq("TASK_ASSIGNED"), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listNotifications(" task_assigned ", null, 0, 20, model);

        assertEquals("notifications/list", view);
        assertEquals("TASK_ASSIGNED", model.getAttribute("type"));
        verify(notificationService).getNotificationsWithFilters(eq(71L), eq("TASK_ASSIGNED"), isNull(), any());
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
    void listNotificationsDropsInvalidTypeCharactersFromFilter() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(72L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        when(paginationConfig.normalizePage(0)).thenReturn(0);
        when(paginationConfig.normalizeSize(20)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        when(notificationService.getNotificationsWithFilters(eq(72L), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Model model = new ExtendedModelMap();
        String view = controller.listNotifications("TASK<script>", null, 0, 20, model);

        assertEquals("notifications/list", view);
        assertEquals(null, model.getAttribute("type"));
        verify(notificationService).getNotificationsWithFilters(eq(72L), isNull(), isNull(), any());
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
    void listNotificationsRefetchesLastPageWhenRequestedPageIsOutOfRange() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(15L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        when(paginationConfig.normalizePage(9)).thenReturn(9);
        when(paginationConfig.normalizeSize(20)).thenReturn(20);
        when(paginationConfig.getAllowedSizes()).thenReturn(List.of(10, 20, 50));
        java.util.concurrent.atomic.AtomicInteger invocationCount = new java.util.concurrent.atomic.AtomicInteger();
        when(notificationService.getNotificationsWithFilters(eq(15L), eq("TASK"), eq(Boolean.TRUE), any()))
            .thenAnswer(invocation -> invocationCount.getAndIncrement() == 0
                ? new PageImpl<>(List.of(), PageRequest.of(9, 20), 45)
                : new PageImpl<>(List.of(), PageRequest.of(2, 20), 45));

        Model model = new ExtendedModelMap();
        String view = controller.listNotifications(" TASK ", true, 9, 20, model);

        assertEquals("notifications/list", view);
        assertEquals("TASK", model.getAttribute("type"));
        assertEquals(2, model.getAttribute("currentPage"));
        assertEquals(2, model.getAttribute("lastPage"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService, times(2))
            .getNotificationsWithFilters(eq(15L), eq("TASK"), eq(Boolean.TRUE), pageableCaptor.capture());
        List<Pageable> capturedPageables = pageableCaptor.getAllValues();
        assertEquals(9, capturedPageables.get(0).getPageNumber());
        assertEquals(2, capturedPageables.get(1).getPageNumber());
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
    void deleteSelectedNotificationsSanitizesNullAndDuplicateIds() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(13L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.deleteSelectedNotifications(java.util.Arrays.asList(1L, null, 1L, 2L), redirect);

        assertEquals("redirect:/notifications", view);
        assertEquals("notifications.flash.deleted_selected", redirect.getFlashAttributes().get("successMessage"));
        verify(notificationService).deleteMultipleNotifications(List.of(1L, 2L), 13L);
    }

    @Test
    void deleteSelectedNotificationsRejectsNullOnlyIdList() {
        NotificationService notificationService = mock(NotificationService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        PaginationConfig paginationConfig = mock(PaginationConfig.class);
        NotificationController controller = new NotificationController(notificationService, tenantContextService, paginationConfig);

        User user = new User();
        user.setId(14L);
        when(tenantContextService.getCurrentUser()).thenReturn(user);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.deleteSelectedNotifications(java.util.Collections.singletonList((Long) null), redirect);

        assertEquals("redirect:/notifications", view);
        assertEquals("notifications.flash.select_one", redirect.getFlashAttributes().get("errorMessage"));
        verifyNoInteractions(notificationService);
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
