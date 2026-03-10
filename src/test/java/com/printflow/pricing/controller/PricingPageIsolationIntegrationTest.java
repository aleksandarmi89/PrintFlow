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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PricingPageIsolationIntegrationTest {

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
    private TenantTestFixture.TenantIds ids;

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void deleteComponentRequiresMatchingVariantPath() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        ids = fixture.createTenantData();
        Company company = companyRepository.findById(ids.company1Id()).orElseThrow();

        Product product = new Product();
        product.setCompany(company);
        product.setName("Poster");
        product.setCategory(com.printflow.entity.enums.ProductCategory.OTHER);
        product.setUnitType(com.printflow.entity.enums.UnitType.SQM);
        product = productRepository.save(product);

        ProductVariant primaryVariant = new ProductVariant();
        primaryVariant.setCompany(company);
        primaryVariant.setProduct(product);
        primaryVariant.setName("Poster Main");
        primaryVariant = variantRepository.save(primaryVariant);

        ProductVariant otherVariant = new ProductVariant();
        otherVariant.setCompany(company);
        otherVariant.setProduct(product);
        otherVariant.setName("Poster Other");
        otherVariant = variantRepository.save(otherVariant);

        PricingComponent component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(primaryVariant);
        component.setType(com.printflow.entity.enums.PricingComponentType.MATERIAL);
        component.setModel(com.printflow.entity.enums.PricingModel.FIXED);
        component.setAmount(new BigDecimal("5.00"));
        component = componentRepository.save(component);

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/pricing/variants/{variantId}/pricing/{componentId}/delete",
                otherVariant.getId(), component.getId())
                .session(tenant1)
                .with(csrf()))
            .andExpect(status().isNotFound());

        assertThat(componentRepository.findById(component.getId())).isPresent();
    }
}
