package com.printflow.util;

import org.springframework.stereotype.Component;

@Component
public class OrderNumberGenerator {
    
    public String generateOrderNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.valueOf((int)(Math.random() * 1000));
        return "ORD-" + timestamp.substring(timestamp.length() - 6) + "-" + random;
    }
    
    public String generateOrderNumber(String prefix) {
        return prefix + "-" + generateOrderNumber();
    }
}