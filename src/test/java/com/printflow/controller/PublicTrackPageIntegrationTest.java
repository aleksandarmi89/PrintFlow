package com.printflow.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicTrackPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicTrackPageRendersSerbianCopy() throws Exception {
        mockMvc.perform(get("/public/track")
                .header("Accept-Language", "sr"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Kod za pra\u0107enje")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unesi kod za pra\u0107enje")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Primer:")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ORD-123456-789")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Koristi broj naloga")));
    }

    @Test
    void publicTrackPageRendersEnglishCopyWhenLangEn() throws Exception {
        mockMvc.perform(get("/public/track").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tracking Code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter your tracking code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Example:")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"lang\" value=\"en\"")));
    }

    @Test
    void publicTrackPageClampsHiddenLangToSrForUnsupportedLocale() throws Exception {
        mockMvc.perform(get("/public/track").param("lang", "de"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"lang\" value=\"sr\"")));
    }
}
