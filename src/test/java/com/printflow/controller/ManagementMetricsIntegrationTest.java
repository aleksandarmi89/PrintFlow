package com.printflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void managementMetricsListsCustomPrintflowCounters() throws Exception {
        mockMvc.perform(get("/management/metrics")
                .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names", hasItem("printflow_email_send_retries_total")))
            .andExpect(jsonPath("$.names", hasItem("printflow_email_send_failures_total")))
            .andExpect(jsonPath("$.names", hasItem("printflow_rate_limit_denied_total")))
            .andExpect(jsonPath("$.names", hasItem("printflow_rate_limit_auto_ban_total")));
    }

    @Test
    void managementMetricDetailsAreAccessibleForCustomCounter() throws Exception {
        mockMvc.perform(get("/management/metrics/printflow_email_send_failures_total")
                .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("printflow_email_send_failures_total"));
    }
}
