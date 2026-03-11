package com.printflow.controller;

import com.printflow.entity.WorkOrder;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.testsupport.TenantTestFixture;
import com.printflow.testsupport.TenantTestFixture.TenantIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class PublicOrderTokenIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void expiredPublicTokenIsRejected() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("expired-token");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusDays(5));
        order.setPublicTokenExpiresAt(LocalDateTime.now().minusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "expired-token"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"));
    }

    @Test
    void rotatedPublicTokenInvalidatesOldLink() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String oldToken = "rotate-token";
        order.setPublicToken(oldToken);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusDays(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(5));
        workOrderRepository.save(order);

        MockHttpSession session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/admin/orders/{id}/rotate-public-token", order.getId())
                .session(session)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        workOrderRepository.flush();
        WorkOrder updated = workOrderRepository.findById(order.getId()).orElseThrow();
        String newToken = updated.getPublicToken();
        assertNotEquals(oldToken, newToken);

        mockMvc.perform(get("/public/order/{token}", oldToken))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"));

        mockMvc.perform(get("/public/order/{token}", newToken))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"));
    }

    @Test
    void tokenWithWhitespaceRedirectsToCanonicalPublicOrderUrl() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("canonical-token");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "  canonical-token  "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/canonical-token"));
    }

    @Test
    void canonicalOrderRedirectPreservesNormalizedSupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("canonical-token-lang");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "  canonical-token-lang  ")
                .param("lang", "  EN  "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/canonical-token-lang?lang=en"));
    }

    @Test
    void orderNumberRedirectPreservesNormalizedSupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("resolved-token-lang");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", order.getOrderNumber())
                .param("lang", "  EN  "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/resolved-token-lang?lang=en"));
    }

    @Test
    void orderNumberRedirectOmitsUnsupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("resolved-token-no-lang");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", order.getOrderNumber())
                .param("lang", "de"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/resolved-token-no-lang"));
    }

    @Test
    void tooLongTokenIsRejectedAsNotFound() throws Exception {
        String tooLongToken = "x".repeat(200);

        mockMvc.perform(get("/public/order/{token}", tooLongToken))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"));
    }

    @Test
    void orderTrackingPageIncludesLangHiddenFieldForPostFormsWhenLangEn() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(content().string(containsString("name=\"lang\" value=\"en\"")));
    }

    @Test
    void orderTrackingPageFallsBackToSerbianLangFieldForUnsupportedLocale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-lang-token-fallback";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "de"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(content().string(containsString("name=\"lang\" value=\"sr\"")));
    }
}
