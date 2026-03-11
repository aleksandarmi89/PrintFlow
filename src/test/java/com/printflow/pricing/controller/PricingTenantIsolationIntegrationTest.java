package com.printflow.pricing.controller;

import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PricingTenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private PricingComponentRepository componentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pricingCalculateRequiresCsrfToken() throws Exception {
        Company company = createCompany("Company Csrf");
        createUser(company, "tenantCsrf", "password");

        Product product = createProduct(company, "Sticker", ProductCategory.OTHER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Sticker Basic");
        createComponent(company, variant, new BigDecimal("1.00"));

        MockHttpSession session = login("tenantCsrf", "password");

        String payload = "{" +
            "\"variantId\":" + variant.getId() + "," +
            "\"quantity\":10" +
            "}";

        mockMvc.perform(post("/api/pricing/calculate")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());
    }

    @Test
    void tenantCannotCalculateOtherCompanyVariant() throws Exception {
        Company companyA = createCompany("Company A");
        Company companyB = createCompany("Company B");
        createUser(companyA, "tenantA", "password");

        Product productB = createProduct(companyB, "Banner", ProductCategory.BANNER, UnitType.SQM);
        ProductVariant variantB = createVariant(companyB, productB, "Banner 510");

        MockHttpSession sessionA = login("tenantA", "password");

        String payload = "{" +
            "\"variantId\":" + variantB.getId() + "," +
            "\"quantity\":10," +
            "\"widthMm\":1000," +
            "\"heightMm\":1000" +
            "}";

        mockMvc.perform(post("/api/pricing/calculate")
                .session(sessionA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isNotFound());
    }

    @Test
    void pricingCalculateReturnsExpectedSchema() throws Exception {
        Company company = createCompany("Company Schema");
        createUser(company, "tenantSchema", "password");

        Product product = createProduct(company, "Poster", ProductCategory.PAPER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Poster A2");
        createComponent(company, variant, new BigDecimal("2.00"));

        MockHttpSession session = login("tenantSchema", "password");

        String payload = "{" +
            "\"variantId\":" + variant.getId() + "," +
            "\"quantity\":10" +
            "}";

        MvcResult result = mockMvc.perform(post("/api/pricing/calculate")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("RSD"))
            .andExpect(jsonPath("$.totalCost").exists())
            .andExpect(jsonPath("$.totalPrice").exists())
            .andExpect(jsonPath("$.profitAmount").exists())
            .andExpect(jsonPath("$.marginPercent").exists())
            .andExpect(jsonPath("$.markupPercent").exists())
            .andExpect(jsonPath("$.breakdown").isArray())
            .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> breakdown = extractBreakdownRows(response.get("breakdown"));
        BigDecimal sum = BigDecimal.ZERO;
        if (breakdown != null) {
            for (Map<String, Object> row : breakdown) {
                Object lineCost = row.get("lineCost");
                if (lineCost != null) {
                    sum = sum.add(new BigDecimal(lineCost.toString()));
                }
            }
        }
        BigDecimal totalCost = new BigDecimal(response.get("totalCost").toString());
        org.assertj.core.api.Assertions.assertThat(sum).isEqualByComparingTo(totalCost);
    }

    private List<Map<String, Object>> extractBreakdownRows(Object rawBreakdown) {
        if (!(rawBreakdown instanceof List<?> rows)) {
            return List.of();
        }
        return rows.stream()
            .filter(Map.class::isInstance)
            .map(row -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) row;
                return typed;
            })
            .toList();
    }

    @Test
    void tenantCannotAddItemToOtherCompanyWorkOrder() throws Exception {
        Company companyA = createCompany("Company A2");
        Company companyB = createCompany("Company B2");
        createUser(companyA, "tenantA2", "password");

        Client clientB = new Client();
        clientB.setCompanyName("Client B");
        clientB.setPhone("123");
        clientB.setCompany(companyB);
        clientB = clientRepository.save(clientB);

        WorkOrder workOrderB = new WorkOrder();
        workOrderB.setOrderNumber("WO-2000");
        workOrderB.setTitle("Order B");
        workOrderB.setStatus(OrderStatus.NEW);
        workOrderB.setClient(clientB);
        workOrderB.setCompany(companyB);
        workOrderB = workOrderRepository.save(workOrderB);

        Product productA = createProduct(companyA, "Mug", ProductCategory.MUG, UnitType.PIECE);
        ProductVariant variantA = createVariant(companyA, productA, "White Mug");

        MockHttpSession sessionA = login("tenantA2", "password");

        String payload = "{" +
            "\"variantId\":" + variantA.getId() + "," +
            "\"quantity\":5" +
            "}";

        mockMvc.perform(post("/api/workorders/" + workOrderB.getId() + "/items")
                .session(sessionA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isNotFound());
    }

    private Company createCompany(String name) {
        Company company = new Company();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        company.setName(name + "-" + uniqueSuffix);
        company.setSlug("test-" + uniqueSuffix);
        company.setActive(true);
        company.setTrialStart(LocalDateTime.now().minusDays(1));
        company.setTrialEnd(LocalDateTime.now().plusDays(30));
        return companyRepository.save(company);
    }

    private void createUser(Company company, String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setCompany(company);
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setFullName("Admin User");
        userRepository.save(user);
    }

    private Product createProduct(Company company, String name, ProductCategory category, UnitType unitType) {
        Product product = new Product();
        product.setCompany(company);
        product.setName(name);
        product.setCategory(category);
        product.setUnitType(unitType);
        return productRepository.save(product);
    }

    private ProductVariant createVariant(Company company, Product product, String name) {
        ProductVariant variant = new ProductVariant();
        variant.setCompany(company);
        variant.setProduct(product);
        variant.setName(name);
        variant.setDefaultMarkupPercent(new BigDecimal("20.00"));
        return variantRepository.save(variant);
    }
    
    private void createComponent(Company company, ProductVariant variant, BigDecimal amount) {
        PricingComponent component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(variant);
        component.setType(com.printflow.entity.enums.PricingComponentType.MATERIAL);
        component.setModel(com.printflow.entity.enums.PricingModel.PER_UNIT);
        component.setAmount(amount);
        componentRepository.save(component);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/login").user(username).password(password))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
