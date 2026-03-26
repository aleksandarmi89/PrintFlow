package com.printflow.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.BaseFont;
import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrderItem;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.awt.Color;

@Service
public class OrderPdfService {

    private static final Font TITLE = buildFont(Font.BOLD, 16);
    private static final Font SUBTITLE = buildFont(Font.BOLD, 12);
    private static final Font BODY = buildFont(Font.NORMAL, 10);
    private static final Font BODY_BOLD = buildFont(Font.BOLD, 10);
    private static final Font MUTED = buildFont(Font.NORMAL, 9);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Color BRAND_BLUE = new Color(30, 64, 175);
    private static final Color BORDER_GRAY = new Color(229, 231, 235);
    private static final Color HEADER_GRAY = new Color(243, 244, 246);
    private static final Color ROW_ALT_GRAY = new Color(249, 250, 251);

    private final MessageSource messageSource;

    public OrderPdfService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public byte[] generateQuotePdf(WorkOrderDTO order, Company company, List<WorkOrderItem> items) {
        return generate(order, company, items, true, LocaleContextHolder.getLocale());
    }

    public byte[] generateQuotePdf(WorkOrderDTO order, Company company, List<WorkOrderItem> items, Locale locale) {
        return generate(order, company, items, true, locale);
    }

    public byte[] generateOrderSummaryPdf(WorkOrderDTO order, Company company, List<WorkOrderItem> items) {
        return generate(order, company, items, false, LocaleContextHolder.getLocale());
    }

    public byte[] generateOrderSummaryPdf(WorkOrderDTO order, Company company, List<WorkOrderItem> items, Locale locale) {
        return generate(order, company, items, false, locale);
    }

