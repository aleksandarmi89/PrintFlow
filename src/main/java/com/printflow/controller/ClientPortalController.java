package com.printflow.controller;

import com.printflow.entity.Attachment;
import com.printflow.entity.ClientPortalAccess;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrder.DeliveryType;
import com.printflow.entity.WorkOrderActivity;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.service.ActivityLogService;
import com.printflow.service.ClientPortalService;
import com.printflow.service.FileStorageService;
import com.printflow.service.WorkOrderService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.AccessDeniedException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/portal")
public class ClientPortalController {

    private final ClientPortalService clientPortalService;
    private final WorkOrderService workOrderService;
    private final FileStorageService fileStorageService;
    private final ActivityLogService activityLogService;
    private final com.printflow.service.CompanyBrandingService companyBrandingService;

    public ClientPortalController(ClientPortalService clientPortalService,
                                  WorkOrderService workOrderService,
                                  FileStorageService fileStorageService,
                                  ActivityLogService activityLogService,
                                  com.printflow.service.CompanyBrandingService companyBrandingService) {
        this.clientPortalService = clientPortalService;
        this.workOrderService = workOrderService;
        this.fileStorageService = fileStorageService;
        this.activityLogService = activityLogService;
        this.companyBrandingService = companyBrandingService;
    }

    @GetMapping("/{token}")
    public String portal(@PathVariable String token, Model model) {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        var client = access.getClient();
        var company = access.getCompany();

        List<WorkOrder> orders = safeList(clientPortalService.getActiveOrdersForClient(client, company));
        List<WorkOrder> recentOrders = safeList(clientPortalService.getRecentOrdersForClient(client, company, 15));
        long pendingApprovalCount = orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.WAITING_CLIENT_APPROVAL)
            .count();
        long readyForPickupCount = orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.READY_FOR_DELIVERY)
            .filter(order -> order.getDeliveryType() == DeliveryType.PICKUP)
            .count();
        long inShipmentCount = orders.stream()
            .filter(order -> order.getDeliveryType() == DeliveryType.COURIER || order.getDeliveryType() == DeliveryType.EXPRESS_POST)
            .filter(order -> order.getStatus() == OrderStatus.SENT || order.getStatus() == OrderStatus.READY_FOR_DELIVERY)
            .count();
        long courierTrackingPendingCount = orders.stream()
            .filter(order -> order.getDeliveryType() == DeliveryType.COURIER || order.getDeliveryType() == DeliveryType.EXPRESS_POST)
            .filter(order -> order.getStatus() == OrderStatus.READY_FOR_DELIVERY || order.getStatus() == OrderStatus.SENT)
            .filter(order -> order.getTrackingNumber() == null || order.getTrackingNumber().isBlank())
            .count();
        List<WorkOrder> archivedOrders = recentOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED)
            .toList();
        List<Attachment> attachments = safeList(clientPortalService.getAttachmentsForClient(client, company));

        Map<Long, List<Attachment>> attachmentsByOrder = attachments.stream()
            .filter(att -> att.getWorkOrder() != null)
            .collect(Collectors.groupingBy(att -> att.getWorkOrder().getId()));
        Map<Long, List<WorkOrderActivity>> activityByOrder = recentOrders.stream()
            .collect(Collectors.toMap(
                WorkOrder::getId,
                order -> safeList(activityLogService.getForWorkOrder(order.getId(), company))
                    .stream()
                    .limit(3)
                    .toList(),
                (left, right) -> left
            ));
        Map<Long, WorkOrder> recentOrdersById = recentOrders.stream()
            .collect(Collectors.toMap(WorkOrder::getId, Function.identity(), (left, right) -> left));
        model.addAttribute("accessToken", token);
        model.addAttribute("client", client);
        model.addAttribute("orders", orders);
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("pendingApprovalCount", pendingApprovalCount);
        model.addAttribute("readyForPickupCount", readyForPickupCount);
        model.addAttribute("inShipmentCount", inShipmentCount);
        model.addAttribute("courierTrackingPendingCount", courierTrackingPendingCount);
        model.addAttribute("archivedOrders", archivedOrders);
        model.addAttribute("recentOrdersById", recentOrdersById);
        model.addAttribute("activityByOrder", activityByOrder);
        model.addAttribute("attachmentsByOrder", attachmentsByOrder);
        model.addAttribute("portalCurrency", company != null && company.getCurrency() != null && !company.getCurrency().isBlank()
            ? company.getCurrency().toUpperCase(Locale.ROOT)
            : "RSD");
        model.addAttribute("companyBrand", companyBrandingService.toBranding(company, token, "portal"));
        return "portal/portal";
    }

    @PostMapping("/{token}/attachments/{approvalToken}/approve")
    public String approveDesign(@PathVariable String token,
                                @PathVariable String approvalToken,
                                @RequestParam(name = "lang", required = false) String lang,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        var client = access.getClient();
        try {
            Attachment attachment = clientPortalService.getAttachmentForApproval(approvalToken, client.getId());
            clientPortalService.approveAttachment(attachment, client.getCompanyName(), request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("portalFlashSuccessKey", "portal.flash.design_approved");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("portalFlashErrorKey", "portal.flash.design_already_approved");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("portalFlashErrorKey", "portal.flash.design_error");
        }
        return "redirect:/portal/" + token + toLangQuery(lang);
    }

    @PostMapping("/{token}/attachments/{approvalToken}/reject")
    public String rejectDesign(@PathVariable String token,
                               @PathVariable String approvalToken,
                               @RequestParam(name = "lang", required = false) String lang,
                               @RequestParam(name = "reason", required = false) String reason,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        var client = access.getClient();
        try {
            Attachment attachment = clientPortalService.getAttachmentForApproval(approvalToken, client.getId());
            clientPortalService.rejectAttachmentForRevision(attachment, client.getCompanyName(), request.getRemoteAddr(), reason);
            if (attachment.getWorkOrder() != null) {
                String normalizedReason = reason == null ? "" : reason.trim();
                String activityMessage = normalizedReason.isBlank()
                    ? "Client requested design revision via portal."
                    : "Client requested design revision via portal: " + normalizedReason;
                activityLogService.log(attachment.getWorkOrder(), "DESIGN_REJECTED", activityMessage, null);
            }
            redirectAttributes.addFlashAttribute("portalFlashSuccessKey", "portal.flash.design_rejected");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("portalFlashErrorKey", "portal.flash.design_reject_error");
        }
        return "redirect:/portal/" + token + toLangQuery(lang);
    }

    @PostMapping("/{token}/reorder/{orderToken}")
    public String reorder(@PathVariable String token,
                          @PathVariable String orderToken,
                          @RequestParam(name = "lang", required = false) String lang,
                          RedirectAttributes redirectAttributes) {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        try {
            workOrderService.reorderWorkOrderForClientToken(orderToken, access.getClient());
            redirectAttributes.addFlashAttribute("portalFlashSuccessKey", "portal.flash.reorder_created");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("portalFlashErrorKey", "portal.flash.reorder_failed");
        }
        return "redirect:/portal/" + token + toLangQuery(lang);
    }

    @GetMapping("/{token}/attachments/{approvalToken}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String token,
                                                      @PathVariable String approvalToken) throws IOException {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        var client = access.getClient();
        Attachment attachment = clientPortalService.getAttachmentForApproval(approvalToken, client.getId());
        byte[] data = fileStorageService.getAttachmentFileByApprovalToken(approvalToken, client.getId());
        ByteArrayResource resource = new ByteArrayResource(data);
        String filename = attachment.getOriginalFileName() != null ? attachment.getOriginalFileName() : "file";
        String mime = attachment.getMimeType() != null ? attachment.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(mime))
            .body(resource);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handlePortalAccessDenied(AccessDeniedException ex, Model model) {
        String message = ex != null ? ex.getMessage() : null;
        String messageKey = "portal.access_denied.invalid";
        if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("expired")) {
            messageKey = "portal.access_denied.expired";
        }
        model.addAttribute("portalAccessDeniedMessageKey", messageKey);
        return "portal/access-denied";
    }

    private String toLangQuery(String lang) {
        if (lang == null || lang.isBlank()) {
            return "";
        }
        String normalized = "en".equalsIgnoreCase(lang) ? "en" : "sr";
        return "?lang=" + normalized;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value != null ? value : Collections.emptyList();
    }

}
