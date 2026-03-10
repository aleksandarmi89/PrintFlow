package com.printflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Mapiranje osnovnih URL-ova na view-ove
        registry.addViewController("/").setViewName("redirect:/public/");
        registry.addViewController("/login").setViewName("auth/login");
        registry.addViewController("/register").setViewName("auth/register");
        registry.addViewController("/access-denied").setViewName("auth/access-denied");
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mapiranje za uploads folder
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
        
        // Mapiranje za statičke resurse
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}