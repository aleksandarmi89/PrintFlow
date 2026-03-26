package com.printflow.controller;

import com.printflow.dto.PublicOrderRequestForm;
import com.printflow.entity.Company;
import com.printflow.service.PublicOrderRequestException;
import com.printflow.service.PublicOrderRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class PublicOrderRequestController extends BaseController {

    private final PublicOrderRequestService requestService;
    private final MessageSource messageSource;

    public PublicOrderRequestController(PublicOrderRequestService requestService, MessageSource messageSource) {
        this.requestService = requestService;
        this.messageSource = messageSource;
    }

    @GetMapping("/p/{companySlug}/order")
    public String form(@PathVariable String companySlug, Model model) {
        Company company = requestService.requireActiveCompanyBySlug(companySlug);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PublicOrderRequestForm());
        }
        addUploadConstraints(model);
        model.addAttribute("company", company);
        model.addAttribute("companySlug", companySlug);
        return "public/order-request-form";
    }

    @PostMapping("/p/{companySlug}/order")
    public String submit(@PathVariable String companySlug,
                         @Valid @ModelAttribute("form") PublicOrderRequestForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "lang", required = false) String lang,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         HttpServletRequest request,
                         Model model) {
        Company company = requestService.requireActiveCompanyBySlug(companySlug);
        validateBusinessRules(form, bindingResult);
        if (bindingResult.hasErrors()) {
            addUploadConstraints(model);
            model.addAttribute("company", company);
            model.addAttribute("companySlug", companySlug);
            return "public/order-request-form";
        }
        try {
            requestService.createPublicRequest(companySlug, form, files, request.getRemoteAddr());
            String resolvedLang = resolveLang(lang);
            if (resolvedLang != null) {
                return "redirect:/p/" + companySlug + "/order/success?lang=" + resolvedLang;
            }
            return "redirect:/p/" + companySlug + "/order/success";
        } catch (PublicOrderRequestException ex) {
            addUploadConstraints(model);
            model.addAttribute("company", company);
            model.addAttribute("companySlug", companySlug);
            if ("public.order.validation.deadline.future".equals(ex.getMessageKey())) {
                bindingResult.rejectValue("deadline", ex.getMessageKey());
                return "public/order-request-form";
            }
            String resolvedMessage = messageSource.getMessage(ex.getMessageKey(), ex.getMessageArgs(), LocaleContextHolder.getLocale());
            if (isUploadRelatedError(ex.getMessageKey())) {
                model.addAttribute("fileErrorMessage", resolvedMessage);
            } else {
                model.addAttribute("errorMessage", resolvedMessage);
            }
            return "public/order-request-form";
        } catch (RuntimeException ex) {
            addUploadConstraints(model);
            model.addAttribute("company", company);
            model.addAttribute("companySlug", companySlug);
            model.addAttribute("errorMessage",
                messageSource.getMessage("public.order.error.generic", null, LocaleContextHolder.getLocale()));
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
    public String formByCompanyId(@PathVariable Long companyId,
                                  @RequestParam(value = "lang", required = false) String lang) {
        Company company = requestService.requireActiveCompanyById(companyId);
        String slug = requestService.ensureCompanySlug(company);
        String resolvedLang = resolveLang(lang);
        if (resolvedLang != null) {
            return "redirect:/p/" + slug + "/order?lang=" + resolvedLang;
        }
        return "redirect:/p/" + slug + "/order";
    }

    private void addUploadConstraints(Model model) {
        model.addAttribute("publicRequestMaxFiles", requestService.getPublicMaxFiles());
        model.addAttribute("publicRequestMaxFileBytes", requestService.getPublicMaxFileBytes());
        model.addAttribute("publicRequestAllowedExt", requestService.getAllowedPublicExtensionsCsv());
    }

    private void validateBusinessRules(PublicOrderRequestForm form, BindingResult bindingResult) {
        if (form == null || bindingResult == null) {
            return;
        }
        LocalDateTime deadline = form.getDeadline();
        if (deadline != null && deadline.isBefore(LocalDateTime.now().minusMinutes(1))) {
            bindingResult.rejectValue("deadline", "public.order.validation.deadline.future");
        }
    }

    private boolean isUploadRelatedError(String messageKey) {
        if (messageKey == null) {
            return false;
        }
        return "public.order.error.max_files".equals(messageKey)
            || "public.order.error.file_too_large".equals(messageKey)
            || "public.order.error.file_type_not_allowed".equals(messageKey)
            || "public.order.error.upload_failed".equals(messageKey);
    }

    private String resolveLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return null;
        }
        return "en".equalsIgnoreCase(lang) ? "en" : "sr";
    }
}
