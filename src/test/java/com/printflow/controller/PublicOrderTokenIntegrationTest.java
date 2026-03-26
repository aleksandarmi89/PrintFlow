package com.printflow.controller;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.QuoteStatus;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.testsupport.TenantTestFixture;
import com.printflow.testsupport.TenantTestFixture.TenantIds;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class PublicOrderTokenIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private WorkOrderItemRepository workOrderItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
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
    void expiredPublicTokenIsRejected() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("expired-token");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusDays(5));
        order.setPublicTokenExpiresAt(LocalDateTime.now().minusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "expired-token"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"));
    }

    @Test
    void rotatedPublicTokenInvalidatesOldLink() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String oldToken = "rotate-token";
        order.setPublicToken(oldToken);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusDays(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(5));
        workOrderRepository.save(order);

        MockHttpSession session = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/admin/orders/{id}/rotate-public-token", order.getId())
                .session(session)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        workOrderRepository.flush();
        WorkOrder updated = workOrderRepository.findById(order.getId()).orElseThrow();
        String newToken = updated.getPublicToken();
        assertNotEquals(oldToken, newToken);

        mockMvc.perform(get("/public/order/{token}", oldToken))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"));

        mockMvc.perform(get("/public/order/{token}", newToken))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"));
    }

    @Test
    void tokenWithWhitespaceRedirectsToCanonicalPublicOrderUrl() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("canonical-token");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "  canonical-token  "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/canonical-token"));
    }

    @Test
    void canonicalOrderRedirectPreservesNormalizedSupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("canonical-token-lang");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "  canonical-token-lang  ")
                .param("lang", "  EN  "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/canonical-token-lang?lang=en"));
    }

    @Test
    void canonicalOrderRedirectSupportsLanguageAliasParam() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("canonical-token-language-alias");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "  canonical-token-language-alias  ")
                .param("language", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/canonical-token-language-alias?lang=en"));
    }

    @Test
    void canonicalOrderRedirectOmitsUnsupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("canonical-token-no-lang");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", "  canonical-token-no-lang  ")
                .param("lang", "de"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/canonical-token-no-lang"));
    }

    @Test
    void orderNumberRedirectPreservesNormalizedSupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("resolved-token-lang");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", order.getOrderNumber())
                .param("lang", "  EN  "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/resolved-token-lang?lang=en"));
    }

    @Test
    void orderNumberRedirectOmitsUnsupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken("resolved-token-no-lang");
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", order.getOrderNumber())
                .param("lang", "de"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/resolved-token-no-lang"));
    }

    @Test
    void tooLongTokenIsRejectedAsNotFound() throws Exception {
        String tooLongToken = "x".repeat(200);

        mockMvc.perform(get("/public/order/{token}", tooLongToken))
            .andExpect(status().isNotFound())
            .andExpect(view().name("public/order-not-found"));
    }

    @Test
    void publicQuotePdfDownloadWorksForValidToken() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.READY);
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}/pdf/quote", token).param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")))
            .andExpect(header().string("Content-Disposition", containsString("quote-")));
    }

    @Test
    void publicQuotePdfDownloadSupportsLanguageAliasParam() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-token-language-alias";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.READY);
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}/pdf/quote", token).param("language", "en-US"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")))
            .andExpect(header().string("Content-Disposition", containsString("quote-")));
    }

    @Test
    void publicQuotePdfFallsBackToLocaleAliasWhenLangIsUnsupported() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-token-lang-fallback";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.SENT);
        workOrderRepository.save(order);

        byte[] pdf = mockMvc.perform(get("/public/order/{token}/pdf/quote", token)
                .param("lang", "de")
                .param("locale", "en_US"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        String text = extractPdfText(pdf);
        org.assertj.core.api.Assertions.assertThat(text).contains("Quote total");
    }

    @Test
    void publicQuotePdfDoesNotExposeInternalCostFields() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-privacy-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.SENT);
        workOrderRepository.save(order);

        byte[] pdf = mockMvc.perform(get("/public/order/{token}/pdf/quote", token).param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        String text = extractPdfText(pdf);
        org.assertj.core.api.Assertions.assertThat(text).contains("Quote total");
        org.assertj.core.api.Assertions.assertThat(text).doesNotContain("Final order cost");
        org.assertj.core.api.Assertions.assertThat(text).doesNotContain("Total cost");
        org.assertj.core.api.Assertions.assertThat(text).doesNotContain("Margin %");
    }

    @Test
    void publicQuotePdfRespectsSerbianLangLabels() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-sr-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.SENT);
        workOrderRepository.save(order);

        byte[] pdf = mockMvc.perform(get("/public/order/{token}/pdf/quote", token).param("lang", "sr"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        String text = extractPdfText(pdf);
        org.assertj.core.api.Assertions.assertThat(text).contains("Ukupna cena ponude");
    }

    @Test
    void publicQuotePdfUsesItemsTotalWhenOrderHeaderPriceIsStale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-stale-price-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.SENT);
        order.setPrice(315.0d); // stale order-level value
        workOrderRepository.save(order);

        ProductVariant variant = productVariantRepository.findAllByCompany_Id(order.getCompany().getId())
            .stream()
            .findFirst()
            .orElseGet(() -> {
                Product product = new Product();
                product.setCompany(order.getCompany());
                product.setName("Quote PDF Test Product");
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

        byte[] pdf = mockMvc.perform(get("/public/order/{token}/pdf/quote", token).param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        String text = extractPdfText(pdf);
        org.assertj.core.api.Assertions.assertThat(text).contains("Quote total");
        org.assertj.core.api.Assertions.assertThat(text).contains("300,000.00 RSD");
        org.assertj.core.api.Assertions.assertThat(text).doesNotContain("315.00 RSD");
    }

    @Test
    void publicQuotePdfUsesZeroItemsTotalWhenItemsExistAndHeaderIsStale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-stale-zero-items-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.SENT);
        order.setPrice(315.0d); // stale order-level value
        workOrderRepository.save(order);

        ProductVariant variant = productVariantRepository.findAllByCompany_Id(order.getCompany().getId())
            .stream()
            .findFirst()
            .orElseGet(() -> {
                Product product = new Product();
                product.setCompany(order.getCompany());
                product.setName("Quote PDF Zero Test Product");
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
        item.setCalculatedCost(BigDecimal.ZERO);
        item.setCalculatedPrice(BigDecimal.ZERO);
        item.setProfitAmount(BigDecimal.ZERO);
        item.setMarginPercent(BigDecimal.ZERO);
        item.setCurrency("RSD");
        item.setBreakdownJson("{\"breakdown\":[]}");
        item.setPricingSnapshotJson("{}");
        item.setPriceLocked(true);
        item.setPriceCalculatedAt(LocalDateTime.now());
        workOrderItemRepository.save(item);

        byte[] pdf = mockMvc.perform(get("/public/order/{token}/pdf/quote", token).param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        String text = extractPdfText(pdf);
        org.assertj.core.api.Assertions.assertThat(text).contains("Quote total");
        org.assertj.core.api.Assertions.assertThat(text).contains("0.00 RSD");
        org.assertj.core.api.Assertions.assertThat(text).doesNotContain("315.00 RSD");
    }

    @Test
    void publicQuotePdfDownloadRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/public/order/{token}/pdf/quote", "invalid-token"))
            .andExpect(status().isNotFound());
    }

    @Test
    void publicQuotePdfDownloadBlockedWhenQuoteNotReady() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "public-quote-not-ready-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(QuoteStatus.NONE);
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}/pdf/quote", token))
            .andExpect(status().isNotFound());
    }

    @Test
    void orderTrackingShowsQuotePdfUnavailableHintWhenQuoteNotReady() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "quote-not-ready-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setQuoteStatus(com.printflow.entity.enums.QuoteStatus.NONE);
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token).param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(content().string(containsString("Quote PDF will be available once the quote is ready.")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/public/order/" + token + "/pdf/quote"))));
    }

    @Test
    void orderTrackingPageIncludesLangHiddenFieldForPostFormsWhenLangEn() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(content().string(containsString("name=\"lang\" value=\"en\"")));
    }

    @Test
    void orderTrackingPageFallsBackToSerbianLangFieldForUnsupportedLocale() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-lang-token-fallback";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "de"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(content().string(containsString("name=\"lang\" value=\"sr\"")));
    }

    @Test
    void orderTrackingCanonicalizesLanguageAliasQueryParam() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-canonical-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void orderTrackingCanonicalizesLangAndPreservesUploadErrorKey() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-preserve-error-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("locale", "en_US")
                .param("uploadErrorKey", "public.upload.error.generic"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en&uploadErrorKey=public.upload.error.generic"));
    }

    @Test
    void orderTrackingCanonicalizationDropsInvalidUploadErrorKey() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-invalid-upload-error-key-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US")
                .param("uploadErrorKey", "public.upload.error.generic<script>"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void orderTrackingCanonicalizationPrefersValidAliasOverUnsupportedLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-priority-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "de")
                .param("locale", "en_US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void orderTrackingCanonicalizationPrefersPrimaryLangParamWhenValid() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-primary-priority-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "EN")
                .param("language", "sr-RS"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void orderTrackingCanonicalizesLangAndPreservesApproveDraftParams() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-preserve-approve-draft-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US")
                .param("approveErrorKey", "order_tracking.design_comment_required")
                .param("approveDraftDecision", "false")
                .param("approveDraftComment", "Need crop marks"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token
                + "?lang=en&approveErrorKey=order_tracking.design_comment_required"
                + "&approveDraftDecision=false&approveDraftComment=Need+crop+marks"));
    }

    @Test
    void orderTrackingCanonicalizationDropsInvalidApproveErrorKey() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-invalid-approve-error-key-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US")
                .param("approveErrorKey", "order_tracking.design_error<script>"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void orderTrackingIgnoresInvalidApproveErrorKeyWhenLangAlreadyCanonical() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-invalid-approve-error-key-canonical-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en")
                .param("approveErrorKey", "order_tracking.design_error<script>"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("order_tracking.design_error<script>"))));
    }

    @Test
    void orderTrackingIgnoresInvalidUploadErrorKeyWhenLangAlreadyCanonical() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-invalid-upload-error-key-canonical-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en")
                .param("uploadErrorKey", "public.upload.error.generic<script>"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("public.upload.error.generic<script>"))));
    }

    @Test
    void orderTrackingIgnoresInvalidApproveDraftDecisionWhenLangAlreadyCanonical() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-invalid-approve-decision-canonical-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en")
                .param("approveDraftDecision", "not-a-boolean"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(model().attributeDoesNotExist("approveDraftDecision"));
    }

    @Test
    void orderTrackingCapsApproveDraftCommentWhenLangAlreadyCanonical() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-cap-approve-comment-canonical-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        String longComment = "b".repeat(540);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en")
                .param("approveDraftComment", longComment))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(model().attribute("approveDraftComment", "b".repeat(500)));
    }

    @Test
    void orderTrackingCapsUploadErrorWhenLangAlreadyCanonical() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-cap-upload-error-canonical-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        String longUploadError = "c".repeat(340);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en")
                .param("uploadError", longUploadError))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(model().attribute("uploadError", "c".repeat(300)));
    }

    @Test
    void orderTrackingPrefersValidUploadErrorKeyOverUploadErrorWhenLangAlreadyCanonical() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-upload-error-key-priority-canonical-lang-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("lang", "en")
                .param("uploadError", "fallback text")
                .param("uploadErrorKey", "public.upload.error.generic"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/order-tracking"))
            .andExpect(model().attribute("uploadErrorKey", "public.upload.error.generic"))
            .andExpect(model().attributeDoesNotExist("uploadError"));
    }

    @Test
    void orderTrackingCanonicalizationCapsUploadErrorKeyLength() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-cap-upload-error-key-len-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        String longKey = "a".repeat(130);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US")
                .param("uploadErrorKey", longKey))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en&uploadErrorKey=" + "a".repeat(120)));
    }

    @Test
    void orderTrackingCanonicalizationCapsApproveErrorKeyLength() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-cap-approve-error-key-len-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        String longKey = "b".repeat(130);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("locale", "en_US")
                .param("approveErrorKey", longKey))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en&approveErrorKey=" + "b".repeat(120)));
    }

    @Test
    void orderTrackingCanonicalizesLangAndPreservesUploadErrorMessage() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-preserve-upload-error-message-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US")
                .param("uploadError", "Session expired, please retry"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en&uploadError=Session+expired%2C+please+retry"));
    }

    @Test
    void orderTrackingCanonicalizationTrimsAndCapsUploadErrorMessage() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-cap-upload-error-message-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        String longUploadError = "  " + "x".repeat(320) + "  ";

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US")
                .param("uploadError", longUploadError))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en&uploadError=" + "x".repeat(300)));
    }

    @Test
    void orderTrackingCanonicalizesLangAndEncodesApproveDraftComment() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-encode-approve-comment-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("locale", "en_US")
                .param("approveErrorKey", "order_tracking.design_comment_required")
                .param("approveDraftComment", "Need 3mm bleed & crop marks"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token
                + "?lang=en&approveErrorKey=order_tracking.design_comment_required"
                + "&approveDraftComment=Need+3mm+bleed+%26+crop+marks"));
    }

    @Test
    void orderTrackingCanonicalizationCapsApproveDraftCommentAt500Chars() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-cap-approve-comment-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        String longComment = "a".repeat(510);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("locale", "en_US")
                .param("approveDraftComment", longComment))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en&approveDraftComment=" + "a".repeat(500)));
    }

    @Test
    void orderTrackingCanonicalizationDropsInvalidApproveDraftDecision() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-invalid-decision-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("language", "en-US")
                .param("approveDraftDecision", "not-a-boolean"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void orderTrackingCanonicalizationNormalizesApproveDraftDecisionBoolean() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-lang-alias-normalize-decision-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", token)
                .param("locale", "en_US")
                .param("approveDraftDecision", " TRUE "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en&approveDraftDecision=true"));
    }

    @Test
    void orderTrackingCanonicalizesLangAfterOrderNumberResolution() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "order-number-locale-alias-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(get("/public/order/{token}", order.getOrderNumber())
                .param("language", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void trackSubmitRemovesWhitespaceInsideTrackingCode() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "abc123xyz";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/track")
                .param("trackingCode", "  abc 123 xyz  ")
                .param("lang", "en")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void trackSubmitResolvesOrderNumberCaseInsensitive() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "track-order-number-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);

        String orderNumberLower = order.getOrderNumber() != null
            ? order.getOrderNumber().toLowerCase(java.util.Locale.ROOT)
            : "wo-1000";

        mockMvc.perform(post("/public/track")
                .param("trackingCode", "  " + orderNumberLower + "  ")
                .param("lang", "en")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?lang=en"));
    }

    @Test
    void approveDesignRejectWithoutCommentRedirectsWithValidationError() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-reject-comment-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setStatus(OrderStatus.WAITING_CLIENT_APPROVAL);
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/order/{token}/approve-design", token)
                .with(csrf())
                .param("lang", "en")
                .param("approved", "false")
                .param("comment", " "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?approveErrorKey=order_tracking.design_comment_required&lang=en&approveDraftDecision=false#design-approval-section"));
    }

    @Test
    void approveDesignWithoutDecisionRedirectsWithValidationError() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-decision-required-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setStatus(OrderStatus.WAITING_CLIENT_APPROVAL);
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/order/{token}/approve-design", token)
                .with(csrf())
                .param("lang", "sr"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?approveErrorKey=order_tracking.design_decision_required&lang=sr#design-approval-section"));
    }

    @Test
    void approveDesignUsesLanguageAliasParamsForRedirectLang() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-language-alias-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setStatus(OrderStatus.WAITING_CLIENT_APPROVAL);
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/order/{token}/approve-design", token)
                .with(csrf())
                .param("language", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?approveErrorKey=order_tracking.design_decision_required&lang=en#design-approval-section"));
    }

    @Test
    void approveDesignFallsBackToLocaleAliasWhenLangIsUnsupported() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-language-fallback-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setStatus(OrderStatus.WAITING_CLIENT_APPROVAL);
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/order/{token}/approve-design", token)
                .with(csrf())
                .param("lang", "de")
                .param("locale", "en_US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?approveErrorKey=order_tracking.design_decision_required&lang=en#design-approval-section"));
    }

    @Test
    void approveDesignPrefersValidPrimaryLangOverLocaleAlias() throws Exception {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        String token = "approve-language-primary-priority-token";
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusHours(1));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        order.setStatus(OrderStatus.WAITING_CLIENT_APPROVAL);
        workOrderRepository.save(order);

        mockMvc.perform(post("/public/order/{token}/approve-design", token)
                .with(csrf())
                .param("lang", "EN")
                .param("locale", "sr_RS"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?approveErrorKey=order_tracking.design_decision_required&lang=en#design-approval-section"));
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

}
