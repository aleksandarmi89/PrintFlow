package com.printflow.controller;

import com.printflow.entity.PublicOrderRequest;
import com.printflow.entity.PublicOrderRequestAttachment;
import com.printflow.entity.enums.PrintType;
import com.printflow.entity.enums.PublicOrderRequestSourceChannel;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.PublicOrderRequestStatus;
import com.printflow.config.PaginationConfig;
import com.printflow.entity.enums.AuditAction;
import com.printflow.entity.User;
import com.printflow.service.AuditLogService;
import com.printflow.service.TenantContextService;
import com.printflow.service.PublicOrderRequestConversionService;
import com.printflow.service.PublicOrderRequestService;
import com.printflow.service.TaskService;
import com.printflow.service.UserService;
import com.printflow.service.EmailService;
import com.printflow.dto.TaskDTO;
import com.printflow.dto.UserDTO;
import com.printflow.dto.EmailMessage;
import com.printflow.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.i18n.LocaleContextHolder;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Controller
@RequestMapping("/admin/public-requests")
public class AdminPublicOrderRequestController extends BaseController {

    private final PublicOrderRequestService requestService;
    private final PublicOrderRequestConversionService conversionService;
    private final PaginationConfig paginationConfig;
    private final AuditLogService auditLogService;
    private final TenantContextService tenantContextService;
    private final TaskService taskService;
    private final UserService userService;
    private final TaskRepository taskRepository;
    private final EmailService emailService;

