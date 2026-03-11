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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.cors.enabled=true",
    "app.security.cors.allowed-origins= https://one.example.com ,https://two.example.com "
})
class SecurityCorsMultipleOriginsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void firstConfiguredOriginIsAllowed() throws Exception {
        mockMvc.perform(get("/public/")
                .header("Origin", "https://one.example.com"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://one.example.com"));
    }

    @Test
    void secondConfiguredOriginIsAllowed() throws Exception {
        mockMvc.perform(get("/public/")
                .header("Origin", "https://two.example.com"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://two.example.com"));
    }
}

