package com.printflow.pricing.controller;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.ProductSource;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.repository.*;
import com.printflow.testsupport.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProductRepository productRepository;

    private TenantTestFixture fixture;

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void authenticatedTenantSeesOnlyOwnProducts() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company c1 = companyRepository.findById(ids.company1Id()).orElseThrow();
        Company c2 = companyRepository.findById(ids.company2Id()).orElseThrow();

        productRepository.save(product(c1, "Tenant1 Product", "SKU-T1"));
        productRepository.save(product(c2, "Tenant2 Product", "SKU-T2"));

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/products").session(tenant1))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tenant1 Product")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Tenant2 Product"))));
    }

    @Test
    void tenantCannotEditOrDeleteOtherTenantProduct() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company c1 = companyRepository.findById(ids.company1Id()).orElseThrow();
        Company c2 = companyRepository.findById(ids.company2Id()).orElseThrow();
        Product p2 = productRepository.save(product(c2, "Tenant2 Product", "SKU-T2"));

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/products/{id}/edit", p2.getId()).session(tenant1))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/products/{id}/delete", p2.getId()).with(csrf()).session(tenant1))
            .andExpect(status().isNotFound());

        assertThat(productRepository.findByIdAndCompany_Id(p2.getId(), c2.getId())).isPresent();
        assertThat(productRepository.findByIdAndCompany_Id(p2.getId(), c1.getId())).isEmpty();
    }

    @Test
    void createFormValidationWorks() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        fixture.createTenantData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");

        mockMvc.perform(post("/products")
                .with(csrf())
                .session(tenant1)
                .param("name", "")
                .param("basePrice", "-1"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Name is required")));
    }

    @Test
    void importEndpointProcessesCsvForCurrentTenantOnly() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company c1 = companyRepository.findById(ids.company1Id()).orElseThrow();
        Company c2 = companyRepository.findById(ids.company2Id()).orElseThrow();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");

        String csv = "name,sku,description,category,unit,basePrice,currency,active,externalId\n" +
            "Banner X,SKU-IMP,,Banner,m2,120.50,RSD,true,EXT-1\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/products/import")
                .file(file)
                .param("mode", "ADD_NEW_ONLY")
                .with(csrf())
                .session(tenant1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/products/import"));

        assertThat(productRepository.findByCompany_IdAndSkuIgnoreCase(c1.getId(), "SKU-IMP")).isPresent();
        assertThat(productRepository.findByCompany_IdAndSkuIgnoreCase(c2.getId(), "SKU-IMP")).isEmpty();
    }

    @Test
    void syncEndpointRedirectsToSettingsWhenProviderIsNotConfigured() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        fixture.createTenantData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");

        mockMvc.perform(post("/products/sync")
                .with(csrf())
                .session(tenant1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/products/sync/settings"))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void syncSettingsCanBeSavedForCurrentTenant() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        fixture.createTenantData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");

        mockMvc.perform(post("/products/sync/settings")
                .with(csrf())
                .session(tenant1)
                .param("enabled", "true")
                .param("endpointUrl", "https://example.com/api/products")
                .param("authType", "NONE")
                .param("payloadRoot", "items")
                .param("connectTimeoutMs", "9000")
                .param("readTimeoutMs", "16000"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/products/sync/settings"))
            .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(get("/products/sync/settings").session(tenant1))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.com/api/products")));
    }

    @Test
    void testConnectionEndpointRedirectsToSettings() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        fixture.createTenantData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");

        mockMvc.perform(post("/products/sync/settings/test")
                .with(csrf())
                .session(tenant1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/products/sync/settings"));
    }

    @Test
    void syncPricingEndpointRunsForCurrentTenant() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        fixture.createTenantData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");

        mockMvc.perform(post("/products/sync-pricing")
                .with(csrf())
                .session(tenant1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/products"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    private Product product(Company company, String name, String sku) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setSku(sku + "-" + UUID.randomUUID().toString().substring(0, 8));
        p.setBasePrice(new BigDecimal("10.00"));
        p.setCurrency("RSD");
        p.setSource(ProductSource.MANUAL);
        p.setCategory(ProductCategory.OTHER);
        p.setUnitType(UnitType.PIECE);
        p.setActive(true);
        return p;
    }
}
