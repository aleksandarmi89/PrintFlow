package com.printflow.controller;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.MailSettingsRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.entity.User;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.testsupport.TenantTestFixture;
import com.printflow.testsupport.TenantTestFixture.TenantIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class TenantAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private WorkOrderItemRepository workOrderItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private MailSettingsRepository mailSettingsRepository;
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
    void workOrderAccessRespectsTenant() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        String publicToken = workOrderRepository.findById(ids.workOrderId()).orElseThrow().getPublicToken();
        mockMvc.perform(get("/admin/orders/{id}", ids.workOrderId()).session(tenant1Session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/public/order/" + publicToken + "?lang=")));

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/admin/orders/{id}", ids.workOrderId()).session(tenant2Session))
            .andExpect(tenantIsolationStatus());
    }

    @Test
    void superAdminCanAccessOrderAcrossTenants() throws Exception {
        User superAdmin = userRepository.findByUsername("tenant_super_admin")
            .orElseGet(() -> {
                User u = new User();
                u.setUsername("tenant_super_admin");
                u.setPassword(passwordEncoder.encode("password"));
                u.setRole(User.Role.SUPER_ADMIN);
                u.setCompany(companyRepository.findById(ids.company2Id()).orElseThrow());
                u.setActive(true);
                u.setFirstName("Super");
                u.setLastName("Admin");
                u.setFullName("Super Admin");
                return userRepository.save(u);
            });
        superAdmin.setRole(User.Role.SUPER_ADMIN);
        superAdmin.setActive(true);
        if (superAdmin.getCompany() == null) {
            superAdmin.setCompany(companyRepository.findById(ids.company2Id()).orElseThrow());
        }
        userRepository.save(superAdmin);

        MockHttpSession superAdminSession = fixture.login("tenant_super_admin", "password");
        mockMvc.perform(get("/admin/orders/{id}", ids.workOrderId()).session(superAdminSession))
            .andExpect(status().isOk());
    }

    @Test
    void workOrderPdfDownloadRespectsTenant() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPrice(315.0d); // stale header value; quote PDF should still follow item totals
        workOrderRepository.save(order);

        ProductVariant variant = productVariantRepository.findAllByCompany_Id(ids.company1Id())
            .stream()
            .findFirst()
            .orElseGet(() -> {
                Product product = new Product();
                product.setCompany(order.getCompany());
                product.setName("Tenant Access PDF Product");
                product.setCategory(ProductCategory.OTHER);
                product.setUnitType(UnitType.PIECE);
                product.setBasePrice(BigDecimal.ZERO);
                product.setCurrency("RSD");
                Product savedProduct = productRepository.save(product);

                ProductVariant fallbackVariant = new ProductVariant();
                fallbackVariant.setCompany(order.getCompany());
                fallbackVariant.setProduct(savedProduct);
                fallbackVariant.setName("Standard");
                fallbackVariant.setDefaultMarkupPercent(new BigDecimal("20.00"));
                fallbackVariant.setWastePercent(BigDecimal.ZERO);
                return productVariantRepository.save(fallbackVariant);
            });
        WorkOrderItem item = new WorkOrderItem();
        item.setCompany(order.getCompany());
        item.setWorkOrder(order);
        item.setVariant(variant);
        item.setQuantity(new BigDecimal("100"));
        item.setCalculatedCost(new BigDecimal("200000.00"));
        item.setCalculatedPrice(new BigDecimal("300000.00"));
        item.setProfitAmount(new BigDecimal("100000.00"));
        item.setMarginPercent(new BigDecimal("33.33"));
        item.setCurrency("RSD");
        item.setBreakdownJson("{\"breakdown\":[]}");
        item.setPricingSnapshotJson("{}");
        item.setPriceLocked(true);
        item.setPriceCalculatedAt(LocalDateTime.now());
        workOrderItemRepository.save(item);

        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        byte[] pdf = mockMvc.perform(get("/admin/orders/{id}/pdf/quote", ids.workOrderId()).param("lang", "en").session(tenant1Session))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"quote-")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        String text = extractPdfText(pdf);
        assertThat(text).contains("Quote total");
        assertThat(text).contains("300,000.00 RSD");
        assertThat(text).doesNotContain("315.00 RSD");

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/admin/orders/{id}/pdf/quote", ids.workOrderId()).session(tenant2Session))
            .andExpect(tenantIsolationStatus());
    }

    @Test
    void superAdminCanDownloadOrderSummaryPdfAcrossTenants() throws Exception {
        User superAdmin = userRepository.findByUsername("tenant_super_admin")
            .orElseGet(() -> {
                User u = new User();
                u.setUsername("tenant_super_admin");
                u.setPassword(passwordEncoder.encode("password"));
                u.setRole(User.Role.SUPER_ADMIN);
                u.setCompany(companyRepository.findById(ids.company2Id()).orElseThrow());
                u.setActive(true);
                u.setFirstName("Super");
                u.setLastName("Admin");
                u.setFullName("Super Admin");
                return userRepository.save(u);
            });
        superAdmin.setRole(User.Role.SUPER_ADMIN);
        superAdmin.setActive(true);
        if (superAdmin.getCompany() == null) {
            superAdmin.setCompany(companyRepository.findById(ids.company2Id()).orElseThrow());
        }
        userRepository.save(superAdmin);

        MockHttpSession superAdminSession = fixture.login("tenant_super_admin", "password");
        mockMvc.perform(get("/admin/orders/{id}/pdf/summary", ids.workOrderId()).session(superAdminSession))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"order-summary-")));
    }

    @Test
    void emailSettingsAreTenantIsolated() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/settings/email")
                .session(tenant1Session)
                .with(csrf())
                .param("enabled", "true")
                .param("smtpHost", "smtp.tenant1.test")
                .param("smtpPort", "587")
                .param("smtpUsername", "tenant1@example.com")
                .param("smtpPassword", "secret")
                .param("smtpUseTls", "true")
                .param("fromEmail", "noreply@tenant1.test")
                .param("fromName", "Tenant1"))
            .andExpect(status().is3xxRedirection());

        assertThat(mailSettingsRepository.findByCompany_Id(ids.company1Id())).isPresent();
        assertThat(mailSettingsRepository.findByCompany_Id(ids.company2Id())).isEmpty();

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/settings/email").session(tenant2Session))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("smtp.tenant1.test"))));
    }

    @Test
    void taskAccessRespectsTenant() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/admin/tasks/{id}", ids.taskId()).session(tenant1Session))
            .andExpect(status().isOk());

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/admin/tasks/{id}", ids.taskId()).session(tenant2Session))
            .andExpect(tenantIsolationStatus());
    }

    @Test
    void clientAccessRespectsTenant() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/admin/clients/edit/{id}", ids.clientId()).session(tenant1Session))
            .andExpect(status().isOk());

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/admin/clients/edit/{id}", ids.clientId()).session(tenant2Session))
            .andExpect(tenantIsolationStatus());
    }

    @Test
    void superAdminCanAccessClientAcrossTenants() throws Exception {
        User superAdmin = userRepository.findByUsername("tenant_super_admin")
            .orElseGet(() -> {
                User u = new User();
                u.setUsername("tenant_super_admin");
                u.setPassword(passwordEncoder.encode("password"));
                u.setRole(User.Role.SUPER_ADMIN);
                u.setCompany(companyRepository.findById(ids.company2Id()).orElseThrow());
                u.setActive(true);
                u.setFirstName("Super");
                u.setLastName("Admin");
                u.setFullName("Super Admin");
                return userRepository.save(u);
            });
        superAdmin.setRole(User.Role.SUPER_ADMIN);
        superAdmin.setActive(true);
        if (superAdmin.getCompany() == null) {
            superAdmin.setCompany(companyRepository.findById(ids.company2Id()).orElseThrow());
        }
        userRepository.save(superAdmin);

        MockHttpSession superAdminSession = fixture.login("tenant_super_admin", "password");
        mockMvc.perform(get("/admin/clients/edit/{id}", ids.clientId()).session(superAdminSession))
            .andExpect(status().isOk());
    }

    @Test
    void attachmentAccessRespectsTenant() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/api/files/download/{id}", ids.attachmentId()).session(tenant1Session))
            .andExpect(status().isOk());

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/api/files/download/{id}", ids.attachmentId()).session(tenant2Session))
            .andExpect(status().isNotFound());
    }

    @Test
    void attachmentViewIsTenantScoped() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/api/files/view/{id}", ids.attachmentId()).session(tenant1Session))
            .andExpect(status().isOk());

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/api/files/view/{id}", ids.attachmentId()).session(tenant2Session))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminClientsPageRendersWithoutLazyInit() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/admin/clients").session(tenant1Session))
            .andExpect(status().isOk());
    }

    @Test
    void createTaskPersistsDefaultStatus() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/admin/tasks/create")
                .session(tenant1Session)
                .with(csrf())
                .param("title", "New Task")
                .param("description", "Task description")
                .param("priority", "MEDIUM")
                .param("workOrderId", String.valueOf(ids.workOrderId())))
            .andExpect(status().is3xxRedirection());

        var tasks = taskRepository.findByWorkOrderId(ids.workOrderId());
        assertThat(tasks)
            .extracting("title")
            .contains("New Task");
        assertThat(tasks)
            .filteredOn(task -> "New Task".equals(task.getTitle()))
            .allMatch(task -> task.getStatus() != null);
    }

    @Test
    void taskListIncludesAssignedStatusForTenant() throws Exception {
        var tenant1 = companyRepository.findById(ids.company1Id()).orElseThrow();
        var tenant2 = companyRepository.findById(ids.company2Id()).orElseThrow();
        var tenant1User = userRepository.findByUsername("tenant1_admin").orElseThrow();
        var tenant2User = userRepository.findByUsername("tenant2_admin").orElseThrow();
        var workOrder = workOrderRepository.findById(ids.workOrderId()).orElseThrow();

        com.printflow.entity.Task assignedTask = new com.printflow.entity.Task();
        assignedTask.setTitle("Assigned Task Visible");
        assignedTask.setStatus(com.printflow.entity.enums.TaskStatus.ASSIGNED);
        assignedTask.setAssignedTo(tenant1User);
        assignedTask.setWorkOrder(workOrder);
        assignedTask.setCompany(tenant1);
        taskRepository.save(assignedTask);

        com.printflow.entity.Task otherTenantTask = new com.printflow.entity.Task();
        otherTenantTask.setTitle("Assigned Task Hidden");
        otherTenantTask.setStatus(com.printflow.entity.enums.TaskStatus.ASSIGNED);
        otherTenantTask.setAssignedTo(tenant2User);
        otherTenantTask.setCompany(tenant2);
        taskRepository.save(otherTenantTask);

        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/admin/tasks").session(tenant1Session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Assigned Task Visible")))
            .andExpect(content().string(not(containsString("Assigned Task Hidden"))));

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/admin/tasks").session(tenant2Session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Assigned Task Hidden")))
            .andExpect(content().string(not(containsString("Assigned Task Visible"))));
    }

    @Test
    void adminCanOpenPricingPages() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/admin/pricing/products").session(tenant1Session))
            .andExpect(status().isOk());
        mockMvc.perform(get("/pricing/calculate").session(tenant1Session))
            .andExpect(status().isOk());
    }

    @Test
    void attachmentThumbnailIsTenantScoped() throws Exception {
        MockHttpSession tenant1Session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/api/files/thumbnail/{id}", ids.attachmentId()).session(tenant1Session))
            .andExpect(status().isOk());

        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(get("/api/files/thumbnail/{id}", ids.attachmentId()).session(tenant2Session))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteClientIsTenantScoped() throws Exception {
        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(post("/admin/clients/delete/{id}", ids.clientId())
                .with(csrf())
                .session(tenant2Session))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteOrderIsTenantScoped() throws Exception {
        MockHttpSession tenant2Session = fixture.login("tenant2_admin", "password");
        mockMvc.perform(post("/admin/orders/{id}/delete", ids.workOrderId())
                .with(csrf())
                .session(tenant2Session))
            .andExpect(status().isNotFound());
    }

    private String extractPdfText(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                sb.append(extractor.getTextFromPage(i)).append('\n');
            }
            return sb.toString();
        }
    }

    private static ResultMatcher tenantIsolationStatus() {
        return result -> {
            int statusCode = result.getResponse().getStatus();
            assertThat(statusCode)
                .as("tenant-isolated endpoint should not be accessible")
                .isIn(302, 403, 404);
        };
    }
}
