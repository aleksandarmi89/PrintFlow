package com.printflow.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.QuoteStatus;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderPdfServiceTest {

    private final OrderPdfService service = new OrderPdfService(createMessages());

    @Test
    void generateQuotePdfReturnsDocumentBytes() {
        WorkOrderDTO order = sampleOrder();
        Company company = sampleCompany();
        WorkOrderItem item = sampleItem();

        byte[] pdf = service.generateQuotePdf(order, company, List.of(item));

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void generateOrderSummarySupportsNullItems() {
        WorkOrderDTO order = sampleOrder();
        Company company = sampleCompany();

        byte[] pdf = service.generateOrderSummaryPdf(order, company, null);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    void generateThrowsWhenOrderMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generateQuotePdf(null, sampleCompany(), List.of()));
    }

    @Test
    void quotePdfUsesItemsTotalForClientFacingTotalWhenItemsExist() throws Exception {
        WorkOrderDTO order = sampleOrder();
        order.setPrice(315.0); // intentionally different from items total
        Company company = sampleCompany();
        WorkOrderItem itemA = sampleItem();
        WorkOrderItem itemB = sampleItem();
        itemA.setCalculatedPrice(BigDecimal.valueOf(300000));
        itemB.setCalculatedPrice(BigDecimal.valueOf(300000));
        itemA.setQuantity(BigDecimal.valueOf(100));
        itemB.setQuantity(BigDecimal.valueOf(100));
        Locale locale = Locale.US;

        byte[] pdf = service.generateQuotePdf(order, company, List.of(itemA, itemB), locale);
        String text = extractPdfText(pdf);

        assertThat(text).contains("Quote total");
        assertThat(text).contains("600,000.00 RSD");
    }

    @Test
    void quotePdfUsesZeroItemsTotalWhenItemsExistWithZeroPrice() throws Exception {
        WorkOrderDTO order = sampleOrder();
        order.setPrice(315.0); // stale order-level value should be ignored when items exist
        Company company = sampleCompany();
        WorkOrderItem item = sampleItem();
        item.setCalculatedPrice(BigDecimal.ZERO);
        item.setCalculatedCost(BigDecimal.ZERO);
        Locale locale = Locale.US;

        byte[] pdf = service.generateQuotePdf(order, company, List.of(item), locale);
        String text = extractPdfText(pdf);

        assertThat(text).contains("Quote total");
        assertThat(text).contains("0.00 RSD");
        assertThat(text).doesNotContain("315.00 RSD");
    }

    @Test
    void quotePdfDoesNotExposeInternalCostAndMarginFields() throws Exception {
        WorkOrderDTO order = sampleOrder();
        Company company = sampleCompany();
        WorkOrderItem item = sampleItem();

        byte[] pdf = service.generateQuotePdf(order, company, List.of(item), Locale.US);
        String text = extractPdfText(pdf);

        assertThat(text).contains("Quote total");
        assertThat(text).doesNotContain("Final order cost");
        assertThat(text).doesNotContain("Total cost");
        assertThat(text).doesNotContain("Margin %");
    }

    @Test
    void summaryPdfStillContainsInternalCostFields() throws Exception {
        WorkOrderDTO order = sampleOrder();
        Company company = sampleCompany();
        WorkOrderItem item = sampleItem();

        byte[] pdf = service.generateOrderSummaryPdf(order, company, List.of(item), Locale.US);
        String text = extractPdfText(pdf);

        assertThat(text).contains("Final order cost");
        assertThat(text).contains("Total cost");
    }

    @Test
    void quotePdfSerbianLocaleUsesLocalizedLabels() throws Exception {
        WorkOrderDTO order = sampleOrder();
        Company company = sampleCompany();
        WorkOrderItem item = sampleItem();

        byte[] pdf = service.generateQuotePdf(order, company, List.of(item), new Locale("sr"));
        String text = extractPdfText(pdf);

        assertThat(text).contains("Ukupna cena ponude");
        assertThat(text).contains("Količina");
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

    private StaticMessageSource createMessages() {
        StaticMessageSource sms = new StaticMessageSource();
        sms.addMessage("pdf.totals.client_price", Locale.ENGLISH, "Quote total");
        sms.addMessage("pdf.totals.client_price", new Locale("sr"), "Ukupna cena ponude");
        sms.addMessage("pdf.items.qty", Locale.ENGLISH, "Qty");
        sms.addMessage("pdf.items.qty", new Locale("sr"), "Količina");
        return sms;
    }

    private WorkOrderDTO sampleOrder() {
        WorkOrderDTO order = new WorkOrderDTO();
        order.setOrderNumber("ORD-1001");
        order.setTitle("Flyers A5");
        order.setClientName("Demo Client");
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setQuoteStatus(QuoteStatus.READY);
        order.setCreatedAt(LocalDateTime.now().minusDays(1));
        order.setDeadline(LocalDateTime.now().plusDays(3));
        order.setDeliveryType(WorkOrder.DeliveryType.COURIER);
        order.setCourierName("DHL");
        order.setTrackingNumber("TRK-001");
        order.setDeliveryAddress("Main Street 1");
        order.setPrice(15000.0);
        order.setCost(9000.0);
        order.setDescription("Promo flyers");
        return order;
    }

    private Company sampleCompany() {
        Company company = new Company();
        company.setName("PrintFlow Demo");
        company.setEmail("info@printflow.test");
        company.setPhone("+38111123456");
        company.setAddress("Belgrade");
        company.setCurrency("RSD");
        return company;
    }

    private WorkOrderItem sampleItem() {
        Product product = new Product();
        product.setName("Flyer");
        ProductVariant variant = new ProductVariant();
        variant.setName("A5");
        variant.setProduct(product);

        WorkOrderItem item = new WorkOrderItem();
        item.setVariant(variant);
        item.setQuantity(BigDecimal.valueOf(1000));
        item.setWidthMm(148);
        item.setHeightMm(210);
        item.setCalculatedCost(BigDecimal.valueOf(9000));
        item.setCalculatedPrice(BigDecimal.valueOf(15000));
        item.setMarginPercent(BigDecimal.valueOf(40));
        return item;
    }
}
