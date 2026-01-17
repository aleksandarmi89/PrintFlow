package com.printflow.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController extends BaseController {
    
    @GetMapping("/")
    public String home(Model model) {
        return "redirect:/public/";
    }
    
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }
    
    @GetMapping("/register")
    public String registerPage(Model model) {
        return "auth/register";
    }
    
    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }
    
    @GetMapping("/profile")
    public String profile() {
        return "auth/profile";
    }
    
    @GetMapping("/settings")
    public String settings() {
        return "auth/settings";
    }
}