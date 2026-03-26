package com.printflow.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicTrackPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicTrackPageRendersSerbianCopy() throws Exception {
        mockMvc.perform(get("/public/track")
                .header("Accept-Language", "sr"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Kod za pra\u0107enje")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unesi kod za pra\u0107enje")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Primer:")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ORD-123456-789")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Koristi broj naloga")));
    }

    @Test
    void publicTrackPageRendersEnglishCopyWhenLangEn() throws Exception {
        mockMvc.perform(get("/public/track").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tracking Code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter your tracking code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Example:")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"lang\" value=\"en\"")));
    }

    @Test
    void publicTrackPageNormalizesLanguageAliasToCanonicalLangQuery() throws Exception {
        mockMvc.perform(get("/public/track").param("language", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?lang=en"));
    }

    @Test
    void publicTrackPageFallsBackToLocaleAliasWhenLangIsUnsupported() throws Exception {
        mockMvc.perform(get("/public/track").param("lang", "de").param("locale", "en_US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?lang=en"));
    }

    @Test
    void publicTrackPageCanonicalRedirectKeepsTokenAndCompany() throws Exception {
        mockMvc.perform(get("/public/track")
                .param("language", "en-US")
                .param("token", "ORD-ALIAS-1")
                .param("company_id", "17"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?lang=en&company=17&token=ORD-ALIAS-1"));
    }

    @Test
    void publicTrackPageCanonicalizesUppercaseLangParam() throws Exception {
        mockMvc.perform(get("/public/track").param("lang", "EN"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?lang=en"));
    }

    @Test
    void publicTrackPageCanonicalizesTrimmedLangAndKeepsTokenAndCompany() throws Exception {
        mockMvc.perform(get("/public/track")
                .param("lang", "  EN  ")
                .param("token", "ORD-ALIAS-2")
                .param("company", "11"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?lang=en&company=11&token=ORD-ALIAS-2"));
    }

    @Test
    void publicTrackPageDoesNotRedirectWhenCanonicalLangAlreadyProvided() throws Exception {
        mockMvc.perform(get("/public/track")
                .param("lang", "en")
                .param("language", "sr-RS")
                .param("token", "ORD-CANON-1")
                .param("company", "12"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"lang\" value=\"en\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-CANON-1\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"company\" value=\"12\"")));
    }

    @Test
    void publicTrackPhpPageRendersEnglishCopyWhenLangEn() throws Exception {
        mockMvc.perform(get("/public/track.php").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tracking Code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"lang\" value=\"en\"")));
    }

    @Test
    void publicTrackPageClampsHiddenLangToSrForUnsupportedLocale() throws Exception {
        mockMvc.perform(get("/public/track").param("lang", "de"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"lang\" value=\"sr\"")));
    }

    @Test
    void publicTrackInputDisablesAutoCapitalizationAndAutocorrect() throws Exception {
        mockMvc.perform(get("/public/track"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"trackingCode\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("autocapitalize=\"off\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("autocomplete=\"off\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("autocorrect=\"off\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("spellcheck=\"false\"")));
    }

    @Test
    void legacyTrackPathRedirectsToTrackFormWithToken() throws Exception {
        mockMvc.perform(get("/public/track/ORD-123_ABC"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-123_ABC"));
    }

    @Test
    void legacyTrackPhpPathRedirectsToTrackFormWithToken() throws Exception {
        mockMvc.perform(get("/public/track.php/ORD-123_ABC"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-123_ABC"));
    }

    @Test
    void legacyTrackPathKeepsLangAndCompanyParams() throws Exception {
        mockMvc.perform(get("/public/track/ORD-123_ABC").param("lang", "en").param("company", "7"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-123_ABC&company=7&lang=en"));
    }

    @Test
    void legacyOrderQueryRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/order").param("token", "ORD-321_XY").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-321_XY?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsShortTokenAlias() throws Exception {
        mockMvc.perform(get("/public/order").param("t", "ORD-321_SHORT").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-321_SHORT?lang=en"));
    }

    @Test
    void legacyOrderPhpQueryRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/order.php").param("token", "ORD-321_XY").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-321_XY?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsLocaleAliasParam() throws Exception {
        mockMvc.perform(get("/public/order").param("token", "ORD-321_XY").param("locale", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-321_XY?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsLanguageAliasParam() throws Exception {
        mockMvc.perform(get("/public/order").param("token", "ORD-321_XY").param("language", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-321_XY?lang=en"));
    }

    @Test
    void legacyOrderQueryFallsBackToLocaleAliasWhenLangIsUnsupported() throws Exception {
        mockMvc.perform(get("/public/order")
                .param("token", "ORD-321_XY")
                .param("lang", "de")
                .param("locale", "en_US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-321_XY?lang=en"));
    }

    @Test
    void legacyQuoteQueryFallsBackToLocaleAliasWhenLangIsUnsupported() throws Exception {
        mockMvc.perform(get("/public/quote")
                .param("token", "ORD-Q-XY")
                .param("lang", "de")
                .param("locale", "en_US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-Q-XY/pdf/quote?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsRegionalLocaleValue() throws Exception {
        mockMvc.perform(get("/public/order").param("token", "ORD-321_XY").param("language", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-321_XY?lang=en"));
    }

    @Test
    void legacyOrderQueryWithoutTokenRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/order").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsTrackingCodeParam() throws Exception {
        mockMvc.perform(get("/public/order").param("trackingCode", "ORD-TRK-1").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-TRK-1?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsOrderNumberParam() throws Exception {
        mockMvc.perform(get("/public/order").param("orderNumber", "ORD-N-10").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-N-10?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsPublicTokenParam() throws Exception {
        mockMvc.perform(get("/public/order").param("publicToken", "ORD-PUB-1").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-PUB-1?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsSnakeCasePublicTokenParam() throws Exception {
        mockMvc.perform(get("/public/order").param("public_token", "ORD-PUB-1B").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-PUB-1B?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsTrackingParam() throws Exception {
        mockMvc.perform(get("/public/order").param("tracking", "ORD-TRACK-11").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-TRACK-11?lang=en"));
    }

    @Test
    void legacyOrderQuerySupportsOrderParam() throws Exception {
        mockMvc.perform(get("/public/order").param("order", "ORD-TRACK-12").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-TRACK-12?lang=en"));
    }

    @Test
    void legacyOrderQueryWithoutTokenKeepsCompanyFilterOnTrackPage() throws Exception {
        mockMvc.perform(get("/public/order").param("company", "9").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?company=9&lang=en"));
    }

    @Test
    void legacyOrderQueryWithoutTokenKeepsSnakeCaseCompanyFilterOnTrackPage() throws Exception {
        mockMvc.perform(get("/public/order").param("company_id", "9").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?company=9&lang=en"));
    }

    @Test
    void trackFormSupportsLegacyTrackingCodeQueryParam() throws Exception {
        mockMvc.perform(get("/public/track").param("trackingCode", "ORD-999_ABC"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-999_ABC\"")));
    }

    @Test
    void trackFormSupportsSnakeCaseTrackingCodeQueryParam() throws Exception {
        mockMvc.perform(get("/public/track").param("tracking_code", "ORD-999_SNAKE"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-999_SNAKE\"")));
    }

    @Test
    void trackFormPrefersTokenOverLegacyTrackingCodeWhenBothExist() throws Exception {
        mockMvc.perform(get("/public/track")
                .param("token", "ORD-111")
                .param("trackingCode", "ORD-222"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-111\"")));
    }

    @Test
    void trackFormSupportsLegacyCodeQueryParam() throws Exception {
        mockMvc.perform(get("/public/track").param("code", "ORD-777_X"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-777_X\"")));
    }

    @Test
    void trackFormSupportsLegacyOrderNumberQueryParam() throws Exception {
        mockMvc.perform(get("/public/track").param("orderNumber", "ORD-555"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-555\"")));
    }

    @Test
    void trackFormSupportsLegacyPublicTokenQueryParam() throws Exception {
        mockMvc.perform(get("/public/track").param("publicToken", "ORD-PUB-2"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-PUB-2\"")));
    }

    @Test
    void trackFormSupportsLegacyTrackingQueryParam() throws Exception {
        mockMvc.perform(get("/public/track").param("tracking", "ORD-PUB-3"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-PUB-3\"")));
    }

    @Test
    void trackFormSupportsLegacyOrderQueryParam() throws Exception {
        mockMvc.perform(get("/public/track").param("order", "ORD-PUB-4"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\" value=\"ORD-PUB-4\"")));
    }

    @Test
    void legacyTrackOrderRouteRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/track-order")
                .param("trackingCode", "ORD-101")
                .param("company", "3")
                .param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-101&company=3&lang=en"));
    }

    @Test
    void legacyTrackOrderRouteSupportsLocaleAliasParam() throws Exception {
        mockMvc.perform(get("/public/track-order")
                .param("trackingCode", "ORD-101")
                .param("company", "3")
                .param("locale", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-101&company=3&lang=en"));
    }

    @Test
    void legacyTrackOrderRouteSupportsLngAliasParam() throws Exception {
        mockMvc.perform(get("/public/track-order")
                .param("trackingCode", "ORD-101")
                .param("company", "3")
                .param("lng", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-101&company=3&lang=en"));
    }

    @Test
    void legacyTrackOrderRouteSupportsSnakeCaseCompanyParam() throws Exception {
        mockMvc.perform(get("/public/track-order")
                .param("trackingCode", "ORD-101")
                .param("company_id", "3")
                .param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-101&company=3&lang=en"));
    }

    @Test
    void legacyOrderTrackingRouteRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/order-tracking").param("code", "ORD-202"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-202"));
    }

    @Test
    void legacyOrderTrackingRouteSupportsOrderNumberParam() throws Exception {
        mockMvc.perform(get("/public/order-tracking").param("orderNumber", "ORD-303"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-303"));
    }

    @Test
    void legacyTrackingRouteRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/tracking").param("tracking", "ORD-404").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-404&lang=en"));
    }

    @Test
    void legacyTrackingHtmlRouteRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/tracking.html").param("trackingCode", "ORD-405"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-405"));
    }

    @Test
    void legacyStatusPathSupportsLanguageAliasParam() throws Exception {
        mockMvc.perform(get("/public/status/ORD-STATUS-1").param("language", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-STATUS-1?lang=en"));
    }

    @Test
    void legacyNestedStatusPathSupportsLngAliasParam() throws Exception {
        mockMvc.perform(get("/public/order/ORD-STATUS-2/status").param("lng", "en_US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-STATUS-2?lang=en"));
    }

    @Test
    void legacyQuotePathSupportsLocaleAliasParam() throws Exception {
        mockMvc.perform(get("/public/order/ORD-QUOTE-1/quote-pdf").param("locale", "en-US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-QUOTE-1/pdf/quote?lang=en"));
    }

    @Test
    void legacyQuoteDirectPathSupportsLanguageAliasParam() throws Exception {
        mockMvc.perform(get("/public/quote/ORD-QUOTE-2").param("language", "en_US"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-QUOTE-2/pdf/quote?lang=en"));
    }

    @Test
    void legacyTrackingPhpRouteRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/tracking.php").param("trackingCode", "ORD-406").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-406&lang=en"));
    }

    @Test
    void legacyQuoteRouteRedirectsToCanonicalQuotePdfRoute() throws Exception {
        mockMvc.perform(get("/public/order/ORD-333/quote").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-333/pdf/quote?lang=en"));
    }

    @Test
    void legacyQuotePdfRouteRedirectsToCanonicalQuotePdfRoute() throws Exception {
        mockMvc.perform(get("/public/order/ORD-444/quote-pdf"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-444/pdf/quote"));
    }

    @Test
    void legacyQuotePdfExtensionRouteRedirectsToCanonicalQuotePdfRoute() throws Exception {
        mockMvc.perform(get("/public/order/ORD-445/quote.pdf").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-445/pdf/quote?lang=en"));
    }

    @Test
    void legacyQuotePathRouteRedirectsToCanonicalQuotePdfRoute() throws Exception {
        mockMvc.perform(get("/public/quote/ORD-446"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-446/pdf/quote"));
    }

    @Test
    void legacyQuoteQueryRouteRedirectsToCanonicalQuotePdfRoute() throws Exception {
        mockMvc.perform(get("/public/quote").param("orderNumber", "ORD-447").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-447/pdf/quote?lang=en"));
    }

    @Test
    void legacyQuoteQueryRouteSupportsSnakeCaseOrderNumber() throws Exception {
        mockMvc.perform(get("/public/quote").param("order_number", "ORD-447A").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-447A/pdf/quote?lang=en"));
    }

    @Test
    void legacyQuoteQueryRouteWithoutTokenRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/quote").param("company", "12").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?company=12&lang=en"));
    }

    @Test
    void legacyQuotePhpQueryRouteRedirectsToCanonicalQuotePdfRoute() throws Exception {
        mockMvc.perform(get("/public/quote.php").param("order", "ORD-448").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-448/pdf/quote?lang=en"));
    }

    @Test
    void legacyOrderStatusRouteRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/order-status/ORD-777").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-777?lang=en"));
    }

    @Test
    void legacyStatusRouteRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/status/ORD-778"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-778"));
    }

    @Test
    void legacyStatusPhpPathRouteRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/status.php/ORD-778"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-778"));
    }

    @Test
    void legacyOrderStatusQueryRouteRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/order-status").param("trackingCode", "ORD-779").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-779?lang=en"));
    }

    @Test
    void legacyStatusQueryRouteWithoutTokenRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/status").param("company", "11").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?company=11&lang=en"));
    }

    @Test
    void legacyStatusQueryRouteSupportsOrderParam() throws Exception {
        mockMvc.perform(get("/public/status").param("order", "ORD-781").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-781?lang=en"));
    }

    @Test
    void legacyStatusQueryRouteSupportsLocaleAliasParam() throws Exception {
        mockMvc.perform(get("/public/status").param("order", "ORD-781").param("locale", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-781?lang=en"));
    }

    @Test
    void legacyNestedTrackingRouteRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/order/ORD-779/tracking").param("lang", "en"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-779?lang=en"));
    }

    @Test
    void legacyNestedStatusRouteRedirectsToCanonicalOrderPath() throws Exception {
        mockMvc.perform(get("/public/order/ORD-780/status"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/ORD-780"));
    }

    @Test
    void legacyTrackOrderPathWithTokenRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/track-order/ORD-909").param("lang", "en").param("company", "4"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-909&company=4&lang=en"));
    }

    @Test
    void legacyOrderTrackingPathWithTokenRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/order-tracking/ORD-910"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-910"));
    }

    @Test
    void legacyTrackingPathWithTokenRedirectsToTrackForm() throws Exception {
        mockMvc.perform(get("/public/tracking/ORD-911"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/track?token=ORD-911"));
    }
}
