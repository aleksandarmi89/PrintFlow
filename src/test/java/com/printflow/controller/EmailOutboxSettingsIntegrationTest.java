package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.EmailOutbox;
import com.printflow.entity.User;
import com.printflow.entity.enums.EmailOutboxStatus;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.EmailOutboxRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailOutboxSettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailOutboxRepository emailOutboxRepository;

    private TenantTestFixture fixture;

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void outboxPageShowsOnlyCurrentTenantEntries() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company1 = companyRepository.findById(ids.company1Id()).orElseThrow();
        Company company2 = companyRepository.findById(ids.company2Id()).orElseThrow();

        EmailOutbox c1 = new EmailOutbox();
        c1.setCompany(company1);
        c1.setToEmail("tenant1@example.com");
        c1.setSubject("Tenant 1 subject");
        c1.setTemplate("template-1");
        c1.setStatus(EmailOutboxStatus.FAILED);
        c1.setErrorMessage("smtp fail");
        emailOutboxRepository.save(c1);

        EmailOutbox c2 = new EmailOutbox();
        c2.setCompany(company2);
        c2.setToEmail("tenant2@example.com");
        c2.setSubject("Tenant 2 subject");
        c2.setTemplate("template-2");
        c2.setStatus(EmailOutboxStatus.SENT);
        emailOutboxRepository.save(c2);

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/settings/email").session(tenant1))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("tenant1@example.com")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("tenant2@example.com"))));
    }

    @Test
    void cleanupFailedDeletesOnlyCurrentTenantFailedRows() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company1 = companyRepository.findById(ids.company1Id()).orElseThrow();
        Company company2 = companyRepository.findById(ids.company2Id()).orElseThrow();

        EmailOutbox c1Failed = new EmailOutbox();
        c1Failed.setCompany(company1);
        c1Failed.setToEmail("tenant1-failed@example.com");
        c1Failed.setStatus(EmailOutboxStatus.FAILED);
        emailOutboxRepository.save(c1Failed);

        EmailOutbox c1Sent = new EmailOutbox();
        c1Sent.setCompany(company1);
        c1Sent.setToEmail("tenant1-sent@example.com");
        c1Sent.setStatus(EmailOutboxStatus.SENT);
        emailOutboxRepository.save(c1Sent);

        EmailOutbox c2Failed = new EmailOutbox();
        c2Failed.setCompany(company2);
        c2Failed.setToEmail("tenant2-failed@example.com");
        c2Failed.setStatus(EmailOutboxStatus.FAILED);
        emailOutboxRepository.save(c2Failed);

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/settings/email/outbox/cleanup-failed")
                .with(csrf())
                .session(tenant1)
                .param("days", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/settings/email?successKey=*&cleanupCount=*"));

        assertThat(emailOutboxRepository.countByCompany_IdAndStatus(company1.getId(), EmailOutboxStatus.FAILED)).isZero();
        assertThat(emailOutboxRepository.countByCompany_IdAndStatus(company1.getId(), EmailOutboxStatus.SENT)).isEqualTo(1);
        assertThat(emailOutboxRepository.countByCompany_IdAndStatus(company2.getId(), EmailOutboxStatus.FAILED)).isEqualTo(1);
    }

    @Test
    void nonAdminCannotAccessEmailSettingsOrCleanupOutbox() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company1 = companyRepository.findById(ids.company1Id()).orElseThrow();

        User worker = new User();
        worker.setUsername("tenant1_worker_email");
        worker.setPassword(passwordEncoder.encode("password"));
        worker.setRole(User.Role.WORKER_GENERAL);
        worker.setCompany(company1);
        worker.setFirstName("Worker");
        worker.setLastName("Email");
        worker.setActive(true);
        userRepository.save(worker);

        MockHttpSession workerSession = fixture.login("tenant1_worker_email", "password");

        mockMvc.perform(get("/settings/email").session(workerSession))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/settings/email/outbox/cleanup-failed")
                .with(csrf())
                .session(workerSession)
                .param("days", "0"))
            .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCanOpenOwnSettingsPasswordPage() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company1 = companyRepository.findById(ids.company1Id()).orElseThrow();

        User worker = new User();
        worker.setUsername("tenant1_worker_settings");
        worker.setPassword(passwordEncoder.encode("password"));
        worker.setRole(User.Role.WORKER_GENERAL);
        worker.setCompany(company1);
        worker.setFirstName("Worker");
        worker.setLastName("Settings");
        worker.setActive(true);
        userRepository.save(worker);

        MockHttpSession workerSession = fixture.login("tenant1_worker_settings", "password");

        mockMvc.perform(get("/settings").session(workerSession))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/settings/password")));
    }

    @Test
    void anonymousUserIsRedirectedToLoginForEmailSettings() throws Exception {
        mockMvc.perform(get("/settings/email"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost/login"));
    }
}
