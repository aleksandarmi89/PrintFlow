package com.printflow.controller;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.service.WorkOrderService;
import com.printflow.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller  // DODAJ OVO!
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController extends BaseController {
    
    private final WorkOrderService workOrderService;
    private final FileStorageService fileStorageService;
    
    
    
    public PublicController(WorkOrderService workOrderService, FileStorageService fileStorageService) {
		super();
		this.workOrderService = workOrderService;
		this.fileStorageService = fileStorageService;
	}

	// Glavna stranica za praćenje naloga
    @GetMapping("/order/{token}")
    public String trackOrder(@PathVariable String token, Model model) {
        try {
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(token);
            List<com.printflow.dto.AttachmentDTO> attachments = fileStorageService.getAttachmentsByWorkOrder(order.getId());
            
            // Filtriraj samo preview fajlove za klijenta
            List<com.printflow.dto.AttachmentDTO> previewAttachments = attachments.stream()
                .filter(a -> a.getAttachmentType() == AttachmentType.DESIGN_PREVIEW)
                .collect(Collectors.toList());
            
            model.addAttribute("order", order);
            model.addAttribute("previewAttachments", previewAttachments);
            model.addAttribute("token", token);
            
            return "public/order-tracking";
        } catch (Exception e) {
            model.addAttribute("error", "Order not found or invalid tracking code");
            return "public/order-not-found";
        }
    }
    
    // Odobrenje dizajna od strane klijenta
    @PostMapping("/order/{token}/approve-design")
    public String approveDesign(@PathVariable String token,
                               @RequestParam boolean approved,
                               @RequestParam(required = false) String comment,
                               Model model) {
        try {
            WorkOrderDTO order = workOrderService.getWorkOrderByPublicToken(token);
            
            // Procesuiraj odobrenje
            workOrderService.approveDesign(order.getId(), approved, comment);
            
            if (approved) {
                model.addAttribute("message", "Thank you for approving the design. Production will start soon.");
            } else {
                model.addAttribute("message", "Thank you for your feedback. The design will be revised.");
            }
            
            return "public/design-feedback";
        } catch (Exception e) {
            model.addAttribute("error", "Error processing your request: " + e.getMessage());
            return "public/order-not-found";
        }
    }
    
    // Stranica za unos tracking koda
    @GetMapping("/track")
    public String trackOrderForm() {
        return "public/track-order";
    }
    
    @PostMapping("/track")
    public String trackOrderSubmit(@RequestParam String trackingCode, Model model) {
        if (trackingCode == null || trackingCode.trim().isEmpty()) {
            model.addAttribute("error", "Please enter a tracking code");
            return "public/track-order";
        }
        
        return "redirect:/public/order/" + trackingCode.trim();
    }
    
    // Landing page za PrintFlow
    @GetMapping("/")
    public String landingPage(Model model) {
        model.addAttribute("pageTitle", "PrintFlow - Order Tracking System");
        return "public/landing";
    }
    
    // Contact page
    @GetMapping("/contact")
    public String contactPage(Model model) {
        return "public/contact";
    }
}