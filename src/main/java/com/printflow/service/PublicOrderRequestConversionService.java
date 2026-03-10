package com.printflow.service;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.dto.EmailMessage;
import com.printflow.entity.Attachment;
import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.PublicOrderRequest;
import com.printflow.entity.PublicOrderRequestAttachment;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.entity.enums.PrintType;
import com.printflow.entity.enums.PublicOrderRequestStatus;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.PublicOrderRequestAttachmentRepository;
import com.printflow.repository.PublicOrderRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PublicOrderRequestConversionService {

    private final PublicOrderRequestRepository requestRepository;
    private final PublicOrderRequestAttachmentRepository requestAttachmentRepository;
    private final ClientRepository clientRepository;
    private final WorkOrderService workOrderService;
    private final AttachmentRepository attachmentRepository;
    private final TenantGuard tenantGuard;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;
    private final String baseUrl;

    public PublicOrderRequestConversionService(PublicOrderRequestRepository requestRepository,
                                               PublicOrderRequestAttachmentRepository requestAttachmentRepository,
                                               ClientRepository clientRepository,
                                               WorkOrderService workOrderService,
                                               AttachmentRepository attachmentRepository,
                                               TenantGuard tenantGuard,
                                               EmailTemplateService emailTemplateService,
                                               EmailService emailService,
                                               @Value("${app.base-url:http://localhost:8088}") String baseUrl) {
        this.requestRepository = requestRepository;
        this.requestAttachmentRepository = requestAttachmentRepository;
        this.clientRepository = clientRepository;
        this.workOrderService = workOrderService;
        this.attachmentRepository = attachmentRepository;
        this.tenantGuard = tenantGuard;
        this.emailTemplateService = emailTemplateService;
        this.emailService = emailService;
        this.baseUrl = baseUrl;
    }

    public WorkOrder convertToOrder(Long requestId) {
        Long companyId = tenantGuard.requireCompanyId();
        PublicOrderRequest request = requestRepository.findByIdAndCompany_Id(requestId, companyId)
            .orElseThrow(() -> new RuntimeException("Zahtev nije pronađen"));

        if (request.getConvertedOrder() != null || request.getStatus() == PublicOrderRequestStatus.CONVERTED) {
            throw new RuntimeException("Zahtev je već konvertovan");
        }

        Client client = resolveOrCreateClient(request);
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setClientId(client.getId());
        dto.setCreatedById(currentUserId());
        dto.setTitle(buildOrderTitle(request));
        dto.setDescription(buildOrderDescription(request));
        dto.setSpecifications(buildOrderSpecifications(request));
        dto.setDeadline(request.getDeadline());
        dto.setPriority(5);
        dto.setPrintType(resolvePrintType(request.getProductType()));

        WorkOrderDTO created = workOrderService.createWorkOrder(dto);
        WorkOrder order = workOrderService.getWorkOrderEntityById(created.getId());

        copyRequestAttachmentsToOrder(request, order);

        request.setStatus(PublicOrderRequestStatus.CONVERTED);
        request.setConvertedAt(LocalDateTime.now());
        request.setConvertedOrder(order);
        requestRepository.save(request);
        sendConversionEmail(request, order, client);
        return order;
    }

    public WorkOrder getOrConvertToOrder(Long requestId) {
        Long companyId = tenantGuard.requireCompanyId();
        PublicOrderRequest request = requestRepository.findByIdAndCompany_Id(requestId, companyId)
            .orElseThrow(() -> new RuntimeException("Zahtev nije pronađen"));

        if (request.getConvertedOrder() != null) {
            return request.getConvertedOrder();
        }
        return convertToOrder(requestId);
    }

    private void copyRequestAttachmentsToOrder(PublicOrderRequest request, WorkOrder order) {
        List<PublicOrderRequestAttachment> requestFiles = requestAttachmentRepository.findByRequest_IdOrderByCreatedAtAsc(request.getId());
        if (requestFiles.isEmpty()) {
            return;
        }
        User current = tenantGuard.getCurrentUser();
        for (PublicOrderRequestAttachment file : requestFiles) {
            Attachment att = new Attachment();
            att.setCompany(order.getCompany());
            att.setWorkOrder(order);
            att.setUploadedBy(current);
            att.setFileName(file.getFileName());
            att.setFilePath(file.getFilePath());
            att.setOriginalFileName(file.getOriginalFileName());
            att.setMimeType(file.getMimeType());
            att.setFileSize(file.getFileSize());
            att.setFileType(file.getMimeType());
            att.setAttachmentType(AttachmentType.CLIENT_FILE);
            att.setDescription("Imported from public request #" + request.getId());
            attachmentRepository.save(att);
        }
    }

    private Client resolveOrCreateClient(PublicOrderRequest request) {
        Company company = request.getCompany();
        Long companyId = company.getId();
        String email = request.getCustomerEmail();
        String phone = request.getCustomerPhone();

        if (email != null && !email.isBlank()) {
            var existing = clientRepository.findByEmailAndCompany_Id(email, companyId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        if (phone != null && !phone.isBlank()) {
            var existing = clientRepository.findByPhoneAndCompany_Id(phone, companyId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Client client = new Client();
        client.setCompany(company);
        client.setCompanyName(
            request.getCustomerCompanyName() != null && !request.getCustomerCompanyName().isBlank()
                ? request.getCustomerCompanyName()
                : request.getCustomerName()
        );
        client.setContactPerson(request.getCustomerName());
        client.setEmail(email);
        client.setPhone(phone != null && !phone.isBlank() ? phone : "-");
        client.setCountry("Serbia");
        client.setNotes("Auto-created from public request #" + request.getId());
        return clientRepository.save(client);
    }

    private String buildOrderTitle(PublicOrderRequest request) {
        String customer = request.getCustomerCompanyName() != null && !request.getCustomerCompanyName().isBlank()
            ? request.getCustomerCompanyName()
            : request.getCustomerName();
        return request.getProductType() + " - " + customer;
    }

    private String buildOrderDescription(PublicOrderRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Public request #").append(request.getId()).append("\n");
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            sb.append(request.getNotes());
        }
        return sb.toString();
    }

    private String buildOrderSpecifications(PublicOrderRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Product: ").append(request.getProductType()).append("\n");
        sb.append("Quantity: ").append(request.getQuantity()).append("\n");
        if (request.getDimensions() != null) {
            sb.append("Dimensions: ").append(request.getDimensions()).append("\n");
        }
        if (request.getMaterial() != null) {
            sb.append("Material: ").append(request.getMaterial()).append("\n");
        }
        if (request.getFinishing() != null) {
            sb.append("Finishing: ").append(request.getFinishing()).append("\n");
        }
        return sb.toString();
    }

    private PrintType resolvePrintType(String productType) {
        if (productType == null) {
            return PrintType.OTHER;
        }
        String normalized = productType.trim().toUpperCase();
        for (PrintType type : PrintType.values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        if (normalized.contains("DTF")) return PrintType.DTF;
        if (normalized.contains("LASER")) return PrintType.LASER;
        return PrintType.OTHER;
    }

    private Long currentUserId() {
        User current = tenantGuard.getCurrentUser();
        return current != null ? current.getId() : null;
    }

    private void sendConversionEmail(PublicOrderRequest request, WorkOrder order, Client client) {
        String to = request.getCustomerEmail();
        if (to == null || to.isBlank() || order == null || order.getPublicToken() == null || order.getPublicToken().isBlank()) {
            return;
        }
        Company company = request.getCompany();
        boolean serbian = isSerbianCompany(company);
        String lang = serbian ? "sr" : "en";
        String trackingUrl = baseUrl + "/public/order/" + order.getPublicToken();

        Map<String, Object> model = new HashMap<>();
        model.put("lang", lang);
        model.put("orderNumber", order.getOrderNumber());
        model.put("orderTitle", order.getTitle());
        model.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : "NEW");
        model.put("trackingUrl", trackingUrl);
        model.put("clientName", client != null && client.getContactPerson() != null && !client.getContactPerson().isBlank()
            ? client.getContactPerson()
            : request.getCustomerName());
        model.put("companyName", company != null ? company.getName() : "PrintFlow");
        model.put("companyEmail", company != null ? company.getEmail() : null);
        model.put("companyPhone", company != null ? company.getPhone() : null);
        model.put("companyWebsite", company != null ? company.getWebsite() : null);
        model.put("companyAddress", company != null ? company.getAddress() : null);
        model.put("logoUrl", company != null && company.getLogoPath() != null && !company.getLogoPath().isBlank()
            ? baseUrl + "/public/company-logo/" + company.getId()
            : null);
        model.put("logoInline", null);

        String html = emailTemplateService.render("public-tracking-link.html", model);
        EmailMessage message = new EmailMessage();
        message.setTo(to);
        message.setSubject(serbian
            ? "Vaš zahtev je prihvaćen: " + order.getOrderNumber()
            : "Your request was accepted: " + order.getOrderNumber());
        message.setHtmlBody(html);
        message.setTextBody((serbian ? "Vaš nalog je kreiran. Pratite status: " : "Your order has been created. Track status: ") + trackingUrl);
        emailService.send(message, company, "public-request-converted");
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
