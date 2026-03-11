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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    "app.rate-limit.public-global.max-requests=1",
    "app.rate-limit.public-global.window-seconds=120",
    "app.rate-limit.public-track.enabled=false",
    "app.rate-limit.public-token.enabled=false"
})
class PublicErrorStatusIntegrationTest {

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
        rateLimitService.unban("198.51.100.10");
    }

    @AfterEach
    void tearDown() throws Exception {
        rateLimitService.clearInMemoryState();
        rateLimitService.unban("198.51.100.10");
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void invalidTokenReturns404WithPublicErrorKey() throws Exception {
        mockMvc.perform(get("/public/order/{token}", "missing-token")
                .header("X-Forwarded-For", "198.51.100.20"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"))
            .andExpect(model().attribute("errorKey", "order_not_found.message"))
            .andExpect(model().attribute("errorHeadingKey", "order_not_found.heading"));
    }

    @Test
    void orderNotFoundLinksPreserveSelectedLocale() throws Exception {
        mockMvc.perform(get("/public/order/{token}", "missing-token")
                .param("lang", "en")
                .header("X-Forwarded-For", "198.51.100.21"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/public/track?lang=en")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/public/?lang=en")));
    }

    @Test
    void bannedIpReturns403WithAccessDeniedKey() throws Exception {
        String token = assignPublicToken("status-token-1");
        rateLimitService.ban("198.51.100.10");

        mockMvc.perform(get("/public/order/{token}", token)
                .header("X-Forwarded-For", "198.51.100.10"))
            .andExpect(status().isForbidden())
            .andExpect(content().string("Access denied."));
    }

    @Test
    void repeatedRequestsReturn429WithRateLimitKey() throws Exception {
        String token = assignPublicToken("status-token-2");

        mockMvc.perform(get("/public/order/{token}", token)
                .header("X-Forwarded-For", "198.51.100.30"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"));

        mockMvc.perform(get("/public/order/{token}", token)
                .header("X-Forwarded-For", "198.51.100.30"))
            .andExpect(status().isTooManyRequests())
            .andExpect(view().name("public/order-not-found"))
            .andExpect(model().attribute("errorKey", "public.error.too_many_requests"))
            .andExpect(model().attribute("errorHeadingKey", "public.error.heading"));
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
