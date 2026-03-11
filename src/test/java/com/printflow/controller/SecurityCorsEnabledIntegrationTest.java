package com.printflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.cors.enabled=true",
    "app.security.cors.allowed-origins=https://allowed.example.com"
})
class SecurityCorsEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowedOriginGetsCorsHeader() throws Exception {
        mockMvc.perform(get("/public/")
                .header("Origin", "https://allowed.example.com"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://allowed.example.com"));
    }

    @Test
    void disallowedOriginDoesNotGetCorsHeader() throws Exception {
        mockMvc.perform(get("/public/")
                .header("Origin", "https://blocked.example.com"))
            .andExpect(status().isForbidden())
            .andExpect(content().string("Invalid CORS request"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
