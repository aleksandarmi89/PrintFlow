package com.printflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerRendersTranslatedPasswordMismatchMessage() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("companyName", "Acme")
                .param("username", "admin")
                .param("fullName", "Admin User")
                .param("email", "admin@acme.test")
                .param("phone", "+381")
                .param("password", "secret123")
                .param("confirmPassword", "different123"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Passwords do not match!")))
            .andExpect(content().string(not(containsString("auth.password_mismatch"))));
    }

    @Test
    void registerPageContainsSingleCsrfField() throws Exception {
        assertSingleCsrfField("/register");
    }

    @Test
    void loginPageContainsSingleCsrfField() throws Exception {
        assertSingleCsrfField("/login");
    }

    @Test
    void forgotPasswordPageContainsSingleCsrfField() throws Exception {
        assertSingleCsrfField("/forgot-password");
    }

    @Test
    void registerRendersTranslatedSuccessMessage() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("companyName", "Acme-" + suffix)
                .param("username", "admin_" + suffix)
                .param("fullName", "Admin User")
                .param("email", "admin+" + suffix + "@acme.test")
                .param("phone", "+381")
                .param("password", "secret123")
                .param("confirmPassword", "secret123"))
            .andExpect(status().isOk())
            .andExpect(content().string(anyOf(
                containsString("Company created. You can now sign in."),
                containsString("Kompanija je kreirana.")
            )))
            .andExpect(content().string(not(containsString("auth.register.success"))));
    }

    private void assertSingleCsrfField(String uri) throws Exception {
        String html = mockMvc.perform(get(uri))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        int occurrences = html.split("name=\"_csrf\"", -1).length - 1;
        assertEquals(1, occurrences);
    }
}