    public AdminPublicOrderRequestController(PublicOrderRequestService requestService,
                                             PublicOrderRequestConversionService conversionService,
                                             PaginationConfig paginationConfig,
                                             AuditLogService auditLogService,
                                             TenantContextService tenantContextService,
                                             TaskService taskService,
                                             UserService userService,
                                             TaskRepository taskRepository,
                                             EmailService emailService) {
        this.requestService = requestService;
        this.conversionService = conversionService;
        this.paginationConfig = paginationConfig;
        this.auditLogService = auditLogService;
        this.tenantContextService = tenantContextService;
        this.taskService = taskService;
        this.userService = userService;
        this.taskRepository = taskRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String search,
                       @RequestParam(required = false, defaultValue = "false") boolean resetFilters,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) Integer size,
                       HttpSession session,
                       Model model) {
        String normalizedStatusInput = normalizeOptional(status);
        String normalizedSearchInput = normalizeOptional(search);
        if (resetFilters) {
            session.removeAttribute("publicRequests.statusFilter");
            session.removeAttribute("publicRequests.searchFilter");
            normalizedStatusInput = null;
            normalizedSearchInput = null;
        } else {
            if (normalizedStatusInput == null) {
                normalizedStatusInput = normalizeOptional((String) session.getAttribute("publicRequests.statusFilter"));
            } else {
                session.setAttribute("publicRequests.statusFilter", normalizedStatusInput);
            }
            if (normalizedSearchInput == null) {
                normalizedSearchInput = normalizeOptional((String) session.getAttribute("publicRequests.searchFilter"));
            } else {
                session.setAttribute("publicRequests.searchFilter", normalizedSearchInput);
            }
        }

        PublicOrderRequestStatus statusFilter = parseStatus(normalizedStatusInput);
        String normalizedStatus = statusFilter != null ? statusFilter.name() : null;
        if (normalizedStatusInput != null && statusFilter == null) {
            session.removeAttribute("publicRequests.statusFilter");
        } else if (normalizedStatus != null) {
            session.setAttribute("publicRequests.statusFilter", normalizedStatus);
        }
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        Page<PublicOrderRequest> requests = requestService.listForCurrentTenant(
            statusFilter,
            normalizedSearchInput,
            PageRequest.of(safePage, pageSize, Sort.by("createdAt").descending()));
        model.addAttribute("requestsPage", requests);
        model.addAttribute("statusFilter", normalizedStatus);
        model.addAttribute("search", normalizedSearchInput);
        model.addAttribute("allStatuses", PublicOrderRequestStatus.values());
        Map<String, String> statusOptionLabels = new HashMap<>();
        for (PublicOrderRequestStatus requestStatus : PublicOrderRequestStatus.values()) {
            statusOptionLabels.put(requestStatus.name(), localizeStatus(requestStatus));
        }
        model.addAttribute("statusOptionLabels", statusOptionLabels);
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("now", now);
        Map<Long, String> slaState = new HashMap<>();
        Map<Long, String> slaLabel = new HashMap<>();
        Map<Long, String> statusLabel = new HashMap<>();
        Map<Long, String> sourceLabel = new HashMap<>();
        for (PublicOrderRequest request : requests.getContent()) {
            statusLabel.put(request.getId(), localizeStatus(request.getStatus()));
            sourceLabel.put(request.getId(), localizeSource(request.getSourceChannel()));
            LocalDateTime deadline = request.getDeadline();
            if (deadline == null) {
                slaState.put(request.getId(), "NO_DEADLINE");
                slaLabel.put(request.getId(), tr("Bez roka", "No deadline"));
                continue;
            }
            if (deadline.isBefore(now)) {
                slaState.put(request.getId(), "OVERDUE");
                slaLabel.put(request.getId(), tr("Istekao rok", "Overdue"));
                continue;
            }
            if (deadline.isBefore(now.plusHours(24))) {
                slaState.put(request.getId(), "DUE_TODAY");
                slaLabel.put(request.getId(), tr("Rok < 24h", "Due < 24h"));
                continue;
            }
            if (deadline.isBefore(now.plusDays(3))) {
                slaState.put(request.getId(), "DUE_SOON");
                slaLabel.put(request.getId(), tr("Rok uskoro", "Due soon"));
                continue;
            }
            slaState.put(request.getId(), "ON_TRACK");
            slaLabel.put(request.getId(), tr("Na vreme", "On track"));
        }
        model.addAttribute("slaState", slaState);
        model.addAttribute("slaLabel", slaLabel);
        model.addAttribute("statusLabel", statusLabel);
        model.addAttribute("sourceLabel", sourceLabel);
        return "admin/public-requests/list";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        PublicOrderRequest request = requestService.getForCurrentTenant(id);
        model.addAttribute("requestItem", request);
        model.addAttribute("attachments", requestService.getAttachmentsForCurrentTenantRequest(id));
        model.addAttribute("convertedOrderId", request.getConvertedOrder() != null ? request.getConvertedOrder().getId() : null);
        model.addAttribute("convertedOrderNumber", request.getConvertedOrder() != null ? request.getConvertedOrder().getOrderNumber() : null);
        model.addAttribute("requestStatusLabel", localizeStatus(request.getStatus()));
        model.addAttribute("requestSourceLabel", localizeSource(request.getSourceChannel()));
        model.addAttribute("allStatuses", PublicOrderRequestStatus.values());
        Map<String, String> statusOptionLabels = new HashMap<>();
        for (PublicOrderRequestStatus requestStatus : PublicOrderRequestStatus.values()) {
            statusOptionLabels.put(requestStatus.name(), localizeStatus(requestStatus));
        }
        model.addAttribute("statusOptionLabels", statusOptionLabels);
        model.addAttribute("requestAuditLogs", auditLogService.getByEntity("PublicOrderRequest", id));
        return "admin/public-requests/details";
    }

    @PostMapping("/bulk-action")
    public String bulkAction(@RequestParam(name = "requestIds", required = false) List<Long> requestIds,
                             @RequestParam(name = "action", defaultValue = "none") String action,
                             Model model) {
        if (requestIds == null || requestIds.isEmpty()) {
            return redirectWithError("/admin/public-requests", tr("Izaberite bar jedan zahtev.", "Select at least one request."), model);
        }
        if ("none".equalsIgnoreCase(action)) {
            return redirectWithError("/admin/public-requests", tr("Izaberite bulk akciju.", "Select bulk action."), model);
        }
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : requestIds) {
            try {
                switch (action) {
                    case "convert" -> conversionService.getOrConvertToOrder(id);
                    case "auto-basic" -> generateTasks(conversionService.getOrConvertToOrder(id), "basic");
                    case "auto-advanced" -> generateTasks(conversionService.getOrConvertToOrder(id), "advanced");
                    default -> throw new IllegalArgumentException("Unknown action");
                }
                success++;
            } catch (Exception ex) {
                failed++;
                errors.add("#" + id + ": " + ex.getMessage());
            }
        }
        String message = tr(
            "Bulk akcija završena. Uspešno: " + success + ", neuspešno: " + failed,
            "Bulk action completed. Success: " + success + ", failed: " + failed
        );
        if (!errors.isEmpty()) {
            message += " | " + String.join(" ; ", errors.stream().limit(3).toList());
        }
        return failed == 0
            ? redirectWithSuccess("/admin/public-requests", message, model)
            : redirectWithError("/admin/public-requests", message, model);
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam String status,
                               Model model) {
        try {
            PublicOrderRequest request = requestService.getForCurrentTenant(id);
            PublicOrderRequestStatus oldStatus = request.getStatus();
            PublicOrderRequestStatus newStatus = parseStatus(status);
            if (newStatus == null) {
                return redirectWithError("/admin/public-requests/" + id, tr("Neispravan status.", "Invalid status."), model);
            }
            requestService.updateStatus(id, newStatus);
            auditLogService.log(AuditAction.STATUS_CHANGE, "PublicOrderRequest", id,
                oldStatus != null ? oldStatus.name() : null,
                newStatus.name(),
                "Public request status changed by admin",
                tenantContextService.getCurrentCompany());
            return redirectWithSuccess("/admin/public-requests/" + id, "Status updated", model);
        } catch (Exception ex) {
            return redirectWithError("/admin/public-requests/" + id, "Status update failed: " + ex.getMessage(), model);
        }
    }

    @PostMapping("/{id}/convert")
    public String convert(@PathVariable Long id, Model model) {
        try {
            WorkOrder created = conversionService.convertToOrder(id);
            auditLogService.log(AuditAction.CREATE, "PublicOrderRequest", id,
                null, "orderId:" + created.getId(),
                "Public request converted to order",
                tenantContextService.getCurrentCompany());
            return redirectWithSuccess("/admin/orders/" + created.getId(), "Request converted to order", model);
        } catch (Exception ex) {
            return redirectWithError("/admin/public-requests/" + id, "Conversion failed: " + ex.getMessage(), model);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Model model) {
        try {
            requestService.deleteForCurrentTenant(id);
            auditLogService.log(AuditAction.DELETE, "PublicOrderRequest", id,
                null, null,
                "Public request deleted by admin",
                tenantContextService.getCurrentCompany());
            return redirectWithSuccess("/admin/public-requests", tr("Zahtev je obrisan.", "Request deleted."), model);
        } catch (Exception ex) {
            return redirectWithError("/admin/public-requests/" + id, tr("Brisanje nije uspelo: ", "Delete failed: ") + ex.getMessage(), model);
        }
    }

    @PostMapping("/{id}/convert-and-create-task")
    public String convertAndCreateTask(@PathVariable Long id, Model model) {
        try {
            WorkOrder created = conversionService.getOrConvertToOrder(id);
            auditLogService.log(AuditAction.UPDATE, "PublicOrderRequest", id,
                null, "orderId:" + created.getId(),
                "Public request convert+create-task action",
                tenantContextService.getCurrentCompany());
            return redirectWithSuccess("/admin/tasks/create?workOrderId=" + created.getId(),
                "Request ready. Create task for worker.", model);
        } catch (Exception ex) {
            return redirectWithError("/admin/public-requests/" + id, "Action failed: " + ex.getMessage(), model);
        }
    }

    @PostMapping("/{id}/convert-and-generate-tasks")
    public String convertAndGenerateTasks(@PathVariable Long id,
                                          @RequestParam(name = "template", defaultValue = "basic") String template,
                                          Model model) {
        try {
            WorkOrder order = conversionService.getOrConvertToOrder(id);
            int generated = generateTasks(order, template);
            sendAdminTaskSummaryEmail(order, generated, normalizeTemplate(template));
            auditLogService.log(AuditAction.CREATE, "PublicOrderRequest", id,
                null, "orderId:" + order.getId() + ",tasks:" + generated,
                "Public request converted and tasks generated, template=" + normalizeTemplate(template),
                tenantContextService.getCurrentCompany());
            return redirectWithSuccess("/admin/tasks?workOrderId=" + order.getId(),
                tr("Kreirano " + generated + " taskova.", "Generated " + generated + " tasks."), model);
        } catch (Exception ex) {
            return redirectWithError("/admin/public-requests/" + id, tr("Generisanje taskova nije uspelo: ", "Task generation failed: ") + ex.getMessage(), model);
        }
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @ResponseBody
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long attachmentId) {
        try {
            PublicOrderRequestAttachment attachment = requestService.getAttachmentForCurrentTenant(attachmentId);
            byte[] data = requestService.loadAttachmentContent(attachment);
            String downloadName = attachment.getOriginalFileName() != null && !attachment.getOriginalFileName().isBlank()
                ? attachment.getOriginalFileName()
                : attachment.getFileName();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, requestService.resolveAttachmentMimeType(attachment))
                .body(data);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private PublicOrderRequestStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PublicOrderRequestStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int generateTasks(WorkOrder order, String template) {
        String normalizedTemplate = normalizeTemplate(template);
        if (order == null || order.getId() == null) {
            return 0;
        }
        Long companyId = tenantContextService.requireCompanyId();
        Long createdById = tenantContextService.getCurrentUser() != null ? tenantContextService.getCurrentUser().getId() : null;
        List<com.printflow.entity.Task> existing = taskRepository.findByWorkOrderIdAndCompany_Id(order.getId(), companyId);
        List<UserDTO> workers = userService.getWorkers();
        int created = 0;

        if (shouldCreateDesignTask(order) && !hasTaskTitle(existing, "Priprema dizajna")) {
            TaskDTO task = new TaskDTO();
            task.setTitle("Priprema dizajna - " + order.getOrderNumber());
            task.setDescription("Pripremiti dizajn i poslati na proveru.");
            task.setPriority("MEDIUM");
            task.setDueDate(LocalDateTime.now().plusDays(1));
            task.setEstimatedHours(2.0);
            Long assigned = suggestWorker(workers, "WORKER_DESIGN");
            taskService.createTask(task, assigned, order.getId(), createdById);
            created++;
        }

        if (!hasTaskTitle(existing, "Stampa i proizvodnja")) {
            TaskDTO task = new TaskDTO();
            task.setTitle("Stampa i proizvodnja - " + order.getOrderNumber());
            task.setDescription("Izvršiti štampu/preparaciju za nalog.");
            task.setPriority("HIGH");
            task.setDueDate(LocalDateTime.now().plusDays(2));
            task.setEstimatedHours(3.0);
            Long assigned = suggestWorker(workers, order.getPrintType() == PrintType.OTHER ? "WORKER_GENERAL" : "WORKER_PRINT");
            taskService.createTask(task, assigned, order.getId(), createdById);
            created++;
        }

        if (!hasTaskTitle(existing, "Kontrola kvaliteta")) {
            TaskDTO task = new TaskDTO();
            task.setTitle("Kontrola kvaliteta - " + order.getOrderNumber());
            task.setDescription("Proveriti kvalitet i potvrditi spremnost.");
            task.setPriority("MEDIUM");
            task.setDueDate(LocalDateTime.now().plusDays(3));
            task.setEstimatedHours(1.0);
            Long assigned = suggestWorker(workers, "WORKER_GENERAL");
            taskService.createTask(task, assigned, order.getId(), createdById);
            created++;
        }

        if ("advanced".equals(normalizedTemplate) && !hasTaskTitle(existing, "Pakovanje i isporuka")) {
            TaskDTO task = new TaskDTO();
            task.setTitle("Pakovanje i isporuka - " + order.getOrderNumber());
            task.setDescription("Pripremiti pakovanje, obeležavanje i isporuku.");
            task.setPriority("MEDIUM");
            task.setDueDate(LocalDateTime.now().plusDays(4));
            task.setEstimatedHours(1.5);
            Long assigned = suggestWorker(workers, "WORKER_GENERAL");
            taskService.createTask(task, assigned, order.getId(), createdById);
            created++;
        }

        if ("advanced".equals(normalizedTemplate) && !hasTaskTitle(existing, "Zatvaranje naloga")) {
            TaskDTO task = new TaskDTO();
            task.setTitle("Zatvaranje naloga - " + order.getOrderNumber());
            task.setDescription("Finalna provera, dokumentacija i zatvaranje naloga.");
            task.setPriority("LOW");
            task.setDueDate(LocalDateTime.now().plusDays(5));
            task.setEstimatedHours(1.0);
            Long assigned = suggestWorker(workers, "WORKER_GENERAL");
            taskService.createTask(task, assigned, order.getId(), createdById);
            created++;
        }
        return created;
    }

    private String normalizeTemplate(String template) {
        if (template == null) {
            return "basic";
        }
        return "advanced".equalsIgnoreCase(template) ? "advanced" : "basic";
    }

    private void sendAdminTaskSummaryEmail(WorkOrder order, int generated, String template) {
        try {
            User current = tenantContextService.getCurrentUser();
            if (current == null || current.getEmail() == null || current.getEmail().isBlank()) {
                return;
            }
            boolean sr = "sr".equalsIgnoreCase(current.getLanguagePreference());
            String subject = sr
                ? "Auto taskovi kreirani: " + order.getOrderNumber()
                : "Auto tasks created: " + order.getOrderNumber();
            String orderUrl = "/admin/orders/" + order.getId();
            String html = sr
                ? "<p>Uspešno je kreirano <strong>" + generated + "</strong> taskova.</p><p>Template: <strong>" + template + "</strong></p><p>Order: <strong>" + order.getOrderNumber() + "</strong></p><p><a href=\"" + orderUrl + "\">Otvori nalog</a></p>"
                : "<p>Successfully created <strong>" + generated + "</strong> tasks.</p><p>Template: <strong>" + template + "</strong></p><p>Order: <strong>" + order.getOrderNumber() + "</strong></p><p><a href=\"" + orderUrl + "\">Open order</a></p>";
            EmailMessage message = new EmailMessage();
            message.setTo(current.getEmail());
            message.setSubject(subject);
            message.setHtmlBody(html);
            message.setTextBody((sr ? "Kreirano " : "Created ") + generated + " tasks. " + orderUrl);
            emailService.send(message, tenantContextService.getCurrentCompany(), "public-request-auto-tasks-summary");
        } catch (Exception ignored) {
        }
    }

    private String tr(String sr, String en) {
        String language = LocaleContextHolder.getLocale() != null ? LocaleContextHolder.getLocale().getLanguage() : null;
        boolean isSr = "sr".equalsIgnoreCase(language);
        return isSr ? sr : en;
    }

    private boolean shouldCreateDesignTask(WorkOrder order) {
        if (order == null) {
            return false;
        }
        if (order.getStatus() != null) {
            return switch (order.getStatus()) {
                case NEW, IN_DESIGN, WAITING_CLIENT_APPROVAL -> true;
                default -> order.getPrintType() == PrintType.OTHER;
            };
        }
        return order.getPrintType() == PrintType.OTHER;
    }

    private boolean hasTaskTitle(List<com.printflow.entity.Task> existing, String startsWith) {
        if (existing == null || existing.isEmpty()) {
            return false;
        }
        return existing.stream()
            .map(com.printflow.entity.Task::getTitle)
            .filter(t -> t != null)
            .anyMatch(t -> t.startsWith(startsWith + " - "));
    }

    private Long suggestWorker(List<UserDTO> workers, String preferredRole) {
        if (workers == null || workers.isEmpty()) {
            return null;
        }
        UserDTO preferredAvailable = workers.stream()
            .filter(w -> preferredRole.equalsIgnoreCase(w.getRole()))
            .filter(UserDTO::isAvailable)
            .findFirst()
            .orElse(null);
        if (preferredAvailable != null) {
            return preferredAvailable.getId();
        }
        UserDTO preferredAny = workers.stream()
            .filter(w -> preferredRole.equalsIgnoreCase(w.getRole()))
            .findFirst()
            .orElse(null);
        if (preferredAny != null) {
            return preferredAny.getId();
        }
        return workers.stream().filter(UserDTO::isAvailable).map(UserDTO::getId).findFirst()
            .orElse(workers.get(0).getId());
    }

    private String localizeStatus(PublicOrderRequestStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case NEW -> tr("NOV", "NEW");
            case UNDER_REVIEW -> tr("U OBRADI", "UNDER REVIEW");
            case AWAITING_CUSTOMER -> tr("ČEKA KLIJENTA", "AWAITING CUSTOMER");
            case APPROVED_FOR_CONVERSION -> tr("ODOBREN ZA KONVERZIJU", "APPROVED FOR CONVERSION");
            case REJECTED -> tr("ODBIJEN", "REJECTED");
            case CONVERTED -> tr("KONVERTOVAN", "CONVERTED");
        };
    }

    private String localizeSource(PublicOrderRequestSourceChannel source) {
        if (source == null) {
            return "-";
        }
        return switch (source) {
            case PUBLIC_FORM -> tr("JAVNI FORMULAR", "PUBLIC FORM");
            case ADMIN_MANUAL -> tr("ADMIN RUČNO", "ADMIN MANUAL");
            case PHONE -> tr("TELEFON", "PHONE");
            case EMAIL -> tr("EMAIL", "EMAIL");
            case WHATSAPP -> tr("WHATSAPP", "WHATSAPP");
        };
    }
}
