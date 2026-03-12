package com.printflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.cors.enabled=true",
    "app.security.cors.allowed-origins= https://allowed.example.com , https://fallback.example.com "
})
class SecurityCorsTrimmedOriginsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightMatchesTrimmedOriginFromConfiguration() throws Exception {
        mockMvc.perform(options("/public/")
                .header("Origin", "https://allowed.example.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://allowed.example.com"));
    }
}
