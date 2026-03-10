package com.printflow.service;

import com.printflow.dto.EmailMessage;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.events.DesignApprovalRequestedEvent;
import com.printflow.events.OrderCreatedEvent;
import com.printflow.events.OrderStatusChangedEvent;
import com.printflow.repository.WorkOrderRepository;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailEventListener {

    private final WorkOrderRepository workOrderRepository;
    private final CompanyBrandingService companyBrandingService;
    private final EmailService emailService;
    private final EmailTemplateService templateService;
    private final String baseUrl;

    public EmailEventListener(WorkOrderRepository workOrderRepository,
                              CompanyBrandingService companyBrandingService,
                              EmailService emailService,
                              EmailTemplateService templateService,
                              @Value("${app.base-url:http://localhost:8088}") String baseUrl) {
        this.workOrderRepository = workOrderRepository;
        this.companyBrandingService = companyBrandingService;
        this.emailService = emailService;
        this.templateService = templateService;
        this.baseUrl = baseUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCreated(OrderCreatedEvent event) {
        WorkOrder order = workOrderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null || order.getClient() == null || order.getClient().getEmail() == null) {
            return;
        }
        boolean serbian = isSerbianCompany(order.getCompany());
        Map<String, Object> model = buildOrderModel(order);
        String html = templateService.render("order-created", model);
        EmailMessage msg = new EmailMessage();
        msg.setTo(order.getClient().getEmail());
        msg.setSubject((serbian ? "Kreiran nalog: " : "Order created: ") + order.getOrderNumber());
        msg.setHtmlBody(html);
        msg.setTextBody(buildTextBody(order, "Your order has been created.", null, order.getStatus()));
        emailService.send(msg, order.getCompany(), "order-created");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        WorkOrder order = workOrderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null || order.getClient() == null || order.getClient().getEmail() == null) {
            return;
        }
        boolean serbian = isSerbianCompany(order.getCompany());
        Map<String, Object> model = buildOrderModel(order);
        model.put("oldStatus", event.getOldStatus());
        model.put("newStatus", event.getNewStatus());
        String html = templateService.render("order-status-changed", model);
        EmailMessage msg = new EmailMessage();
        msg.setTo(order.getClient().getEmail());
        msg.setSubject((serbian ? "Status naloga ažuriran: " : "Order status updated: ") + order.getOrderNumber());
        msg.setHtmlBody(html);
        msg.setTextBody(buildTextBody(order, "Your order status has been updated.", event.getOldStatus(), event.getNewStatus()));
        emailService.send(msg, order.getCompany(), "order-status-changed");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDesignApprovalRequested(DesignApprovalRequestedEvent event) {
        WorkOrder order = workOrderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null || order.getClient() == null || order.getClient().getEmail() == null) {
            return;
        }
        boolean serbian = isSerbianCompany(order.getCompany());
        Map<String, Object> model = buildOrderModel(order);
        String html = templateService.render("design-approval-request", model);
        EmailMessage msg = new EmailMessage();
        msg.setTo(order.getClient().getEmail());
        msg.setSubject((serbian ? "Potrebno odobrenje dizajna: " : "Design approval required: ") + order.getOrderNumber());
        msg.setHtmlBody(html);
        msg.setTextBody(buildTextBody(order, "Design approval is required for your order.", null, order.getStatus()));
        emailService.send(msg, order.getCompany(), "design-approval-request");
    }

    private Map<String, Object> buildOrderModel(WorkOrder order) {
        Map<String, Object> model = new HashMap<>();
        Company company = order.getCompany();
        model.put("orderNumber", order.getOrderNumber());
        model.put("orderTitle", order.getTitle());
        model.put("clientName", order.getClient() != null ? order.getClient().getCompanyName() : "");
        model.put("companyName", company != null ? company.getName() : "PrintFlow");
        model.put("trackingUrl", baseUrl + "/public/order/" + order.getPublicToken());
        model.put("logoUrl", buildLogoUrl(order));
        model.put("logoInline", buildInlineLogo(order));
        model.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : "");
        model.put("orderPrice", order.getPrice());
        model.put("currency", company != null && company.getCurrency() != null ? company.getCurrency() : "RSD");
        model.put("deadline", order.getDeadline());
        model.put("companyEmail", company != null ? company.getEmail() : null);
        model.put("companyPhone", company != null ? company.getPhone() : null);
        model.put("companyWebsite", company != null ? company.getWebsite() : null);
        model.put("companyAddress", company != null ? company.getAddress() : null);
        model.put("lang", isSerbianCompany(company) ? "sr" : "en");
        return model;
    }

    private String buildLogoUrl(WorkOrder order) {
        Company company = order.getCompany();
        if (company == null || company.getId() == null || company.getLogoPath() == null || company.getLogoPath().isBlank()) {
            return null;
        }
        return baseUrl + "/public/company-logo/" + company.getId();
    }

    private String buildInlineLogo(WorkOrder order) {
        Company company = order.getCompany();
        if (company == null || company.getId() == null || company.getLogoPath() == null || company.getLogoPath().isBlank()) {
            return null;
        }
        try {
            byte[] data = companyBrandingService.loadLogo(company.getId());
            String mime = MediaTypeFactory.getMediaType(company.getLogoPath())
                .map(Object::toString)
                .orElse("image/png");
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildTextBody(WorkOrder order, String intro, OrderStatus oldStatus, OrderStatus newStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append(intro).append("\n\n");
        sb.append("Order: ").append(order.getOrderNumber()).append("\n");
        sb.append("Title: ").append(order.getTitle()).append("\n");
        if (oldStatus != null && newStatus != null) {
            sb.append("Status: ").append(oldStatus).append(" -> ").append(newStatus).append("\n");
        } else if (newStatus != null) {
            sb.append("Status: ").append(newStatus).append("\n");
        }
        if (order.getPrice() != null) {
            String currency = order.getCompany() != null && order.getCompany().getCurrency() != null
                ? order.getCompany().getCurrency() : "RSD";
            sb.append("Price: ").append(String.format("%.2f", order.getPrice())).append(" ").append(currency).append("\n");
        }
        sb.append("Tracking link: ").append(baseUrl).append("/public/order/").append(order.getPublicToken()).append("\n\n");
        if (order.getCompany() != null && order.getCompany().getName() != null) {
            sb.append(order.getCompany().getName()).append("\n");
        }
        if (order.getCompany() != null && order.getCompany().getEmail() != null) {
            sb.append(order.getCompany().getEmail()).append("\n");
        }
        if (order.getCompany() != null && order.getCompany().getPhone() != null) {
            sb.append(order.getCompany().getPhone()).append("\n");
        }
        return sb.toString();
    }

    private boolean isSerbianCompany(Company company) {
        if (company == null) {
            return false;
        }
        if ("RSD".equalsIgnoreCase(company.getCurrency())) {
            return true;
        }
        String address = company.getAddress();
        if (address == null) {
            return false;
        }
        String lower = address.toLowerCase();
        return lower.contains("srbija") || lower.contains("serbia");
    }
}
