package com.printflow.pricing.controller;

import com.printflow.entity.Company;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.testsupport.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PricingAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private PricingComponentRepository componentRepository;

    @Autowired
    private com.printflow.repository.UserRepository userRepository;

    @Autowired
    private com.printflow.repository.ClientRepository clientRepository;

    @Autowired
    private com.printflow.repository.WorkOrderRepository workOrderRepository;

    @Autowired
    private com.printflow.repository.TaskRepository taskRepository;

    @Autowired
    private com.printflow.repository.AttachmentRepository attachmentRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private TenantTestFixture.TenantIds ids;
    private TenantTestFixture fixture;

    private ProductVariant variant;
    private PricingComponent component;

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    private void setupPricingData() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        ids = fixture.createTenantData();
        Company company = companyRepository.findById(ids.company1Id()).orElseThrow();

        Product product = new Product();
        product.setCompany(company);
        product.setName("Banner");
        product.setCategory(com.printflow.entity.enums.ProductCategory.BANNER);
        product.setUnitType(com.printflow.entity.enums.UnitType.SQM);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setCompany(company);
        variant.setProduct(product);
        variant.setName("Banner 510");
        variant.setDefaultMarkupPercent(new BigDecimal("20.00"));
        variant.setWastePercent(new BigDecimal("2.00"));
        variant = variantRepository.save(variant);

        component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(variant);
        component.setType(com.printflow.entity.enums.PricingComponentType.MATERIAL);
        component.setModel(com.printflow.entity.enums.PricingModel.PER_SQM);
        component.setAmount(new BigDecimal("2.00"));
        component = componentRepository.save(component);
    }

    @Test
    void updateVariantPricingIsTenantScoped() throws Exception {
        setupPricingData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        MockHttpSession tenant2 = fixture.login("tenant2_admin", "password");

        mockMvc.perform(post("/admin/pricing/variants/{id}", variant.getId())
                .session(tenant1)
                .header("HX-Request", "true")
                .with(csrf())
                .param("defaultMarkupPercent", "30.00")
                .param("wastePercent", "5.00")
                .param("minPrice", "10.00"))
            .andExpect(status().isOk());

        ProductVariant updated = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(updated.getDefaultMarkupPercent()).isEqualByComparingTo("30.00");
        assertThat(updated.getWastePercent()).isEqualByComparingTo("5.00");

        mockMvc.perform(post("/admin/pricing/variants/{id}", variant.getId())
                .session(tenant2)
                .header("HX-Request", "true")
                .with(csrf())
                .param("defaultMarkupPercent", "99.00")
                .param("wastePercent", "5.00")
                .param("minPrice", "10.00"))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateComponentAmountIsTenantScoped() throws Exception {
        setupPricingData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        MockHttpSession tenant2 = fixture.login("tenant2_admin", "password");

        mockMvc.perform(post("/admin/pricing/components/{id}", component.getId())
                .session(tenant1)
                .header("HX-Request", "true")
                .with(csrf())
                .param("amount", "7.50"))
            .andExpect(status().isOk());

        PricingComponent updated = componentRepository.findById(component.getId()).orElseThrow();
        assertThat(updated.getAmount()).isEqualByComparingTo("7.50");

        mockMvc.perform(post("/admin/pricing/components/{id}", component.getId())
                .session(tenant2)
                .header("HX-Request", "true")
                .with(csrf())
                .param("amount", "9.00"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteVariantAndProductAreTenantScoped() throws Exception {
        setupPricingData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        MockHttpSession tenant2 = fixture.login("tenant2_admin", "password");

        mockMvc.perform(post("/admin/pricing/variants/{id}/delete", variant.getId())
                .session(tenant2)
                .with(csrf()))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/pricing/variants/{id}/delete", variant.getId())
                .session(tenant1)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(variantRepository.findById(variant.getId())).isEmpty();

        Product product = productRepository.findById(variant.getProduct().getId()).orElseThrow();
        mockMvc.perform(post("/admin/pricing/products/{id}/delete", product.getId())
                .session(tenant1)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(product.getId())).isEmpty();
    }

    @Test
    void deleteComponentIsTenantScoped() throws Exception {
        setupPricingData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        MockHttpSession tenant2 = fixture.login("tenant2_admin", "password");

        mockMvc.perform(post("/admin/pricing/variants/{variantId}/components/{componentId}/delete", variant.getId(), component.getId())
                .session(tenant2)
                .with(csrf()))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/pricing/variants/{variantId}/components/{componentId}/delete", variant.getId(), component.getId())
                .session(tenant1)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(componentRepository.findById(component.getId())).isEmpty();
    }

    @Test
    void deleteComponentRequiresMatchingVariantPath() throws Exception {
        setupPricingData();
        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        Company company = companyRepository.findById(ids.company1Id()).orElseThrow();

        ProductVariant otherVariant = new ProductVariant();
        otherVariant.setCompany(company);
        otherVariant.setProduct(variant.getProduct());
        otherVariant.setName("Other Variant");
        otherVariant = variantRepository.save(otherVariant);

        mockMvc.perform(post("/admin/pricing/variants/{variantId}/components/{componentId}/delete",
                otherVariant.getId(), component.getId())
                .session(tenant1)
                .with(csrf()))
            .andExpect(status().isNotFound());

        assertThat(componentRepository.findById(component.getId())).isPresent();
    }
}
