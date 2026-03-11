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
class SecurityCorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void noCorsHeadersWhenCorsIsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/public/")
                .header("Origin", "https://evil.example.com"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

