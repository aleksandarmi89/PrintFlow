package com.printflow.controller;

import com.printflow.entity.Attachment;
import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.Task;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.User.Role;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class TenantIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long workOrderId;
    private Long taskId;
    private Long attachmentId;
    private Path tempFile;

    @BeforeEach
    void setUp() throws Exception {
        Company tenantA = new Company();
        tenantA.setName("Tenant A");
        tenantA = companyRepository.save(tenantA);

        Company tenantB = new Company();
        tenantB.setName("Tenant B");
        tenantB = companyRepository.save(tenantB);

        User adminA = new User();
        adminA.setUsername("tenantA_admin");
        adminA.setPassword(passwordEncoder.encode("password"));
        adminA.setRole(Role.ADMIN);
        adminA.setCompany(tenantA);
        adminA.setFirstName("Admin");
        adminA.setLastName("A");
        adminA.setFullName("Admin A");
        userRepository.save(adminA);

        User adminB = new User();
        adminB.setUsername("tenantB_admin");
        adminB.setPassword(passwordEncoder.encode("password"));
        adminB.setRole(Role.ADMIN);
        adminB.setCompany(tenantB);
        adminB.setFirstName("Admin");
        adminB.setLastName("B");
        adminB.setFullName("Admin B");
        userRepository.save(adminB);

        Client clientA = new Client();
        clientA.setCompanyName("Client A");
        clientA.setPhone("123456");
        clientA.setEmail("clientA@example.com");
        clientA.setCompany(tenantA);
        clientA = clientRepository.save(clientA);

        WorkOrder orderA = new WorkOrder();
        orderA.setOrderNumber("WO-A-1000");
        orderA.setTitle("Tenant A Order");
        orderA.setStatus(OrderStatus.NEW);
        orderA.setClient(clientA);
        orderA.setCompany(tenantA);
        orderA = workOrderRepository.save(orderA);
        workOrderId = orderA.getId();

        Task taskA = new Task();
        taskA.setTitle("Tenant A Task");
        taskA.setStatus(TaskStatus.NEW);
        taskA.setWorkOrder(orderA);
        taskA.setCompany(tenantA);
        taskA = taskRepository.save(taskA);
        taskId = taskA.getId();

        tempFile = Files.createTempFile("tenant-idor-", ".txt");
        Files.writeString(tempFile, "secret");

        Attachment attachment = new Attachment();
        attachment.setFileName("secret.txt");
        attachment.setOriginalFileName("secret.txt");
        attachment.setFilePath(tempFile.toString());
        attachment.setMimeType("text/plain");
        attachment.setFileSize(Files.size(tempFile));
        attachment.setAttachmentType(AttachmentType.DESIGN_PREVIEW);
        attachment.setWorkOrder(orderA);
        attachment.setCompany(tenantA);
        attachment = attachmentRepository.save(attachment);
        attachmentId = attachment.getId();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void orderFromTenantACannotBeAccessedByTenantB() throws Exception {
        MockHttpSession session = login("tenantB_admin", "password");
        mockMvc.perform(get("/admin/orders/{id}", workOrderId).session(session))
            .andExpect(tenantIsolationStatus());
    }

    @Test
    void taskFromTenantACannotBeAccessedByTenantB() throws Exception {
        MockHttpSession session = login("tenantB_admin", "password");
        mockMvc.perform(get("/admin/tasks/{id}", taskId).session(session))
            .andExpect(tenantIsolationStatus());
    }

    @Test
    void attachmentFromTenantACannotBeAccessedByTenantB() throws Exception {
        MockHttpSession session = login("tenantB_admin", "password");
        mockMvc.perform(get("/api/files/download/{id}", attachmentId).session(session))
            .andExpect(status().isNotFound());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/login").user(username).password(password))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static ResultMatcher tenantIsolationStatus() {
        return status().isNotFound();
    }
}
