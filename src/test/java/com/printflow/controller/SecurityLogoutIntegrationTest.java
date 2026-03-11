package com.printflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityLogoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void logoutRedirectsToLoginAndClearsSessionCookie() throws Exception {
        mockMvc.perform(post("/logout")
                .with(user("test-admin").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?logout=true"))
            .andExpect(cookie().maxAge("JSESSIONID", 0));
    }

    @Test
    void logoutWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/logout")
                .with(user("test-admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
}
