package com.printflow.pricing.controller;

import com.printflow.entity.Company;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.testsupport.TenantTestFixture;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductAdminApiIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private PricingComponentRepository componentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private TenantTestFixture fixture;

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void tenantCannotListVariantsForOtherTenantProduct() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company2 = companyRepository.findById(ids.company2Id()).orElseThrow();

        Product product2 = new Product();
        product2.setCompany(company2);
        product2.setName("Tenant2 Product");
        product2 = productRepository.save(product2);

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/api/products/{productId}/variants", product2.getId()).session(tenant1))
            .andExpect(status().isNotFound());
    }

    @Test
    void tenantCannotListComponentsForOtherTenantVariant() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company2 = companyRepository.findById(ids.company2Id()).orElseThrow();

        Product product2 = new Product();
        product2.setCompany(company2);
        product2.setName("Tenant2 Product");
        product2 = productRepository.save(product2);

        ProductVariant variant2 = new ProductVariant();
        variant2.setCompany(company2);
        variant2.setProduct(product2);
        variant2.setName("Tenant2 Variant");
        variant2 = variantRepository.save(variant2);

        PricingComponent component2 = new PricingComponent();
        component2.setCompany(company2);
        component2.setVariant(variant2);
        component2.setType(com.printflow.entity.enums.PricingComponentType.MATERIAL);
        component2.setModel(com.printflow.entity.enums.PricingModel.FIXED);
        component2.setAmount(new BigDecimal("1.00"));
        componentRepository.save(component2);

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(get("/api/variants/{variantId}/components", variant2.getId()).session(tenant1))
            .andExpect(status().isNotFound());
    }
}
