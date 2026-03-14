package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.Task;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.MailSettings;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.MailSettingsRepository;
import com.printflow.testsupport.TenantTestFixture;
import com.printflow.testsupport.TenantTestFixture.TenantIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
class RegressionPagesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private MailSettingsRepository mailSettingsRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private TenantTestFixture fixture;
    private TenantIds tenantIds;
    private ProductVariant variant;
    private MockHttpSession adminSession;
    private MockHttpSession workerSession;

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
        tenantIds = fixture.createTenantData();

        Company company = companyRepository.findById(tenantIds.company1Id()).orElseThrow();
        MailSettings smtp = mailSettingsRepository.findByCompany_Id(company.getId()).orElseGet(MailSettings::new);
        smtp.setCompany(company);
        smtp.setEnabled(true);
        smtp.setSmtpHost("smtp.example.com");
        smtp.setSmtpPort(587);
        smtp.setSmtpUsername("noreply@example.com");
        smtp.setSmtpPasswordEnc("enc-test");
        mailSettingsRepository.save(smtp);

        Product product = new Product();
        product.setCompany(company);
        product.setName("Regression Product");
        product.setCategory(ProductCategory.OTHER);
        product.setUnitType(pickAnyPieceUnit());
        productRepository.save(product);

        variant = new ProductVariant();
        variant.setCompany(company);
        variant.setProduct(product);
        variant.setName("Regression Variant");
        productVariantRepository.save(variant);

        adminSession = fixture.login("tenant1_admin", "password");

        Role workerRole = pickWorkerLikeRole();
        User worker = userRepository.findByUsername("tenant1_worker")
            .orElseGet(() -> {
                User user = new User();
                user.setUsername("tenant1_worker");
                user.setPassword(passwordEncoder.encode("password"));
                user.setRole(workerRole);
                user.setCompany(company);
                user.setFirstName("Worker");
                user.setLastName("One");
                user.setFullName("Worker One");
                return userRepository.save(user);
            });

        Optional<Task> taskOpt = taskRepository.findById(tenantIds.taskId());
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            if (task.getAssignedTo() == null) {
                task.setAssignedTo(worker);
                taskRepository.save(task);
            }
        }

        workerSession = fixture.login("tenant1_worker", "password");
    }

    @Test
    void adminKeyPagesRender() throws Exception {
        mockMvc.perform(get("/admin/pricing/products").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??"))))
            .andExpect(content().string(not(containsString(">BANNER</option>"))))
            .andExpect(content().string(not(containsString(">PER_SQM</option>"))))
            .andExpect(content().string(anyOf(
                containsString(">Banner</option>"),
                containsString(">Baner</option>")
            )));
        mockMvc.perform(get("/admin/pricing/products/" + variant.getProduct().getId()).session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??"))))
            .andExpect(content().string(anyOf(
                containsString("Akcije"),
                containsString("Actions")
            )))
            .andExpect(content().string(anyOf(
                containsString("Prevucite horizontalno"),
                containsString("Swipe horizontally")
            )))
            .andExpect(content().string(anyOf(
                containsString("Sačuvaj"),
                containsString("Save")
            )));
        mockMvc.perform(get("/admin/pricing/variants/" + variant.getId()).session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??"))))
            .andExpect(content().string(not(containsString(">PER_SQM</option>"))))
            .andExpect(content().string(anyOf(
                containsString("Add Component"),
                containsString("Dodaj komponentu")
            )))
            .andExpect(content().string(anyOf(
                containsString("Sačuvaj"),
                containsString("Save")
            )));
        mockMvc.perform(get("/pricing/calculate").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(anyOf(
                containsString("Width/Height are used for m"),
                containsString("Širina/visina se koriste za m")
            )));
        mockMvc.perform(get("/admin/orders").session(adminSession))
            .andExpect(status().isOk());
        mockMvc.perform(get("/admin/orders/" + tenantIds.workOrderId()).session(adminSession))
            .andExpect(status().isOk());
        mockMvc.perform(get("/admin/orders/" + tenantIds.workOrderId() + "/edit").session(adminSession))
            .andExpect(status().isOk());
        mockMvc.perform(get("/admin/orders/create").session(adminSession))
            .andExpect(status().isOk());
        mockMvc.perform(get("/admin/planner").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??"))))
            .andExpect(content().string(not(containsString(">LASER</option>"))))
            .andExpect(content().string(not(containsString(">OTHER</option>"))))
            .andExpect(content().string(anyOf(
                containsString("Swipe horizontally to see all planner columns."),
                containsString("Prevucite horizontalno da vidite sve kolone planera.")
            )))
            .andExpect(content().string(anyOf(
                containsString("Production Planner"),
                containsString("Planer proizvodnje")
            )));
        mockMvc.perform(get("/admin/rate-limit").session(adminSession))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/company").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??"))))
            .andExpect(content().string(containsString("SMTP")))
            .andExpect(content().string(anyOf(
                containsString("Source:"),
                containsString("Izvor:")
            )))
            .andExpect(content().string(anyOf(
                containsString("company@example.com"),
                containsString("kompanija@example.com")
            )))
            .andExpect(content().string(anyOf(
                containsString("SMTP OK"),
                containsString("SMTP NIJE PODEŠEN")
            )));
        mockMvc.perform(get("/settings/email").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??"))))
            .andExpect(content().string(containsString("smtp.example.com")))
            .andExpect(content().string(not(containsString(">PENDING</option>"))))
            .andExpect(content().string(not(containsString(">SENT</option>"))))
            .andExpect(content().string(not(containsString(">FAILED</option>"))))
            .andExpect(content().string(anyOf(
                containsString("Source:"),
                containsString("Izvor:")
            )))
            .andExpect(content().string(anyOf(
                containsString("Pending"),
                containsString("Na čekanju")
            )))
            .andExpect(content().string(anyOf(
                containsString("Swipe horizontally to see all outbox columns."),
                containsString("Prevucite horizontalno da vidite sve kolone outbox tabele.")
            )));
        mockMvc.perform(get("/notifications").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??"))))
            .andExpect(content().string(anyOf(
                containsString("data-notification-read-text=\"Read\""),
                containsString("data-notification-read-text=\"Pročitano\"")
            )))
            .andExpect(content().string(anyOf(
                containsString("data-notification-unread-text=\"Unread\""),
                containsString("data-notification-unread-text=\"Nepročitano\"")
            )));
        mockMvc.perform(get("/admin/dashboard").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/public/companies"))))
            .andExpect(content().string(containsString("ProPrintFlow")))
            .andExpect(content().string(containsString("/admin/pricing/products")))
            .andExpect(content().string(containsString("/pricing/calculate")));
        mockMvc.perform(get("/admin/users").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("??common."))))
            .andExpect(content().string(anyOf(
                containsString("Phone"),
                containsString("Telefon")
            )))
            .andExpect(content().string(anyOf(
                containsString("Active"),
                containsString("Aktivan")
            )));

        Long adminId = userRepository.findByUsername("tenant1_admin").orElseThrow().getId();
        mockMvc.perform(get("/admin/users/permissions/" + adminId).session(adminSession))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users/edit/" + adminId));
    }

    @Test
    void workerKeyPagesRender() throws Exception {
        mockMvc.perform(get("/worker/dashboard").session(workerSession))
            .andExpect(status().isOk());
        mockMvc.perform(get("/worker/my-tasks").session(workerSession))
            .andExpect(status().isOk());
    }

    @Test
    void publicPagesRender() throws Exception {
        String token = workOrderRepository.findById(tenantIds.workOrderId())
            .orElseThrow()
            .getPublicToken();
        mockMvc.perform(get("/public/track"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/public/order/" + token))
            .andExpect(status().isOk());
    }

    private UnitType pickAnyPieceUnit() {
        String[] preferred = {"PCS", "PC", "PIECE", "KOM", "KOMAD", "ITEM"};
        return Arrays.stream(UnitType.values())
            .filter(v -> Arrays.stream(preferred).anyMatch(p -> p.equalsIgnoreCase(v.name())))
            .findFirst()
            .orElse(UnitType.values()[0]);
    }

    private Role pickWorkerLikeRole() {
        String[] preferred = {"WORKER", "WORKER_GENERAL", "WORKER_PRINT", "WORKER_DESIGN", "EMPLOYEE", "STAFF", "PRODUCTION", "USER", "MANAGER", "ADMIN"};
        return Arrays.stream(Role.values())
            .filter(v -> Arrays.stream(preferred).anyMatch(p -> p.equalsIgnoreCase(v.name())))
            .findFirst()
            .orElse(Role.ADMIN);
    }
}