    private byte[] generate(WorkOrderDTO order, Company company, List<WorkOrderItem> items, boolean quoteMode, Locale locale) {
        if (order == null) {
            throw new IllegalArgumentException("Order is required");
        }
        List<WorkOrderItem> safeItems = items != null ? items : Collections.emptyList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Locale safeLocale = locale != null ? locale : LocaleContextHolder.getLocale();
            String currency = company != null && company.getCurrency() != null && !company.getCurrency().isBlank()
                ? company.getCurrency().toUpperCase(Locale.ROOT)
                : "RSD";
            String title = quoteMode ? msg("pdf.quote.title", safeLocale, "QUOTE / ESTIMATE")
                : msg("pdf.summary.title", safeLocale, "ORDER SUMMARY");
            addDocumentHeader(doc, safeLocale, title, order, company);
            BigDecimal precomputedItemsTotalPrice = sumItemsPrice(safeItems);
            boolean hasItems = !safeItems.isEmpty();
            addQuoteHighlights(doc, safeLocale, currency, order, quoteMode, precomputedItemsTotalPrice, hasItems);
            addDivider(doc);

            addSectionTitle(doc, msg("pdf.section.company", safeLocale, "Company"));
            addLine(doc, msg("pdf.company.name", safeLocale, "Name"), company != null ? company.getName() : "PrintFlow");
            addOptionalLine(doc, msg("pdf.company.email", safeLocale, "Email"), company != null ? company.getEmail() : null);
            addOptionalLine(doc, msg("pdf.company.phone", safeLocale, "Phone"), company != null ? company.getPhone() : null);
            addOptionalLine(doc, msg("pdf.company.address", safeLocale, "Address"), company != null ? company.getAddress() : null);

            addSectionTitle(doc, msg("pdf.section.order", safeLocale, "Order"));
            addLine(doc, msg("pdf.order.number", safeLocale, "Order number"), safe(order.getOrderNumber()));
            addLine(doc, msg("pdf.order.title", safeLocale, "Title"), safe(order.getTitle()));
            addLine(doc, msg("pdf.order.client", safeLocale, "Client"), safe(order.getClientName()));
            addLine(doc, msg("pdf.order.status", safeLocale, "Status"), resolveOrderStatus(order, safeLocale));
            addLine(doc, msg("pdf.order.quote_status", safeLocale, "Quote status"), resolveQuoteStatus(order, safeLocale));
            addLine(doc, msg("pdf.order.created", safeLocale, "Created"), formatDate(order.getCreatedAt()));
            addOptionalDateLine(doc, msg("pdf.order.deadline", safeLocale, "Deadline"), order.getDeadline());
            addLine(doc, msg("pdf.order.delivery_type", safeLocale, "Delivery type"), resolveDeliveryType(order, safeLocale));
            addOptionalLine(doc, msg("pdf.order.recipient", safeLocale, "Recipient"), order.getDeliveryRecipientName());
            addOptionalLine(doc, msg("pdf.order.recipient_phone", safeLocale, "Recipient phone"), order.getDeliveryRecipientPhone());
            addOptionalLine(doc, msg("pdf.order.delivery_city", safeLocale, "Delivery city"), order.getDeliveryCity());
            addOptionalLine(doc, msg("pdf.order.postal_code", safeLocale, "Postal code"), order.getDeliveryPostalCode());
            addOptionalLine(doc, msg("pdf.order.courier", safeLocale, "Courier"), order.getCourierName());
            addOptionalLine(doc, msg("pdf.order.tracking", safeLocale, "Tracking"), order.getTrackingNumber());
            addOptionalLine(doc, msg("pdf.order.delivery_address", safeLocale, "Delivery address"), order.getDeliveryAddress());
            if (order.getShipmentStatus() != null) {
                addLine(doc, msg("pdf.order.shipment_status", safeLocale, "Shipment status"), resolveShipmentStatus(order, safeLocale));
            }
            if (order.getShipmentPrice() != null) {
                addLine(doc, msg("pdf.order.shipment_price", safeLocale, "Shipment price"),
                    money(BigDecimal.valueOf(order.getShipmentPrice()), currency, safeLocale));
            }
            addOptionalDateLine(doc, msg("pdf.order.shipped_at", safeLocale, "Shipped at"), order.getShippedAt());
            addOptionalDateLine(doc, msg("pdf.order.delivered_at", safeLocale, "Delivered at"), order.getDeliveredAt());
            if (quoteMode) {
                addOptionalDateLine(doc, msg("pdf.order.quote_valid_until", safeLocale, "Quote valid until"), order.getQuoteValidUntil());
                addOptionalDateLine(doc, msg("pdf.order.quote_sent_at", safeLocale, "Quote sent at"), order.getQuoteSentAt());
            }

            addSectionTitle(doc, msg("pdf.section.items", safeLocale, "Items"));
            boolean clientFacing = quoteMode;
            PdfPTable table = new PdfPTable(clientFacing ? 5 : 6);
            table.setWidthPercentage(100);
            if (clientFacing) {
                table.setWidths(new float[]{30, 12, 18, 20, 20});
            } else {
                table.setWidths(new float[]{20, 10, 10, 15, 15, 15});
            }
            addHeader(table, msg("pdf.items.variant", safeLocale, "Variant"));
            addHeader(table, msg("pdf.items.qty", safeLocale, "Qty"));
            addHeader(table, msg("pdf.items.size", safeLocale, "Size"));
            if (!clientFacing) {
                addHeader(table, msg("pdf.items.price", safeLocale, "Price"));
                addHeader(table, msg("pdf.items.cost", safeLocale, "Cost"));
                addHeader(table, msg("pdf.items.margin", safeLocale, "Margin %"));
            } else {
                addHeader(table, msg("pdf.items.unit_price", safeLocale, "Unit price"));
                addHeader(table, msg("pdf.items.line_price", safeLocale, "Line price"));
            }

            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal totalPrice = BigDecimal.ZERO;
            int rowIndex = 0;
            for (WorkOrderItem item : safeItems) {
                String variantName = item.getVariant() != null ? safe(item.getVariant().getName()) : "-";
                String qty = item.getQuantity() != null ? item.getQuantity().stripTrailingZeros().toPlainString() : "-";
                String size = (item.getWidthMm() != null && item.getHeightMm() != null)
                    ? item.getWidthMm() + "x" + item.getHeightMm() + " mm" : "-";
                BigDecimal cost = item.getCalculatedCost() != null ? item.getCalculatedCost() : BigDecimal.ZERO;
                BigDecimal price = item.getCalculatedPrice() != null ? item.getCalculatedPrice() : BigDecimal.ZERO;
                BigDecimal margin = item.getMarginPercent() != null ? item.getMarginPercent() : BigDecimal.ZERO;
                BigDecimal unitPrice = null;
                if (item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    unitPrice = price.divide(item.getQuantity(), 2, RoundingMode.HALF_UP);
                }
                totalCost = totalCost.add(cost);
                totalPrice = totalPrice.add(price);
                Color rowBg = (rowIndex++ % 2 == 0) ? null : ROW_ALT_GRAY;
                addCell(table, variantName, rowBg);
                addAlignedCell(table, qty, Element.ALIGN_RIGHT, rowBg);
                addCell(table, size, rowBg);
                if (!clientFacing) {
                    addAlignedCell(table, money(price, currency, safeLocale), Element.ALIGN_RIGHT, rowBg);
                    addAlignedCell(table, money(cost, currency, safeLocale), Element.ALIGN_RIGHT, rowBg);
                    addAlignedCell(table, percent(margin, safeLocale), Element.ALIGN_RIGHT, rowBg);
                } else {
                    addAlignedCell(table, unitPrice != null ? money(unitPrice, currency, safeLocale) : "-", Element.ALIGN_RIGHT, rowBg);
                    addAlignedCell(table, money(price, currency, safeLocale), Element.ALIGN_RIGHT, rowBg);
                }
            }
            if (safeItems.isEmpty()) {
                addCell(table, "-", clientFacing ? 5 : 6);
            } else if (clientFacing) {
                addSummaryRow(table,
                    msg("pdf.items.subtotal", safeLocale, "Items subtotal"),
                    4,
                    money(totalPrice, currency, safeLocale));
            }
            doc.add(table);

            addSectionTitle(doc, quoteMode
                ? msg("pdf.section.quote_totals", safeLocale, "Quote totals")
                : msg("pdf.section.summary_totals", safeLocale, "Summary totals"));
            BigDecimal orderLevelPrice = order.getPrice() != null ? BigDecimal.valueOf(order.getPrice()) : null;
            BigDecimal orderLevelCost = order.getCost() != null ? BigDecimal.valueOf(order.getCost()) : null;
            addTotalsTable(doc, safeLocale, currency, totalCost, totalPrice, orderLevelCost, orderLevelPrice, clientFacing, hasItems);
            if (!clientFacing) {
                boolean hasDeltaInfo = false;
                if (orderLevelPrice != null) {
                    BigDecimal deltaPrice = orderLevelPrice.subtract(totalPrice);
                    if (deltaPrice.abs().compareTo(new BigDecimal("0.005")) > 0) {
                        addLine(doc, msg("pdf.totals.order_price_delta", safeLocale, "Difference vs item totals (price)"),
                            money(deltaPrice, currency, safeLocale));
                        hasDeltaInfo = true;
                    }
                }
                if (orderLevelCost != null) {
                    BigDecimal deltaCost = orderLevelCost.subtract(totalCost);
                    if (deltaCost.abs().compareTo(new BigDecimal("0.005")) > 0) {
                        addLine(doc, msg("pdf.totals.order_cost_delta", safeLocale, "Difference vs item totals (cost)"),
                            money(deltaCost, currency, safeLocale));
                        hasDeltaInfo = true;
                    }
                }
                if (hasDeltaInfo) {
                    Paragraph totalsHint = new Paragraph(
                        msg("pdf.totals.order_levels_hint", safeLocale, "Order-level fields can include manual corrections not distributed to item rows."),
                        MUTED
                    );
                    totalsHint.setSpacingBefore(4f);
                    doc.add(totalsHint);
                }
            }
            if (quoteMode) {
                addQuoteCommercialNotes(doc, safeLocale, order, company);
            }

            if (order.getDescription() != null && !order.getDescription().isBlank()) {
                addSectionTitle(doc, msg("pdf.section.description", safeLocale, "Description"));
                doc.add(new Paragraph(order.getDescription(), BODY));
            }
            if (order.getSpecifications() != null && !order.getSpecifications().isBlank()) {
                addSectionTitle(doc, msg("pdf.section.specifications", safeLocale, "Specifications"));
                doc.add(new Paragraph(order.getSpecifications(), BODY));
            }
            if (!clientFacing && order.getInternalNotes() != null && !order.getInternalNotes().isBlank()) {
                addSectionTitle(doc, msg("pdf.section.notes", safeLocale, "Notes"));
                doc.add(new Paragraph(order.getInternalNotes(), BODY));
            }
            if (order.getShippingNote() != null && !order.getShippingNote().isBlank()) {
                addSectionTitle(doc, msg("pdf.section.shipping_note", safeLocale, "Shipping note"));
                doc.add(new Paragraph(order.getShippingNote(), BODY));
            }
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to generate PDF", ex);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private void addSectionTitle(Document doc, String text) throws DocumentException {
        doc.add(new Paragraph(" ", BODY));
        Paragraph paragraph = new Paragraph(text, SUBTITLE);
        paragraph.setSpacingAfter(4f);
        doc.add(paragraph);
        addDivider(doc);
    }

