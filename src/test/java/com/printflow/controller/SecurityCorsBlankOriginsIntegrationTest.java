package com.printflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.cors.enabled=true",
    "app.security.cors.allowed-origins=   "
})
class SecurityCorsBlankOriginsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestDoesNotExposeCorsHeadersWhenOriginsListIsBlank() throws Exception {
        mockMvc.perform(get("/public/")
                .header("Origin", "https://any.example.com"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightIsForbiddenWhenOriginsListIsBlank() throws Exception {
        mockMvc.perform(options("/public/")
                .header("Origin", "https://any.example.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(content().string("Invalid CORS request"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

