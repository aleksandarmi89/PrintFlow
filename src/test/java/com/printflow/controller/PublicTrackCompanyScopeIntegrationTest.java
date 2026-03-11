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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class PublicTrackCompanyScopeIntegrationTest {

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
    void trackFormRejectsTokenWhenSelectedCompanyDoesNotMatch() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "scope-token-1";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("trackingCode", token)
                .param("company", String.valueOf(ids.company2Id())))
            .andExpect(status().isOk())
            .andExpect(view().name("public/track-order"))
            .andExpect(model().attribute("errorKey", "track.error.company_mismatch"))
            .andExpect(model().attribute("submittedTrackingCode", token))
            .andExpect(content().string(containsString("name=\"trackingCode\"")))
            .andExpect(content().string(containsString("value=\"" + token + "\"")))
            .andExpect(content().string(containsString("name=\"company\" value=\"" + ids.company2Id() + "\"")))
            .andExpect(content().string(containsString("bg-red-50 border border-red-200")));
    }

    @Test
    void trackFormFallsBackToSerbianLangHiddenFieldForUnsupportedLocale() throws Exception {
        mockMvc.perform(get("/public/track").param("lang", "de"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/track-order"))
            .andExpect(content().string(containsString("name=\"lang\" value=\"sr\"")));
    }

    @Test
    void trackFormTrimsCodeAndRedirectsToCanonicalOrderUrl() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "scope-token-2";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("trackingCode", "  " + token + "  ")
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token));
    }

    @Test
    void trackFormRedirectPreservesSupportedLocale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "scope-token-lang";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("lang", "en")
                .param("trackingCode", token)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void trackFormRedirectIgnoresUnsupportedLocale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "scope-token-lang-unsupported";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("lang", "de")
                .param("trackingCode", token)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token));
    }

    @Test
    void trackFormRedirectNormalizesUppercaseLocale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "scope-token-lang-uppercase";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("lang", "EN")
                .param("trackingCode", token)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void trackFormRedirectTrimsAndNormalizesLocale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "scope-token-lang-trimmed";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("lang", "  EN  ")
                .param("trackingCode", token)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void trackFormRejectsTooLongTrackingCode() throws Exception {
        String tooLongCode = "x".repeat(200);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("trackingCode", tooLongCode)
                .param("company", String.valueOf(ids.company1Id())))
            .andExpect(status().isOk())
            .andExpect(view().name("public/track-order"))
            .andExpect(model().attribute("errorKey", "track.error.invalid_code"))
            .andExpect(model().attribute("submittedTrackingCode", tooLongCode))
            .andExpect(content().string(containsString("bg-red-50 border border-red-200")));
    }

    @Test
    void trackFormCompanyMismatchUsesEnglishMessageWhenLangEn() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "scope-token-en";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .with(csrf())
                .param("lang", "en")
                .param("trackingCode", token)
                .param("company", String.valueOf(ids.company2Id())))
            .andExpect(status().isOk())
            .andExpect(view().name("public/track-order"))
            .andExpect(model().attribute("errorKey", "track.error.company_mismatch"))
            .andExpect(content().string(containsString("Selected company does not match this tracking code.")))
            .andExpect(content().string(not(containsString("Izabrana kompanija ne odgovara ovom kodu za praćenje."))));
    }
}
