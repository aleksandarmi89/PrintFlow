package com.printflow.controller;

import com.printflow.entity.Attachment;
import com.printflow.entity.ClientPortalAccess;
import com.printflow.entity.WorkOrder;
import com.printflow.service.ClientPortalService;
import com.printflow.service.FileStorageService;
import com.printflow.service.WorkOrderService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/portal")
public class ClientPortalController {

    private final ClientPortalService clientPortalService;
    private final WorkOrderService workOrderService;
    private final FileStorageService fileStorageService;
    private final com.printflow.service.CompanyBrandingService companyBrandingService;

    public ClientPortalController(ClientPortalService clientPortalService,
                                  WorkOrderService workOrderService,
                                  FileStorageService fileStorageService,
                                  com.printflow.service.CompanyBrandingService companyBrandingService) {
        this.clientPortalService = clientPortalService;
        this.workOrderService = workOrderService;
        this.fileStorageService = fileStorageService;
        this.companyBrandingService = companyBrandingService;
    }

    @GetMapping("/{token}")
    public String portal(@PathVariable String token, Model model) {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        var client = access.getClient();
        var company = access.getCompany();

        List<WorkOrder> orders = clientPortalService.getActiveOrdersForClient(client, company);
        List<Attachment> attachments = clientPortalService.getAttachmentsForClient(client, company);

        Map<Long, List<Attachment>> attachmentsByOrder = attachments.stream()
            .filter(att -> att.getWorkOrder() != null)
            .collect(Collectors.groupingBy(att -> att.getWorkOrder().getId()));

        model.addAttribute("accessToken", token);
        model.addAttribute("client", client);
        model.addAttribute("orders", orders);
        model.addAttribute("attachmentsByOrder", attachmentsByOrder);
        model.addAttribute("companyBrand", companyBrandingService.toBranding(company, token, "portal"));
        return "portal/portal";
    }

    @PostMapping("/{token}/attachments/{approvalToken}/approve")
    public String approveDesign(@PathVariable String token,
                                @PathVariable String approvalToken,
                                HttpServletRequest request) {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        var client = access.getClient();
        Attachment attachment = clientPortalService.getAttachmentForApproval(approvalToken, client.getId());
        clientPortalService.approveAttachment(attachment, client.getCompanyName(), request.getRemoteAddr());
        return "redirect:/portal/" + token;
    }

    @PostMapping("/{token}/reorder/{orderToken}")
    public String reorder(@PathVariable String token, @PathVariable String orderToken) {
        ClientPortalAccess access = clientPortalService.getAccessOrThrow(token);
        workOrderService.reorderWorkOrderForClientToken(orderToken, access.getClient());
        return "redirect:/portal/" + token;
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
}
