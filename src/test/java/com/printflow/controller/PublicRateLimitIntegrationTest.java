package com.printflow.controller;

import com.printflow.entity.WorkOrder;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.service.RateLimitService;
import com.printflow.testsupport.TenantTestFixture;
import com.printflow.testsupport.TenantTestFixture.TenantIds;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
    "app.rate-limit.public-global.enabled=true",
    "app.rate-limit.public-global.max-requests=2",
    "app.rate-limit.public-global.window-seconds=120",
    "app.rate-limit.public-track.enabled=true",
    "app.rate-limit.public-track.max-requests=2",
    "app.rate-limit.public-track.window-seconds=120",
    "app.rate-limit.public-token.enabled=false"
})
class PublicRateLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RateLimitService rateLimitService;

    private TenantTestFixture fixture;
    private TenantIds ids;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new TenantTestFixture(
            mockMvc,
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            taskRepository,
            attachmentRepository,
            passwordEncoder
        );
        ids = fixture.createTenantData();
        rateLimitService.clearInMemoryState();
    }

    @AfterEach
    void tearDown() throws Exception {
        rateLimitService.clearInMemoryState();
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void publicOrderEndpointIsRateLimitedAfterBurst() throws Exception {
        String token = assignPublicToken("burst-order-token");

        mockMvc.perform(get("/public/order/{token}", token))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"));

        mockMvc.perform(get("/public/order/{token}", token))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"));

        mockMvc.perform(get("/public/order/{token}", token))
            .andExpect(status().isTooManyRequests())
            .andExpect(view().name("public/order-not-found"))
            .andExpect(model().attribute("errorKey", "public.error.too_many_requests"));
    }

    @Test
    void publicTrackSubmitEndpointIsRateLimitedAfterBurst() throws Exception {
        String trackingCode = "ORD-FAKE-001";

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("trackingCode", trackingCode)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("trackingCode", trackingCode)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("trackingCode", trackingCode)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().isOk())
            .andExpect(view().name("public/track-order"))
            .andExpect(model().attribute("errorKey", "track.error.too_many_requests"));
    }

    private String assignPublicToken(String token) {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        return token;
    }
}