    private void addLine(Document doc, String label, String value) throws DocumentException {
        String rendered = (value == null || value.isBlank()) ? "-" : value;
        doc.add(new Paragraph(label + ": " + rendered, BODY));
    }

    private void addOptionalLine(Document doc, String label, String value) throws DocumentException {
        if (value == null || value.isBlank()) {
            return;
        }
        addLine(doc, label, value);
    }

    private void addOptionalDateLine(Document doc, String label, LocalDateTime value) throws DocumentException {
        if (value == null) {
            return;
        }
        addLine(doc, label, formatDate(value));
    }

    private void addTotalsTable(Document doc,
                                Locale locale,
                                String currency,
                                BigDecimal totalCost,
                                BigDecimal totalPrice,
                                BigDecimal orderLevelCost,
                                BigDecimal orderLevelPrice,
                                boolean clientFacing,
                                boolean hasItems) throws DocumentException {
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(55);
        totals.setHorizontalAlignment(Element.ALIGN_LEFT);
        totals.setWidths(new float[]{60, 40});
        if (clientFacing) {
            BigDecimal clientPrice = chooseClientFacingTotal(totalPrice, orderLevelPrice, hasItems);
            addTotalsRow(totals, msg("pdf.totals.client_price", locale, "Quote total"), money(clientPrice, currency, locale), true);
            doc.add(totals);
            return;
        }
        addTotalsRow(totals, msg("pdf.totals.total_price", locale, "Total price"), money(totalPrice, currency, locale), false);
        if (!clientFacing) {
            addTotalsRow(totals, msg("pdf.totals.total_cost", locale, "Total cost"), money(totalCost, currency, locale), false);
            addTotalsRow(totals, msg("pdf.totals.order_cost", locale, "Final order cost"),
                orderLevelCost != null ? money(orderLevelCost, currency, locale) : "-", true);
        }
        addTotalsRow(totals, msg("pdf.totals.order_price", locale, "Final order price"),
            orderLevelPrice != null ? money(orderLevelPrice, currency, locale) : "-", true);
        doc.add(totals);
    }

