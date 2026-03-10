package com.printflow.controller;

import com.printflow.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IpSecurityFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitService rateLimitService;

    private static final String BLOCKED_IP = "203.0.113.10";

    @AfterEach
    void cleanup() {
        rateLimitService.unban(BLOCKED_IP);
    }

    @Test
    void bannedIpIsBlockedGlobally() throws Exception {
        rateLimitService.ban(BLOCKED_IP, "test", null);

        mockMvc.perform(get("/public/track")
                .header("X-Forwarded-For", BLOCKED_IP))
            .andExpect(status().isForbidden());
    }
}
