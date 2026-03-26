package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.PublicOrderRequest;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.Client;
import com.printflow.entity.enums.PublicOrderRequestStatus;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.PublicOrderRequestRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicOrderRequestFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PublicOrderRequestRepository requestRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void publicSubmitCreatesRequestForCorrectTenant() throws Exception {
        Company company = createCompany("Print One", "print-one");

        mockMvc.perform(post("/p/" + company.getSlug() + "/order")
                .with(csrf())
                .param("customerName", "Pera Peric")
                .param("customerEmail", "pera@example.com")
                .param("productType", "Vizit karte")
                .param("quantity", "1000"))
            .andExpect(status().is3xxRedirection());

        PublicOrderRequest created = requestRepository.findAll().stream()
            .filter(r -> r.getCompany().getId().equals(company.getId()))
            .max(Comparator.comparing(PublicOrderRequest::getCreatedAt))
            .orElse(null);
        assertThat(created).isNotNull();
        assertThat(created.getCompany().getId()).isEqualTo(company.getId());
        assertThat(created.getStatus()).isEqualTo(PublicOrderRequestStatus.NEW);
    }

    @Test
    void publicSubmitPreservesLangOnSuccessRedirect() throws Exception {
        Company company = createCompany("Lang Redirect", "lang-redirect");

        mockMvc.perform(post("/p/" + company.getSlug() + "/order")
                .with(csrf())
                .param("lang", "en")
                .param("customerName", "John Doe")
                .param("customerEmail", "john@example.com")
                .param("productType", "Flyers")
                .param("quantity", "100"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/p/" + company.getSlug() + "/order/success?lang=en"));
    }

    @Test
    void companyIdEntryPointPreservesLangOnRedirectToSlug() throws Exception {
        Company company = createCompany("Lang Company", "lang-company");

        mockMvc.perform(get("/p/company/" + company.getId() + "/order")
                .param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/p/" + company.getSlug() + "/order?lang=en"));
    }

    @Test
    void publicSubmitNormalizesUnsupportedLangToSr() throws Exception {
        Company company = createCompany("Lang Normalize", "lang-normalize");

        mockMvc.perform(post("/p/" + company.getSlug() + "/order")
                .with(csrf())
                .param("lang", "de")
                .param("customerName", "Lang Test")
                .param("customerEmail", "lang@example.com")
                .param("productType", "Flyers")
                .param("quantity", "100"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/p/" + company.getSlug() + "/order/success?lang=sr"));
    }

    @Test
    void companyIdEntryPointNormalizesUnsupportedLangToSr() throws Exception {
        Company company = createCompany("Lang Company Normalize", "lang-company-normalize");

        mockMvc.perform(get("/p/company/" + company.getId() + "/order")
                .param("lang", "de"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/p/" + company.getSlug() + "/order?lang=sr"));
    }

    @Test
    void adminSeesOnlyOwnTenantRequests() throws Exception {
        Company companyA = createCompany("Company A", "company-a");
        Company companyB = createCompany("Company B", "company-b");
        createAdmin(companyA, "adminA");
        createAdmin(companyB, "adminB");

        submitPublic(companyA.getSlug(), "a@example.com");
        submitPublic(companyB.getSlug(), "b@example.com");

        MockHttpSession adminASession = login("adminA", "password");

        MvcResult result = mockMvc.perform(get("/admin/public-requests").session(adminASession))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("a@example.com");
        assertThat(body).doesNotContain("b@example.com");
    }

    @Test
    void convertCreatesRegularOrder() throws Exception {
        Company company = createCompany("Convert Co", "convert-co");
        createAdmin(company, "convertAdmin");
        submitPublic(company.getSlug(), "client@convert.co");
        PublicOrderRequest request = findRequest(company.getId(), "client@convert.co");

        MockHttpSession session = login("convertAdmin", "password");
        mockMvc.perform(post("/admin/public-requests/" + request.getId() + "/convert")
                .session(session)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/admin/orders/*"));

        PublicOrderRequest converted = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(converted.getStatus()).isEqualTo(PublicOrderRequestStatus.CONVERTED);
        assertThat(converted.getConvertedOrder()).isNotNull();
        assertThat(workOrderRepository.countByCompany_Id(company.getId())).isEqualTo(1);
    }

    @Test
    void convertCannotRunTwiceForSameRequest() throws Exception {
        Company company = createCompany("Convert Twice", "convert-twice");
        createAdmin(company, "convertTwiceAdmin");
        submitPublic(company.getSlug(), "twice@convert.co");
        PublicOrderRequest request = findRequest(company.getId(), "twice@convert.co");

        MockHttpSession session = login("convertTwiceAdmin", "password");
        mockMvc.perform(post("/admin/public-requests/" + request.getId() + "/convert")
                .session(session)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/public-requests/" + request.getId() + "/convert")
                .session(session)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(workOrderRepository.countByCompany_Id(company.getId())).isEqualTo(1);
    }

    @Test
    void invalidEmailFailsValidation() throws Exception {
        Company company = createCompany("Validation Co", "validation-co");
        MvcResult result = mockMvc.perform(post("/p/" + company.getSlug() + "/order")
                .with(csrf())
                .param("customerName", "Bad Email")
                .param("customerEmail", "not-an-email")
                .param("productType", "Flyers")
                .param("quantity", "5"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("Email nije ispravan");
    }

    @Test
    void quantityLessThanOneFailsValidation() throws Exception {
        Company company = createCompany("Validation Qty", "validation-qty");
        MvcResult result = mockMvc.perform(post("/p/" + company.getSlug() + "/order")
                .with(csrf())
                .param("customerName", "Bad Qty")
                .param("customerEmail", "ok@example.com")
                .param("productType", "Flyers")
                .param("quantity", "0"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("Količina mora biti veća od 0");
    }

    @Test
    void deadlineInPastFailsValidation() throws Exception {
        Company company = createCompany("Validation Deadline", "validation-deadline");
        MvcResult result = mockMvc.perform(post("/p/" + company.getSlug() + "/order")
                .with(csrf())
                .param("customerName", "Past Deadline")
                .param("customerEmail", "deadline@example.com")
                .param("productType", "Flyers")
                .param("quantity", "100")
                .param("deadline", LocalDateTime.now().minusHours(2).withNano(0).toString()))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("Rok mora biti u budu");
    }

    @Test
    void productTypeTooLongFailsValidation() throws Exception {
        Company company = createCompany("Validation ProductType", "validation-product-type");
        String tooLongProductType = "x".repeat(151);
        MvcResult result = mockMvc.perform(post("/p/" + company.getSlug() + "/order")
                .with(csrf())
                .param("customerName", "Long Product Type")
                .param("customerEmail", "length@example.com")
                .param("productType", tooLongProductType)
                .param("quantity", "100"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("Tip proizvoda može imati najviše 150 karaktera");
    }

    @Test
    void convertReusesExistingCustomerInSameTenant() throws Exception {
        Company company = createCompany("Reuse Client", "reuse-client");
        createAdmin(company, "reuseAdmin");
        Client existing = new Client();
        existing.setCompany(company);
        existing.setCompanyName("Existing Co");
        existing.setContactPerson("Existing");
        existing.setEmail("same@mail.com");
        existing.setPhone("060111222");
        existing = clientRepository.save(existing);

        submitPublic(company.getSlug(), "same@mail.com");
        PublicOrderRequest request = findRequest(company.getId(), "same@mail.com");
        MockHttpSession session = login("reuseAdmin", "password");
        mockMvc.perform(post("/admin/public-requests/" + request.getId() + "/convert")
                .session(session)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        PublicOrderRequest converted = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(converted.getConvertedOrder()).isNotNull();
        Long orderId = converted.getConvertedOrder().getId();
        Long clientId = workOrderRepository.findById(orderId).orElseThrow().getClient().getId();
        assertThat(clientId).isEqualTo(existing.getId());
    }

    @Test
    void customerFromOtherTenantIsNeverLinked() throws Exception {
        Company companyA = createCompany("Tenant A", "tenant-a-flow");
        Company companyB = createCompany("Tenant B", "tenant-b-flow");
        createAdmin(companyA, "tenantAAdmin");

        Client foreign = new Client();
        foreign.setCompany(companyB);
        foreign.setCompanyName("Foreign Client");
        foreign.setContactPerson("Foreign");
        foreign.setEmail("foreign@mail.com");
        foreign.setPhone("060999888");
        clientRepository.save(foreign);

        submitPublic(companyA.getSlug(), "foreign@mail.com");
        PublicOrderRequest request = requestRepository.findAll().stream()
            .filter(r -> r.getCompany().getId().equals(companyA.getId()))
            .findFirst()
            .orElseThrow();

        MockHttpSession session = login("tenantAAdmin", "password");
        mockMvc.perform(post("/admin/public-requests/" + request.getId() + "/convert")
                .session(session)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        PublicOrderRequest converted = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(converted.getConvertedOrder()).isNotNull();
        Long orderId = converted.getConvertedOrder().getId();
        Long orderCompanyId = workOrderRepository.findById(orderId).orElseThrow().getCompany().getId();
        assertThat(orderCompanyId).isEqualTo(companyA.getId());
    }

    @Test
    void invalidFileExtensionShowsFileSectionError() throws Exception {
        Company company = createCompany("Validation File", "validation-file");
        MockMultipartFile invalidFile = new MockMultipartFile(
            "files",
            "payload.exe",
            "application/octet-stream",
            "dummy".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/p/" + company.getSlug() + "/order")
                .file(invalidFile)
                .with(csrf())
                .param("customerName", "Bad File")
                .param("customerEmail", "badfile@example.com")
                .param("productType", "Flyers")
                .param("quantity", "100"))
            .andExpect(status().isOk())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).contains("Nedozvoljen tip fajla");
    }

    private void submitPublic(String slug, String email) throws Exception {
        mockMvc.perform(post("/p/" + slug + "/order")
                .with(csrf())
                .param("customerName", "Public Customer")
                .param("customerEmail", email)
                .param("customerPhone", "060123123")
                .param("customerCompanyName", "Client Co")
                .param("productType", "Flyers")
                .param("quantity", "500"))
            .andExpect(status().is3xxRedirection());
    }

    private Company createCompany(String name, String slug) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = new Company();
        company.setName(name + "-" + uniqueSuffix);
        company.setSlug(slug + "-" + uniqueSuffix);
        company.setActive(true);
        company.setCurrency("RSD");
        company.setTrialStart(LocalDateTime.now().minusDays(1));
        company.setTrialEnd(LocalDateTime.now().plusDays(30));
        return companyRepository.save(company);
    }

    private PublicOrderRequest findRequest(Long companyId, String customerEmail) {
        return requestRepository.findAll().stream()
            .filter(r -> r.getCompany().getId().equals(companyId))
            .filter(r -> customerEmail.equalsIgnoreCase(r.getCustomerEmail()))
            .max(Comparator.comparing(PublicOrderRequest::getCreatedAt))
            .orElseThrow();
    }

    private void createAdmin(Company company, String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.ADMIN);
        user.setCompany(company);
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setFullName("Admin User");
        userRepository.save(user);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/login").user(username).password(password))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
