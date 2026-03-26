package com.printflow.pricing.controller;

import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
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
import com.printflow.repository.WorkOrderItemRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;

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
    private WorkOrderItemRepository workOrderItemRepository;

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
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.messageKey").value("api.error.not_found"));
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
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.messageKey").value("api.error.not_found"));
    }

    @Test
    void tenantCannotDeleteOtherCompanyWorkOrderItem() throws Exception {
        Company companyA = createCompany("Company A3");
        Company companyB = createCompany("Company B3");
        createUser(companyA, "tenantA3", "password");

        Client clientB = new Client();
        clientB.setCompanyName("Client B3");
        clientB.setPhone("123");
        clientB.setCompany(companyB);
        clientB = clientRepository.save(clientB);

        WorkOrder workOrderB = new WorkOrder();
        workOrderB.setOrderNumber("WO-3000");
        workOrderB.setTitle("Order B3");
        workOrderB.setStatus(OrderStatus.NEW);
        workOrderB.setClient(clientB);
        workOrderB.setCompany(companyB);
        workOrderB = workOrderRepository.save(workOrderB);

        Product productB = createProduct(companyB, "Poster B3", ProductCategory.PAPER, UnitType.PIECE);
        ProductVariant variantB = createVariant(companyB, productB, "Poster B3 Variant");

        WorkOrderItem itemB = new WorkOrderItem();
        itemB.setCompany(companyB);
        itemB.setWorkOrder(workOrderB);
        itemB.setVariant(variantB);
        itemB.setQuantity(new BigDecimal("10.00"));
        itemB.setCalculatedCost(new BigDecimal("100.00"));
        itemB.setCalculatedPrice(new BigDecimal("120.00"));
        itemB.setProfitAmount(new BigDecimal("20.00"));
        itemB.setMarginPercent(new BigDecimal("16.67"));
        itemB.setCurrency("RSD");
        itemB.setBreakdownJson("{\"breakdown\":[]}");
        itemB.setPricingSnapshotJson("{}");
        itemB.setPriceCalculatedAt(LocalDateTime.now());
        itemB = workOrderItemRepository.save(itemB);

        MockHttpSession sessionA = login("tenantA3", "password");

        mockMvc.perform(delete("/api/workorders/" + workOrderB.getId() + "/items/" + itemB.getId())
                .session(sessionA)
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.messageKey").value("api.error.not_found"));
    }

    @Test
    void addItemSyncsWorkOrderTotalsFromCalculatedItems() throws Exception {
        Company company = createCompany("Company Totals Sync");
        createUser(company, "tenantTotalsSync", "password");

        Client client = new Client();
        client.setCompanyName("Client Totals");
        client.setPhone("123");
        client.setCompany(company);
        client = clientRepository.save(client);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber("WO-3200");
        workOrder.setTitle("Order Totals Sync");
        workOrder.setStatus(OrderStatus.NEW);
        workOrder.setClient(client);
        workOrder.setCompany(company);
        workOrder.setPrice(1.0); // stale value to ensure sync overwrites it
        workOrder.setCost(1.0);
        workOrder = workOrderRepository.save(workOrder);

        Product product = createProduct(company, "Poster Totals", ProductCategory.PAPER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Poster Totals Variant");
        createComponent(company, variant, new BigDecimal("2.00")); // qty 10 => cost 20, price 24

        MockHttpSession session = login("tenantTotalsSync", "password");

        String payload = "{" +
            "\"variantId\":" + variant.getId() + "," +
            "\"quantity\":10" +
            "}";

        mockMvc.perform(post("/api/workorders/" + workOrder.getId() + "/items")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workOrderId").value(workOrder.getId()))
            .andExpect(jsonPath("$.workOrderTotalCost").value(20.0))
            .andExpect(jsonPath("$.workOrderTotalPrice").value(24.0))
            .andExpect(jsonPath("$.currency").value("RSD"));

        WorkOrder refreshed = workOrderRepository.findById(workOrder.getId()).orElseThrow();
        assertThat(refreshed.getCost()).isEqualTo(20.0);
        assertThat(refreshed.getPrice()).isEqualTo(24.0);
    }

    @Test
    void addItemFailsWhenVariantRequiresDimensionsAndDimensionsAreMissing() throws Exception {
        Company company = createCompany("Company Dim Validation");
        createUser(company, "tenantDimValidation", "password");

        Client client = new Client();
        client.setCompanyName("Client Dim Validation");
        client.setPhone("123");
        client.setCompany(company);
        client = clientRepository.save(client);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber("WO-3300");
        workOrder.setTitle("Order Dim Validation");
        workOrder.setStatus(OrderStatus.NEW);
        workOrder.setClient(client);
        workOrder.setCompany(company);
        workOrder = workOrderRepository.save(workOrder);

        Product product = createProduct(company, "Poster Area", ProductCategory.PAPER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Poster Area Variant");
        PricingComponent component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(variant);
        component.setType(com.printflow.entity.enums.PricingComponentType.MATERIAL);
        component.setModel(com.printflow.entity.enums.PricingModel.PER_SQM);
        component.setAmount(new BigDecimal("2.00"));
        componentRepository.save(component);

        MockHttpSession session = login("tenantDimValidation", "password");

        String payload = "{" +
            "\"variantId\":" + variant.getId() + "," +
            "\"quantity\":10" +
            "}";

        mockMvc.perform(post("/api/workorders/" + workOrder.getId() + "/items")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("pricing.calculate.validation.dimensions_required"))
            .andExpect(jsonPath("$.messageKey").value("pricing.calculate.validation.dimensions_required"));
    }

    @Test
    void addItemFailsWhenVariantRequiresMetersAndMetersAreMissing() throws Exception {
        Company company = createCompany("Company Meters Validation");
        createUser(company, "tenantMetersValidation", "password");

        Client client = new Client();
        client.setCompanyName("Client Meters Validation");
        client.setPhone("123");
        client.setCompany(company);
        client = clientRepository.save(client);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber("WO-3400");
        workOrder.setTitle("Order Meters Validation");
        workOrder.setStatus(OrderStatus.NEW);
        workOrder.setClient(client);
        workOrder.setCompany(company);
        workOrder = workOrderRepository.save(workOrder);

        Product product = createProduct(company, "Vinyl Roll", ProductCategory.OTHER, UnitType.METER);
        ProductVariant variant = createVariant(company, product, "Vinyl Roll Variant");
        PricingComponent component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(variant);
        component.setType(com.printflow.entity.enums.PricingComponentType.MATERIAL);
        component.setModel(com.printflow.entity.enums.PricingModel.PER_METER);
        component.setAmount(new BigDecimal("3.00"));
        componentRepository.save(component);

        MockHttpSession session = login("tenantMetersValidation", "password");

        String payload = "{" +
            "\"variantId\":" + variant.getId() + "," +
            "\"quantity\":10," +
            "\"attributes\":{}" +
            "}";

        mockMvc.perform(post("/api/workorders/" + workOrder.getId() + "/items")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("pricing.calculate.validation.meters_required"))
            .andExpect(jsonPath("$.messageKey").value("pricing.calculate.validation.meters_required"));
    }

    @Test
    void addItemFailsWhenVariantRequiresMinutesAndMinutesAreMissing() throws Exception {
        Company company = createCompany("Company Minutes Validation");
        createUser(company, "tenantMinutesValidation", "password");

        Client client = new Client();
        client.setCompanyName("Client Minutes Validation");
        client.setPhone("123");
        client.setCompany(company);
        client = clientRepository.save(client);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber("WO-3500");
        workOrder.setTitle("Order Minutes Validation");
        workOrder.setStatus(OrderStatus.NEW);
        workOrder.setClient(client);
        workOrder.setCompany(company);
        workOrder = workOrderRepository.save(workOrder);

        Product product = createProduct(company, "Design Service", ProductCategory.OTHER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Design Service Variant");
        PricingComponent component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(variant);
        component.setType(com.printflow.entity.enums.PricingComponentType.SETUP);
        component.setModel(com.printflow.entity.enums.PricingModel.PER_MINUTE);
        component.setAmount(new BigDecimal("1.00"));
        componentRepository.save(component);

        MockHttpSession session = login("tenantMinutesValidation", "password");

        String payload = "{" +
            "\"variantId\":" + variant.getId() + "," +
            "\"quantity\":1," +
            "\"attributes\":{}" +
            "}";

        mockMvc.perform(post("/api/workorders/" + workOrder.getId() + "/items")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("pricing.calculate.validation.minutes_required"))
            .andExpect(jsonPath("$.messageKey").value("pricing.calculate.validation.minutes_required"));
    }

    @Test
    void tenantCanDeleteOwnWorkOrderItem() throws Exception {
        Company company = createCompany("Company Own Delete");
        createUser(company, "tenantOwnDelete", "password");

        Client client = new Client();
        client.setCompanyName("Client Own Delete");
        client.setPhone("123");
        client.setCompany(company);
        client = clientRepository.save(client);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber("WO-3100");
        workOrder.setTitle("Order Own Delete");
        workOrder.setStatus(OrderStatus.NEW);
        workOrder.setClient(client);
        workOrder.setCompany(company);
        workOrder = workOrderRepository.save(workOrder);

        Product product = createProduct(company, "Flyer Delete", ProductCategory.PAPER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Flyer Delete Variant");

        WorkOrderItem item = new WorkOrderItem();
        item.setCompany(company);
        item.setWorkOrder(workOrder);
        item.setVariant(variant);
        item.setQuantity(new BigDecimal("5.00"));
        item.setCalculatedCost(new BigDecimal("50.00"));
        item.setCalculatedPrice(new BigDecimal("65.00"));
        item.setProfitAmount(new BigDecimal("15.00"));
        item.setMarginPercent(new BigDecimal("23.08"));
        item.setCurrency("RSD");
        item.setBreakdownJson("{\"breakdown\":[]}");
        item.setPricingSnapshotJson("{}");
        item.setPriceCalculatedAt(LocalDateTime.now());
        item = workOrderItemRepository.save(item);

        MockHttpSession session = login("tenantOwnDelete", "password");

        mockMvc.perform(delete("/api/workorders/" + workOrder.getId() + "/items/" + item.getId())
                .session(session)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.workOrderTotalCost").value(0.0))
            .andExpect(jsonPath("$.workOrderTotalPrice").value(0.0))
            .andExpect(jsonPath("$.currency").value("RSD"));

        org.assertj.core.api.Assertions.assertThat(
            workOrderItemRepository.findByIdAndCompany_Id(item.getId(), company.getId())
        ).isEmpty();
        WorkOrder reloaded = workOrderRepository.findById(workOrder.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getPrice()).isEqualTo(0.0);
        org.assertj.core.api.Assertions.assertThat(reloaded.getCost()).isEqualTo(0.0);
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
