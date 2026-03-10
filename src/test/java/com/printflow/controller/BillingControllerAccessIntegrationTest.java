package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.enums.BillingInterval;
import com.printflow.entity.enums.PlanTier;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.BillingPlanConfigRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.testsupport.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BillingPlanConfigRepository billingPlanConfigRepository;

    private TenantTestFixture fixture;

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void anonymousUserIsRedirectedToLoginForBillingPage() throws Exception {
        mockMvc.perform(get("/admin/billing"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void adminCanOpenBillingPageAndResolveOwnTenantContext() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/admin/billing").session(tenant1))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/billing/index"))
            .andExpect(model().attribute("company", org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(ids.company1Id()))));
    }

    @Test
    void workerCannotAccessBillingPage() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company1 = companyRepository.findById(ids.company1Id()).orElseThrow();

        User worker = new User();
        worker.setUsername("tenant1_worker_billing");
        worker.setPassword(passwordEncoder.encode("password"));
        worker.setRole(User.Role.WORKER_GENERAL);
        worker.setCompany(company1);
        worker.setFirstName("Worker");
        worker.setLastName("Billing");
        worker.setActive(true);
        userRepository.save(worker);

        MockHttpSession session = fixture.login("tenant1_worker_billing", "password");
        mockMvc.perform(get("/admin/billing").session(session))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotUpdateBillingPriceConfig() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        fixture.createTenantData();

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/admin/billing/config")
                .session(tenant1)
                .with(csrf())
                .param("priceIdProMonthly", "price_live_admin_attempt"))
            .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanUpdateBillingPriceConfig() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company1 = companyRepository.findById(ids.company1Id()).orElseThrow();

        User superAdmin = new User();
        superAdmin.setUsername("billing_super_admin");
        superAdmin.setPassword(passwordEncoder.encode("password"));
        superAdmin.setRole(User.Role.SUPER_ADMIN);
        superAdmin.setCompany(company1);
        superAdmin.setFirstName("Super");
        superAdmin.setLastName("Admin");
        superAdmin.setActive(true);
        userRepository.save(superAdmin);

        MockHttpSession session = fixture.login("billing_super_admin", "password");
        mockMvc.perform(post("/admin/billing/config")
                .session(session)
                .with(csrf())
                .param("priceIdFreeMonthly", "price_free_m")
                .param("priceIdFreeYearly", "price_free_y")
                .param("priceIdProMonthly", "price_pro_m")
                .param("priceIdProYearly", "price_pro_y")
                .param("priceIdTeamMonthly", "price_team_m")
                .param("priceIdTeamYearly", "price_team_y"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/billing?success=billing.config.saved"));

        assertThat(billingPlanConfigRepository.findByPlanAndInterval(PlanTier.PRO, BillingInterval.MONTHLY))
            .isPresent()
            .get()
            .extracting(config -> config.getStripePriceId())
            .isEqualTo("price_pro_m");
    }
}
