package com.printflow.controller;

import com.printflow.dto.PublicOrderRequestForm;
import com.printflow.entity.Company;
import com.printflow.service.PublicOrderRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class PublicOrderRequestController extends BaseController {

    private final PublicOrderRequestService requestService;

    public PublicOrderRequestController(PublicOrderRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/p/{companySlug}/order")
    public String form(@PathVariable String companySlug, Model model) {
        Company company = requestService.requireActiveCompanyBySlug(companySlug);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PublicOrderRequestForm());
        }
        model.addAttribute("company", company);
        model.addAttribute("companySlug", companySlug);
        return "public/order-request-form";
    }

    @PostMapping("/p/{companySlug}/order")
    public String submit(@PathVariable String companySlug,
                         @Valid @ModelAttribute("form") PublicOrderRequestForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         HttpServletRequest request,
                         Model model) {
        Company company = requestService.requireActiveCompanyBySlug(companySlug);
        if (bindingResult.hasErrors()) {
            model.addAttribute("company", company);
            model.addAttribute("companySlug", companySlug);
            return "public/order-request-form";
        }
        try {
            requestService.createPublicRequest(companySlug, form, files, request.getRemoteAddr());
            return "redirect:/p/" + companySlug + "/order/success";
        } catch (Exception ex) {
            model.addAttribute("company", company);
            model.addAttribute("companySlug", companySlug);
            model.addAttribute("errorMessage", ex.getMessage());
            return "public/order-request-form";
        }
    }

    @GetMapping("/p/{companySlug}/order/success")
    public String success(@PathVariable String companySlug, Model model) {
        Company company = requestService.requireActiveCompanyBySlug(companySlug);
        model.addAttribute("company", company);
        model.addAttribute("companySlug", companySlug);
        return "public/order-request-success";
    }

    @GetMapping("/p/company/{companyId}/order")
    public String formByCompanyId(@PathVariable Long companyId) {
        Company company = requestService.requireActiveCompanyById(companyId);
        String slug = requestService.ensureCompanySlug(company);
        return "redirect:/p/" + slug + "/order";
    }
}
