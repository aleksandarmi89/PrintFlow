package com.printflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpointReturnsExpectedSecurityHeaders() throws Exception {
        mockMvc.perform(get("/public/"))
            .andExpect(status().isOk())
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().string("Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
            .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }
}

