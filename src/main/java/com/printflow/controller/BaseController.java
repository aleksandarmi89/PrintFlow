package com.printflow.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class BaseController {
    
    @ModelAttribute
    public void addCommonAttributes(Model model, HttpServletRequest request) {
        model.addAttribute("currentYear", LocalDateTime.now().getYear());
        model.addAttribute("currentDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        model.addAttribute("requestUri", request.getRequestURI());
    }
    
    protected String redirectWithSuccess(String path, String message, Model model) {
        model.addAttribute("successMessage", message);
        addFlashAttribute("successMessage", message);
        return "redirect:" + path;
    }
    
    protected String redirectWithError(String path, String message, Model model) {
        model.addAttribute("errorMessage", message);
        addFlashAttribute("errorMessage", message);
        return "redirect:" + path;
    }

    private void addFlashAttribute(String key, String value) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        if (flashMap != null) {
            flashMap.put(key, value);
        }
    }
}