    private void addTotalsRow(PdfPTable table, String label, String value, boolean emphasize) {
        Font font = emphasize ? BODY_BOLD : BODY;
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setPadding(6f);
        labelCell.setBorderColor(BORDER_GRAY);
        if (emphasize) {
            labelCell.setBackgroundColor(new Color(249, 250, 251));
        }
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setPadding(6f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorderColor(BORDER_GRAY);
        if (emphasize) {
            valueCell.setBackgroundColor(new Color(249, 250, 251));
        }
        table.addCell(valueCell);
    }

    private void addHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, SUBTITLE));
        cell.setPadding(6f);
        cell.setBackgroundColor(HEADER_GRAY);
        cell.setBorderColor(BORDER_GRAY);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", BODY));
        cell.setPadding(6f);
        cell.setBorderColor(BORDER_GRAY);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        table.addCell(cell);
    }

    private void addAlignedCell(PdfPTable table, String text, int align, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", BODY));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(BORDER_GRAY);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", BODY));
        cell.setPadding(4f);
        cell.setColspan(colspan);
        cell.setBorderColor(BORDER_GRAY);
        table.addCell(cell);
    }

    private void addSummaryRow(PdfPTable table, String label, int labelColspan, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BODY_BOLD));
        labelCell.setColspan(labelColspan);
        labelCell.setPadding(6f);
        labelCell.setBorderColor(BORDER_GRAY);
        labelCell.setBackgroundColor(HEADER_GRAY);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, BODY_BOLD));
        valueCell.setPadding(6f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorderColor(BORDER_GRAY);
        valueCell.setBackgroundColor(HEADER_GRAY);
        table.addCell(valueCell);
    }

    private void addQuoteCommercialNotes(Document doc,
                                         Locale locale,
                                         WorkOrderDTO order,
                                         Company company) throws DocumentException {
        addSectionTitle(doc, msg("pdf.section.commercial_notes", locale, "Commercial notes"));

        String validity;
        if (order.getQuoteValidUntil() != null) {
            validity = formatDate(order.getQuoteValidUntil());
        } else {
            validity = msg("pdf.quote.validity.by_agreement", locale, "By agreement");
        }
        addLine(doc, msg("pdf.quote.validity", locale, "Quote validity"), validity);

        if (order.getCreatedAt() != null && order.getDeadline() != null && !order.getDeadline().isBefore(order.getCreatedAt())) {
            long leadDays = ChronoUnit.DAYS.between(order.getCreatedAt().toLocalDate(), order.getDeadline().toLocalDate());
            addLine(doc, msg("pdf.quote.lead_time", locale, "Estimated lead time"),
                leadDays + " " + msg("pdf.quote.days", locale, "days"));
        }

        if (company != null) {
            String contact = safe(company.getEmail());
            if (!"-".equals(contact) && company.getPhone() != null && !company.getPhone().isBlank()) {
                contact = contact + " / " + company.getPhone();
            } else if ((contact == null || "-".equals(contact)) && company.getPhone() != null && !company.getPhone().isBlank()) {
                contact = company.getPhone();
            }
            if (contact != null && !contact.isBlank() && !"-".equals(contact)) {
                addLine(doc, msg("pdf.quote.contact_for_confirmation", locale, "Contact for confirmation"), contact);
            }
        }
    }

    private void addDocumentHeader(Document doc,
                                   Locale locale,
                                   String title,
                                   WorkOrderDTO order,
                                   Company company) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{62, 38});

        String companyName = company != null && company.getName() != null && !company.getName().isBlank()
            ? company.getName()
            : "PrintFlow";

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        Paragraph companyP = new Paragraph(companyName, TITLE);
        companyP.getFont().setColor(BRAND_BLUE);
        companyP.setSpacingAfter(2f);
        left.addElement(companyP);
        left.addElement(new Paragraph(title, SUBTITLE));
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        Paragraph meta = new Paragraph(
            msg("pdf.meta.order", locale, "Order") + ": " + safe(order.getOrderNumber())
                + "\n" + msg("pdf.meta.generated_at", locale, "Generated at") + ": " + formatDate(LocalDateTime.now()),
            MUTED
        );
        meta.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(meta);
        header.addCell(right);

        doc.add(header);
    }

    private void addDivider(Document doc) throws DocumentException {
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell dividerCell = new PdfPCell(new Phrase(""));
        dividerCell.setBorder(Rectangle.BOTTOM);
        dividerCell.setBorderColor(BORDER_GRAY);
        dividerCell.setFixedHeight(6f);
        dividerCell.setPadding(0f);
        divider.addCell(dividerCell);
        doc.add(divider);
    }

    private void addQuoteHighlights(Document doc,
                                    Locale locale,
                                    String currency,
                                    WorkOrderDTO order,
                                    boolean quoteMode,
                                    BigDecimal itemsTotalPrice,
                                    boolean hasItems) throws DocumentException {
        BigDecimal finalPrice = order.getPrice() != null ? BigDecimal.valueOf(order.getPrice()) : null;
        BigDecimal itemsPrice = itemsTotalPrice;
        BigDecimal highlightedPrice = quoteMode
            ? chooseClientFacingTotal(itemsPrice, finalPrice, hasItems)
            : (finalPrice != null ? finalPrice : itemsPrice);
        if (finalPrice == null && !quoteMode) {
            return;
        }
        PdfPTable highlights = new PdfPTable(2);
        highlights.setWidthPercentage(100);
        highlights.setSpacingBefore(6f);
        highlights.setSpacingAfter(4f);
        highlights.setWidths(new float[]{50, 50});

        String priceLabel = quoteMode
            ? msg("pdf.totals.client_price", locale, "Quote total")
            : msg("pdf.totals.order_price", locale, "Final order price");
        String priceValue = highlightedPrice != null ? money(highlightedPrice, currency, locale) : "-";
        highlights.addCell(buildHighlightCell(priceLabel, priceValue));

        if (quoteMode) {
            String validLabel = msg("pdf.order.quote_valid_until", locale, "Quote valid until");
            String validValue = formatDate(order.getQuoteValidUntil());
            highlights.addCell(buildHighlightCell(validLabel, validValue));
        } else {
            String statusLabel = msg("pdf.order.status", locale, "Status");
            String statusValue = resolveOrderStatus(order, locale);
            highlights.addCell(buildHighlightCell(statusLabel, statusValue));
        }
        doc.add(highlights);
    }

    private PdfPCell buildHighlightCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8f);
        cell.setBorderColor(BORDER_GRAY);
        cell.setBackgroundColor(new Color(248, 250, 252));
        Paragraph p1 = new Paragraph(label, MUTED);
        p1.setSpacingAfter(2f);
        cell.addElement(p1);
        cell.addElement(new Paragraph(value, BODY_BOLD));
        return cell;
    }

    private BigDecimal chooseClientFacingTotal(BigDecimal itemsTotalPrice, BigDecimal orderLevelPrice, boolean hasItems) {
        if (hasItems && itemsTotalPrice != null) {
            return itemsTotalPrice;
        }
        return orderLevelPrice != null ? orderLevelPrice : BigDecimal.ZERO;
    }

    private String formatDate(LocalDateTime value) {
        return value != null ? DATE_TIME.format(value) : "-";
    }

    private String money(BigDecimal amount, String currency, Locale locale) {
        if (amount == null) {
            return "-";
        }
        DecimalFormat fmt = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale));
        return fmt.format(amount) + " " + (currency != null ? currency : "RSD");
    }

    private String percent(BigDecimal amount, Locale locale) {
        if (amount == null) {
            return "-";
        }
        DecimalFormat fmt = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale));
        return fmt.format(amount) + "%";
    }

    private BigDecimal sumItemsPrice(List<WorkOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
            .map(item -> item != null && item.getCalculatedPrice() != null ? item.getCalculatedPrice() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String resolveOrderStatus(WorkOrderDTO order, Locale locale) {
        if (order.getStatus() == null) return "-";
        try {
            return msg(order.getStatus().getMessageKey(), locale, order.getStatus().name());
        } catch (Exception ignored) {
            return order.getStatus().name();
        }
    }

    private String resolveQuoteStatus(WorkOrderDTO order, Locale locale) {
        if (order.getQuoteStatus() == null) return "NONE";
        try {
            return msg(order.getQuoteStatus().getMessageKey(), locale, order.getQuoteStatus().name());
        } catch (Exception ignored) {
            return order.getQuoteStatus().name();
        }
    }

    private String resolveDeliveryType(WorkOrderDTO order, Locale locale) {
        if (order.getDeliveryType() == null) return "-";
        String key = "orders.delivery_type." + order.getDeliveryType().name().toLowerCase(Locale.ROOT);
        return msg(key, locale, order.getDeliveryType().name());
    }

    private String resolveShipmentStatus(WorkOrderDTO order, Locale locale) {
        if (order.getShipmentStatus() == null) return "-";
        String key = "orders.shipment.status." + order.getShipmentStatus().name().toLowerCase(Locale.ROOT);
        return msg(key, locale, order.getShipmentStatus().name());
    }

    private String msg(String key, Locale locale, String fallback) {
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Font buildFont(int style, float size) {
        try {
            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1250, BaseFont.NOT_EMBEDDED);
            return new Font(base, size, style);
        } catch (Exception ex) {
            return FontFactory.getFont(FontFactory.HELVETICA, size, style);
        }
    }
}
