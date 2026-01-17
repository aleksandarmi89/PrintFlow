package com.printflow.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

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
        return "redirect:" + path;
    }
    
    protected String redirectWithError(String path, String message, Model model) {
        model.addAttribute("errorMessage", message);
        return "redirect:" + path;
    }
}