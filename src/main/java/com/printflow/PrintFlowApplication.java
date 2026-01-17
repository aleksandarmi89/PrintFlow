package com.printflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class PrintFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrintFlowApplication.class, args);
    }
}