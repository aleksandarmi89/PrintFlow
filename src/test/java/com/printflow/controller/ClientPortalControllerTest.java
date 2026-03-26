package com.printflow.controller;

import com.printflow.entity.Attachment;
import com.printflow.entity.Client;
import com.printflow.entity.ClientPortalAccess;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.service.ActivityLogService;
import com.printflow.service.ClientPortalService;
import com.printflow.service.CompanyBrandingService;
import com.printflow.service.FileStorageService;
import com.printflow.service.WorkOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class ClientPortalControllerTest {

    @Mock
    private ClientPortalService clientPortalService;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private CompanyBrandingService companyBrandingService;
    @Mock
    private HttpServletRequest request;

    private ClientPortalController controller;
    private Client client;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new ClientPortalController(
            clientPortalService,
            workOrderService,
            fileStorageService,
            activityLogService,
            companyBrandingService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        client = new Client();
        client.setId(11L);
        client.setCompanyName("Client Co");
    }

    @Test
    void approveDesignSuccessAddsSuccessFlash() {
        String token = "portal-token";
        String approvalToken = "approve-token";
        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);
        Attachment attachment = new Attachment();

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        when(clientPortalService.getAttachmentForApproval(approvalToken, client.getId())).thenReturn(attachment);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.approveDesign(token, approvalToken, null, request, flash);

        assertEquals("redirect:/portal/" + token, view);
        assertEquals("portal.flash.design_approved", flash.getFlashAttributes().get("portalFlashSuccessKey"));
        assertNull(flash.getFlashAttributes().get("portalFlashErrorKey"));
        verify(clientPortalService).approveAttachment(attachment, "Client Co", "127.0.0.1");
    }

    @Test
    void approveDesignAlreadyApprovedAddsFriendlyErrorFlash() {
        String token = "portal-token";
        String approvalToken = "approve-token";
        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);
        Attachment attachment = new Attachment();

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        when(clientPortalService.getAttachmentForApproval(approvalToken, client.getId())).thenReturn(attachment);
        doThrow(new IllegalStateException("already approved")).when(clientPortalService)
            .approveAttachment(any(), any(), any());

        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.approveDesign(token, approvalToken, null, request, flash);

        assertEquals("redirect:/portal/" + token, view);
        assertEquals("portal.flash.design_already_approved", flash.getFlashAttributes().get("portalFlashErrorKey"));
    }

    @Test
    void reorderFailureAddsErrorFlash() {
        String token = "portal-token";
        String orderToken = "order-token";
        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        doThrow(new RuntimeException("not found")).when(workOrderService)
            .reorderWorkOrderForClientToken(orderToken, client);

        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.reorder(token, orderToken, null, flash);

        assertEquals("redirect:/portal/" + token, view);
        assertEquals("portal.flash.reorder_failed", flash.getFlashAttributes().get("portalFlashErrorKey"));
    }

    @Test
    void approveDesignPreservesEnglishLangOnRedirect() {
        String token = "portal-token";
        String approvalToken = "approve-token";
        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);
        Attachment attachment = new Attachment();

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        when(clientPortalService.getAttachmentForApproval(approvalToken, client.getId())).thenReturn(attachment);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.approveDesign(token, approvalToken, "en", request, flash);

        assertEquals("redirect:/portal/" + token + "?lang=en", view);
    }

    @Test
    void reorderNormalizesUnsupportedLangToSrOnRedirect() {
        String token = "portal-token";
        String orderToken = "order-token";
        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        doThrow(new RuntimeException("not found")).when(workOrderService)
            .reorderWorkOrderForClientToken(orderToken, client);

        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.reorder(token, orderToken, "de", flash);

        assertEquals("redirect:/portal/" + token + "?lang=sr", view);
    }

    @Test
    void rejectDesignAddsSuccessFlashAndPreservesLang() {
        String token = "portal-token";
        String approvalToken = "approve-token";
        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);
        Attachment attachment = new Attachment();
        WorkOrder workOrder = new WorkOrder();
        attachment.setWorkOrder(workOrder);

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        when(clientPortalService.getAttachmentForApproval(approvalToken, client.getId())).thenReturn(attachment);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.rejectDesign(token, approvalToken, "en", "Please adjust margins", request, flash);

        assertEquals("redirect:/portal/" + token + "?lang=en", view);
        assertEquals("portal.flash.design_rejected", flash.getFlashAttributes().get("portalFlashSuccessKey"));
        verify(clientPortalService).rejectAttachmentForRevision(attachment, "Client Co", "127.0.0.1", "Please adjust margins");
    }

    @Test
    void portalInvalidTokenRendersAccessDeniedPage() throws Exception {
        when(clientPortalService.getAccessOrThrow("bad-token"))
            .thenThrow(new AccessDeniedException("Invalid portal token"));

        mockMvc.perform(get("/portal/bad-token"))
            .andExpect(status().isForbidden())
            .andExpect(view().name("portal/access-denied"))
            .andExpect(model().attribute("portalAccessDeniedMessageKey", "portal.access_denied.invalid"));
    }

    @Test
    void portalExpiredTokenRendersExpiredMessage() throws Exception {
        when(clientPortalService.getAccessOrThrow("expired-token"))
            .thenThrow(new AccessDeniedException("Portal token expired"));

        mockMvc.perform(get("/portal/expired-token"))
            .andExpect(status().isForbidden())
            .andExpect(view().name("portal/access-denied"))
            .andExpect(model().attribute("portalAccessDeniedMessageKey", "portal.access_denied.expired"));
    }

    @Test
    void portalPageBuildsKpiModelAndRendersPortalView() throws Exception {
        String token = "portal-token";
        Company company = new Company();
        company.setId(7L);
        company.setName("Print Co");

        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);
        access.setCompany(company);

        WorkOrder pendingApproval = new WorkOrder();
        pendingApproval.setId(101L);
        pendingApproval.setStatus(OrderStatus.WAITING_CLIENT_APPROVAL);
        pendingApproval.setDeliveryType(WorkOrder.DeliveryType.COURIER);
        pendingApproval.setTrackingNumber("");
        pendingApproval.setPublicToken("tok-a");

        WorkOrder pickupReady = new WorkOrder();
        pickupReady.setId(102L);
        pickupReady.setStatus(OrderStatus.READY_FOR_DELIVERY);
        pickupReady.setDeliveryType(WorkOrder.DeliveryType.PICKUP);
        pickupReady.setPublicToken("tok-b");

        WorkOrder inShipment = new WorkOrder();
        inShipment.setId(103L);
        inShipment.setStatus(OrderStatus.SENT);
        inShipment.setDeliveryType(WorkOrder.DeliveryType.COURIER);
        inShipment.setTrackingNumber("TRK-1");
        inShipment.setPublicToken("tok-c");

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        when(clientPortalService.getActiveOrdersForClient(client, company))
            .thenReturn(java.util.List.of(pendingApproval, pickupReady, inShipment));
        when(clientPortalService.getRecentOrdersForClient(client, company, 15))
            .thenReturn(java.util.List.of(pendingApproval, pickupReady, inShipment));
        when(clientPortalService.getAttachmentsForClient(client, company))
            .thenReturn(java.util.List.of());
        when(activityLogService.getForWorkOrder(eq(101L), eq(company))).thenReturn(java.util.List.of());
        when(activityLogService.getForWorkOrder(eq(102L), eq(company))).thenReturn(java.util.List.of());
        when(activityLogService.getForWorkOrder(eq(103L), eq(company))).thenReturn(java.util.List.of());

        mockMvc.perform(get("/portal/{token}", token))
            .andExpect(status().isOk())
            .andExpect(view().name("portal/portal"))
            .andExpect(model().attribute("pendingApprovalCount", 1L))
            .andExpect(model().attribute("readyForPickupCount", 1L))
            .andExpect(model().attribute("inShipmentCount", 1L))
            .andExpect(model().attribute("courierTrackingPendingCount", 0L));
    }

    @Test
    void portalPageHandlesNullListsFromService() throws Exception {
        String token = "portal-token";
        Company company = new Company();
        company.setId(8L);
        company.setName("Print Co");

        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);
        access.setCompany(company);

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        when(clientPortalService.getActiveOrdersForClient(client, company)).thenReturn(null);
        when(clientPortalService.getRecentOrdersForClient(client, company, 15)).thenReturn(null);
        when(clientPortalService.getAttachmentsForClient(client, company)).thenReturn(null);

        mockMvc.perform(get("/portal/{token}", token))
            .andExpect(status().isOk())
            .andExpect(view().name("portal/portal"))
            .andExpect(model().attribute("pendingApprovalCount", 0L))
            .andExpect(model().attribute("readyForPickupCount", 0L))
            .andExpect(model().attribute("inShipmentCount", 0L))
            .andExpect(model().attribute("courierTrackingPendingCount", 0L));
    }

    @Test
    void portalPageHandlesNullActivityLists() throws Exception {
        String token = "portal-token";
        Company company = new Company();
        company.setId(9L);
        company.setName("Print Co");

        ClientPortalAccess access = new ClientPortalAccess();
        access.setClient(client);
        access.setCompany(company);

        WorkOrder order = new WorkOrder();
        order.setId(111L);
        order.setStatus(OrderStatus.NEW);
        order.setDeliveryType(WorkOrder.DeliveryType.PICKUP);

        when(clientPortalService.getAccessOrThrow(token)).thenReturn(access);
        when(clientPortalService.getActiveOrdersForClient(client, company)).thenReturn(java.util.List.of(order));
        when(clientPortalService.getRecentOrdersForClient(client, company, 15)).thenReturn(java.util.List.of(order));
        when(clientPortalService.getAttachmentsForClient(client, company)).thenReturn(java.util.List.of());
        when(activityLogService.getForWorkOrder(eq(111L), eq(company))).thenReturn(null);

        mockMvc.perform(get("/portal/{token}", token))
            .andExpect(status().isOk())
            .andExpect(view().name("portal/portal"))
            .andExpect(model().attribute("pendingApprovalCount", 0L))
            .andExpect(model().attribute("readyForPickupCount", 0L))
            .andExpect(model().attribute("inShipmentCount", 0L))
            .andExpect(model().attribute("courierTrackingPendingCount", 0L));
    }
}
