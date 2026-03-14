package com.printflow.controller;

import com.printflow.dto.*;
import com.printflow.entity.User;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.config.PaginationConfig;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.service.*;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.PublicOrderRequestRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaTypeFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin")
public class AdminController extends BaseController {

    private final DashboardService dashboardService;
    private final ClientService clientService;
    private final WorkOrderService workOrderService;
    private final UserService userService;
    private final ExcelImportService excelImportService;
    private final FileStorageService fileStorageService;
    private final CompanyService companyService;
    private final TenantContextService tenantContextService;
    private final TaskService taskService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final PaginationConfig paginationConfig;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final WorkOrderProfitService workOrderProfitService;
    private final ClientPortalService clientPortalService;
    private final ActivityLogService activityLogService;
    private final ProductVariantRepository productVariantRepository;
    private final ClientPricingProfileService clientPricingProfileService;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;
    private final CompanyBrandingService companyBrandingService;
    private final PublicOrderRequestRepository publicOrderRequestRepository;
    private final String baseUrl;

    public AdminController(DashboardService dashboardService,
                           ClientService clientService,
                           WorkOrderService workOrderService,
                           UserService userService,
                           ExcelImportService excelImportService,
                           FileStorageService fileStorageService,
                           CompanyService companyService,
                           TenantContextService tenantContextService,
                           TaskService taskService,
                           AuditLogService auditLogService,
                           NotificationService notificationService,
                           PaginationConfig paginationConfig,
                           WorkOrderItemRepository workOrderItemRepository,
                           WorkOrderProfitService workOrderProfitService,
                           ClientPortalService clientPortalService,
                           ActivityLogService activityLogService,
                           ProductVariantRepository productVariantRepository,
                           ClientPricingProfileService clientPricingProfileService,
                           EmailTemplateService emailTemplateService,
                           EmailService emailService,
                           CompanyBrandingService companyBrandingService,
                           PublicOrderRequestRepository publicOrderRequestRepository,
                           @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8088}") String baseUrl) {
        this.dashboardService = dashboardService;
        this.clientService = clientService;
        this.workOrderService = workOrderService;
        this.userService = userService;
        this.excelImportService = excelImportService;
        this.fileStorageService = fileStorageService;
        this.companyService = companyService;
        this.tenantContextService = tenantContextService;
        this.taskService = taskService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.paginationConfig = paginationConfig;
        this.workOrderItemRepository = workOrderItemRepository;
        this.workOrderProfitService = workOrderProfitService;
        this.clientPortalService = clientPortalService;
        this.activityLogService = activityLogService;
        this.productVariantRepository = productVariantRepository;
        this.clientPricingProfileService = clientPricingProfileService;
        this.emailTemplateService = emailTemplateService;
        this.emailService = emailService;
        this.companyBrandingService = companyBrandingService;
        this.publicOrderRequestRepository = publicOrderRequestRepository;
        this.baseUrl = baseUrl;
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardStatsDTO stats = dashboardService.getDashboardStats();
        model.addAttribute("stats", stats);

        List<WorkOrderDTO> recentOrders = workOrderService.getRecentWorkOrders(10);
        model.addAttribute("recentOrders", recentOrders);

        List<WorkOrderDTO> overdueOrders = workOrderService.getOverdueOrders(5);
        model.addAttribute("overdueOrders", overdueOrders);

        return "admin/dashboard";
    }

    // ==================== CLIENTS ====================

    @GetMapping("/clients")
    public String clients(Model model,
                          @RequestParam(required = false) String search,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size) {
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());

        org.springframework.data.domain.Page<ClientDTO> clientsPage;
        if (search != null && !search.trim().isEmpty()) {
            clientsPage = clientService.searchClients(search.trim(), pageable);
            model.addAttribute("search", search.trim());
        } else {
            clientsPage = clientService.getActiveClients(pageable);
        }
        if (safePage >= clientsPage.getTotalPages() && clientsPage.getTotalPages() > 0) {
            safePage = clientsPage.getTotalPages() - 1;
            pageable = org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
            if (search != null && !search.trim().isEmpty()) {
                clientsPage = clientService.searchClients(search.trim(), pageable);
            } else {
                clientsPage = clientService.getActiveClients(pageable);
            }
        }

        model.addAttribute("clientsPage", clientsPage);
        model.addAttribute("clients", clientsPage.getContent());
        model.addAttribute("currentPage", clientsPage.getNumber());
        model.addAttribute("totalPages", clientsPage.getTotalPages());
        model.addAttribute("totalItems", clientsPage.getTotalElements());
        model.addAttribute("lastPage", Math.max(0, clientsPage.getTotalPages() - 1));
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        model.addAttribute("totalClients", clientService.getTotalActiveClients());
        return "admin/clients/list";
    }

    @GetMapping("/clients/create")
    public String createClientForm(Model model) {
        model.addAttribute("client", new ClientDTO());
        return "admin/clients/create";
    }

    @PostMapping("/clients/create")
    public String createClient(@ModelAttribute ClientDTO clientDTO, Model model) {
        try {
            clientService.createClient(clientDTO);
            return redirectWithSuccess("/admin/clients", "Client created successfully", model);
        } catch (Exception e) {
            return redirectWithError("/admin/clients/create", "Error creating client: " + e.getMessage(), model);
        }
    }

    @GetMapping("/clients/edit/{id}")
    public String editClientForm(@PathVariable Long id, Model model) {
        try {
            ClientDTO client = clientService.getClientById(id);
            model.addAttribute("client", client);
            Long companyId = tenantContextService.requireCompanyId();
            model.addAttribute("pricingVariants", productVariantRepository.findAllByCompany_Id(companyId));
            model.addAttribute("pricingProfiles", clientPricingProfileService.getProfilesForClient(id, companyId));
            var access = clientPortalService.getAccessForClient(id, tenantContextService.requireCompanyId());
            if (access != null) {
                model.addAttribute("portalLink", baseUrl + "/portal/" + access.getAccessToken());
            }
            return "admin/clients/edit";
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/clients", "Client not found", model);
        }
    }

    @PostMapping("/clients/{id}/portal-link")
    public String generatePortalLink(@PathVariable Long id, Model model) {
        try {
            ClientDTO client = clientService.getClientById(id);
            var access = clientPortalService.createOrRefreshAccess(
                clientService.getClientEntity(id),
                tenantContextService.getCurrentCompany(),
                java.time.LocalDateTime.now().plusDays(30)
            );
            model.addAttribute("client", client);
            model.addAttribute("portalLink", baseUrl + "/portal/" + access.getAccessToken());
            model.addAttribute("successMessage", "Portal link generated.");
            return "admin/clients/edit";
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/clients", "Error generating portal link: " + e.getMessage(), model);
        }
    }

    @PostMapping("/clients/{id}/pricing-profile")
    public String updateClientPricingProfile(@PathVariable Long id,
                                              @RequestParam Long variantId,
                                              @RequestParam(required = false) java.math.BigDecimal discountPercent,
                                              Model model) {
        try {
            Long companyId = tenantContextService.requireCompanyId();
            clientPricingProfileService.upsertDiscount(id, variantId, companyId, discountPercent);
            return redirectWithSuccess("/admin/clients/edit/" + id, "Pricing profile updated.", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/clients/edit/" + id, "Error updating pricing profile: " + e.getMessage(), model);
        }
    }

    @PostMapping("/clients/edit/{id}")
    public String updateClient(@PathVariable Long id, @ModelAttribute ClientDTO clientDTO, Model model) {
        try {
            clientService.updateClient(id, clientDTO);
            return redirectWithSuccess("/admin/clients", "Client updated successfully", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/clients/edit/" + id, "Error updating client: " + e.getMessage(), model);
        }
    }

    @PostMapping("/clients/delete/{id}")
    public String deleteClient(@PathVariable Long id, Model model) {
        try {
            clientService.deleteClient(id);
            return redirectWithSuccess("/admin/clients", "Client deleted", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/clients", "Error deleting client: " + e.getMessage(), model);
        }
    }

    // ==================== ORDERS ====================

    @GetMapping("/orders")
    public String orders(Model model,
                         @RequestParam(required = false) String search,
                         @RequestParam(required = false) OrderStatus status,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) Integer size) {
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);

        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<WorkOrderDTO> ordersPage;

        if (search != null && !search.trim().isEmpty()) {
            ordersPage = workOrderService.searchWorkOrders(search.trim(), pageable);
            model.addAttribute("search", search.trim());
        } else if (status != null) {
            ordersPage = workOrderService.getWorkOrdersByStatus(status, pageable);
            model.addAttribute("status", status);
        } else {
            ordersPage = workOrderService.getWorkOrders(pageable);
        }

        if (safePage >= ordersPage.getTotalPages() && ordersPage.getTotalPages() > 0) {
            safePage = ordersPage.getTotalPages() - 1;
            pageable = org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
            if (search != null && !search.trim().isEmpty()) {
                ordersPage = workOrderService.searchWorkOrders(search.trim(), pageable);
            } else if (status != null) {
                ordersPage = workOrderService.getWorkOrdersByStatus(status, pageable);
            } else {
                ordersPage = workOrderService.getWorkOrders(pageable);
            }
        }

        model.addAttribute("ordersPage", ordersPage);
        model.addAttribute("orders", ordersPage.getContent());
        java.util.Map<Long, Long> orderToPublicRequestId = new java.util.HashMap<>();
        java.util.List<Long> orderIds = ordersPage.getContent().stream().map(WorkOrderDTO::getId).toList();
        if (!orderIds.isEmpty()) {
            publicOrderRequestRepository.findByConvertedOrder_IdIn(orderIds)
                .forEach(req -> {
                    if (req.getConvertedOrder() != null) {
                        orderToPublicRequestId.put(req.getConvertedOrder().getId(), req.getId());
                    }
                });
        }
        model.addAttribute("orderToPublicRequestId", orderToPublicRequestId);
        model.addAttribute("currentPage", ordersPage.getNumber());
        model.addAttribute("totalPages", ordersPage.getTotalPages());
        model.addAttribute("totalItems", ordersPage.getTotalElements());
        model.addAttribute("lastPage", Math.max(0, ordersPage.getTotalPages() - 1));
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        java.util.List<OrderStatus> filterStatuses = java.util.List.of(
            OrderStatus.NEW,
            OrderStatus.IN_DESIGN,
            OrderStatus.WAITING_CLIENT_APPROVAL,
            OrderStatus.APPROVED_FOR_PRINT,
            OrderStatus.IN_PRINT,
            OrderStatus.READY_FOR_DELIVERY,
            OrderStatus.SENT,
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED
        );
        model.addAttribute("orderStatuses", filterStatuses);
        model.addAttribute("newCount", workOrderService.countByStatus(OrderStatus.NEW));
        model.addAttribute("designCount", workOrderService.countByStatus(OrderStatus.IN_DESIGN));
        model.addAttribute("printCount", workOrderService.countByStatus(OrderStatus.IN_PRINT));
        model.addAttribute("readyCount", workOrderService.countByStatus(OrderStatus.READY_FOR_DELIVERY));
        model.addAttribute("overdueCount", workOrderService.countOverdueOrders());
        model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
            ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
        return "admin/orders/list";
    }

    @GetMapping("/orders/create")
    public String createOrderForm(Model model) {
        model.addAttribute("order", new WorkOrderDTO());
        model.addAttribute("clients", clientService.getActiveClients());
        model.addAttribute("workers", userService.getWorkers());
        model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
            ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
        return "admin/orders/create";
    }

    @GetMapping("/clients/import")
    public String importClientsForm() {
        return "admin/clients/import";
    }

    @PostMapping("/clients/import")
    public String importClients(@RequestParam("file") MultipartFile file, Model model) {
        if (file == null || file.isEmpty()) {
            return redirectWithError("/admin/clients/import", "Please select a file.", model);
        }
        var result = excelImportService.importClientsFromExcel(file, tenantContextService.getCurrentCompany());
        model.addAttribute("importResult", result);
        return "admin/clients/import";
    }

    @GetMapping("/clients/import-template")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=clients_import_template.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Clients");
            Row header = sheet.createRow(0);
            String[] columns = {"Company Name", "Contact Person", "Phone", "Email", "Address", "City", "Country", "Tax ID"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(response.getOutputStream());
        }
    }

    @PostMapping("/orders/create")
    public String createOrder(@ModelAttribute WorkOrderDTO orderDTO,
                             @RequestParam Long clientId,
                             @RequestParam(required = false) Long assignedToId,
                             Model model) {
        try {
            orderDTO.setClientId(clientId);
            orderDTO.setAssignedToId(assignedToId);
            orderDTO.setCreatedById(getCurrentUserId());

            workOrderService.createWorkOrder(orderDTO);
            return redirectWithSuccess("/admin/orders", "Order created successfully", model);
        } catch (Exception e) {
            return redirectWithError("/admin/orders/create", "Error creating order: " + e.getMessage(), model);
        }
    }

    @GetMapping("/orders/{id}")
    public String orderDetails(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean autocopyPublicLink,
                               Model model) {
        try {
            WorkOrderDTO order = workOrderService.getWorkOrderById(id);
            List<AttachmentDTO> attachments = fileStorageService.getAttachmentsByWorkOrder(id);
            List<UserDTO> workers = userService.getWorkers();
            UserDTO assignedWorker = null;
            if (order.getAssignedToId() != null) {
                for (UserDTO worker : workers) {
                    if (order.getAssignedToId().equals(worker.getId())) {
                        assignedWorker = worker;
                        break;
                    }
                }
            }

            model.addAttribute("order", order);
            model.addAttribute("attachments", attachments);
            model.addAttribute("orderStatuses", OrderStatus.values());
            model.addAttribute("workers", workers);
            model.addAttribute("assignedWorker", assignedWorker);
            model.addAttribute("orderActivities", taskService.getActivitiesByWorkOrder(id));
            model.addAttribute("orderItems", workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
                id, tenantContextService.requireCompanyId()));
            model.addAttribute("profitSummary", workOrderProfitService.calculateRealProfit(
                id, tenantContextService.getCurrentCompany()));
            model.addAttribute("activityFeed", activityLogService.getForWorkOrder(id));
            model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
                ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
            model.addAttribute("variants", productVariantRepository.findAllByCompany_Id(tenantContextService.requireCompanyId()));
            model.addAttribute("publicTrackingUrl", baseUrl + "/public/order/" + order.getPublicToken());
            model.addAttribute("autocopyPublicLink", autocopyPublicLink);
            com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityById(id);
            model.addAttribute("publicTokenExpiresAt", orderEntity.getPublicTokenExpiresAt());
            java.util.List<com.printflow.entity.PublicOrderRequest> sourceRequests =
                publicOrderRequestRepository.findByConvertedOrder_IdIn(java.util.List.of(order.getId()));
            if (!sourceRequests.isEmpty()) {
                model.addAttribute("sourcePublicRequestId", sourceRequests.get(0).getId());
            }
            List<com.printflow.entity.AuditLog> auditLogs = auditLogService.getByEntity("WorkOrder", id);
            model.addAttribute("auditLogs", auditLogs);
            String assignedByName = null;
            java.time.LocalDateTime assignedAt = null;
            String assignedByTooltip = null;
            String assignedByDescription = null;
            String assignedByOld = null;
            String assignedByNew = null;
            String assignedByIp = null;
            String assignedByUserAgent = null;
            for (com.printflow.entity.AuditLog log : auditLogs) {
                if (log.getDescription() != null && log.getDescription().toLowerCase().contains("assignment")) {
                    if (log.getUser() != null) {
                        assignedByName = log.getUser().getFullName();
                    }
                    assignedAt = log.getCreatedAt();
                    assignedByDescription = log.getDescription();
                    assignedByOld = log.getOldValue();
                    assignedByNew = log.getNewValue();
                    assignedByIp = log.getIpAddress();
                    assignedByUserAgent = log.getUserAgent();
                    String ip = log.getIpAddress() != null ? log.getIpAddress() : "-";
                    String ua = log.getUserAgent() != null ? log.getUserAgent() : "-";
                    String oldVal = log.getOldValue() != null ? log.getOldValue() : "-";
                    String newVal = log.getNewValue() != null ? log.getNewValue() : "-";
                    assignedByTooltip = "User: " + (assignedByName != null ? assignedByName : "-")
                        + "\nTime: " + (assignedAt != null ? assignedAt.toString() : "-")
                        + "\nFrom: " + oldVal
                        + "\nTo: " + newVal
                        + "\nIP: " + ip
                        + "\nUA: " + ua;
                    break;
                }
            }
            model.addAttribute("assignedByName", assignedByName);
            model.addAttribute("assignedAt", assignedAt);
            model.addAttribute("assignedByTooltip", assignedByTooltip);
            model.addAttribute("assignedByDescription", assignedByDescription);
            model.addAttribute("assignedByOld", assignedByOld);
            model.addAttribute("assignedByNew", assignedByNew);
            model.addAttribute("assignedByIp", assignedByIp);
            model.addAttribute("assignedByUserAgent", assignedByUserAgent);

            return "admin/orders/details";
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders", "Order not found", model);
        }
    }

    @GetMapping("/orders/{id}/items-fragment")
    public String orderItemsFragment(@PathVariable Long id, Model model) {
        model.addAttribute("orderItems", workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(
            id, tenantContextService.requireCompanyId()));
        model.addAttribute("profitSummary", workOrderProfitService.calculateRealProfit(
            id, tenantContextService.getCurrentCompany()));
        model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
            ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
        return "admin/orders/items-fragment :: itemsTable";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                    @RequestParam OrderStatus status,
                                    @RequestParam(required = false) String notes,
                                    Model model) {
        try {
            workOrderService.updateWorkOrderStatus(id, status, notes);
            return redirectWithSuccess("/admin/orders/" + id, "Order status updated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error updating status: " + e.getMessage(), model);
        }
    }

    @GetMapping("/orders/{id}/edit")
    public String editOrderForm(@PathVariable Long id, Model model) {
        try {
            WorkOrderDTO order = workOrderService.getWorkOrderById(id);
            model.addAttribute("order", order);
            model.addAttribute("clients", clientService.getActiveClients());
            model.addAttribute("workers", userService.getWorkers());
            model.addAttribute("orderStatuses", OrderStatus.values());
            model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
                ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
            return "admin/orders/edit";
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders", "Order not found", model);
        }
    }

    @PostMapping("/orders/{id}/edit")
    public String updateOrder(@PathVariable Long id,
                              @ModelAttribute WorkOrderDTO orderDTO,
                              @RequestParam Long clientId,
                              @RequestParam(required = false) Long assignedToId,
                              Model model) {
        try {
            if (orderDTO.getPrice() != null && orderDTO.getPrice() < 0) {
                return redirectWithError("/admin/orders/" + id + "/edit", "Price cannot be negative", model);
            }
            if (orderDTO.getCost() != null && orderDTO.getCost() < 0) {
                return redirectWithError("/admin/orders/" + id + "/edit", "Cost cannot be negative", model);
            }
            orderDTO.setClientId(clientId);
            orderDTO.setAssignedToId(assignedToId);
            workOrderService.updateWorkOrder(id, orderDTO);
            return redirectWithSuccess("/admin/orders/" + id, "Order updated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id + "/edit", "Error updating order: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/send-for-approval")
    public String sendForApproval(@PathVariable Long id, Model model) {
        try {
            workOrderService.sendForClientApproval(id);
            return redirectWithSuccess("/admin/orders/" + id, "Design approval request sent to client", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error sending approval request: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/rotate-public-token")
    public String rotatePublicToken(@PathVariable Long id, Model model) {
        try {
            workOrderService.rotatePublicToken(id);
            com.printflow.entity.WorkOrder order = workOrderService.getWorkOrderEntityById(id);
            boolean sent = sendTrackingEmailForOrder(order);
            if (sent) {
                return redirectWithSuccess("/admin/orders/" + id, "Public tracking link rotated and sent to client", model);
            }
            return redirectWithSuccess("/admin/orders/" + id, "Public tracking link rotated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error rotating public link: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/rotate-public-token-copy")
    public String rotatePublicTokenAndCopy(@PathVariable Long id, Model model) {
        try {
            workOrderService.rotatePublicToken(id);
            return redirectWithSuccess("/admin/orders/" + id + "?autocopyPublicLink=true",
                "Public tracking link rotated. New link copied.", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error rotating public link: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/send-tracking-email")
    public String sendTrackingEmail(@PathVariable Long id, Model model) {
        try {
            com.printflow.entity.WorkOrder order = workOrderService.getWorkOrderEntityById(id);
            if (sendTrackingEmailForOrder(order)) {
                return redirectWithSuccess("/admin/orders/" + id, "Tracking email sent", model);
            }
            return redirectWithError("/admin/orders/" + id, "Client email is missing", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error sending tracking email: " + e.getMessage(), model);
        }
    }

    private boolean sendTrackingEmailForOrder(com.printflow.entity.WorkOrder order) {
        if (order == null || order.getClient() == null || order.getClient().getEmail() == null || order.getClient().getEmail().isBlank()) {
            return false;
        }
        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("orderNumber", order.getOrderNumber());
        vars.put("orderTitle", order.getTitle());
        vars.put("clientName", order.getClient().getCompanyName());
        vars.put("companyName", order.getCompany() != null ? order.getCompany().getName() : "PrintFlow");
        vars.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : "");
        vars.put("companyEmail", order.getCompany() != null ? order.getCompany().getEmail() : null);
        vars.put("companyPhone", order.getCompany() != null ? order.getCompany().getPhone() : null);
        vars.put("companyWebsite", order.getCompany() != null ? order.getCompany().getWebsite() : null);
        vars.put("companyAddress", order.getCompany() != null ? order.getCompany().getAddress() : null);
        boolean serbian = isSerbianCompany(order.getCompany());
        vars.put("lang", serbian ? "sr" : "en");
        vars.put("trackingUrl", baseUrl + "/public/order/" + order.getPublicToken());
        String logoUrl = null;
        String logoInline = null;
        if (order.getCompany() != null
            && order.getCompany().getId() != null
            && order.getCompany().getLogoPath() != null
            && !order.getCompany().getLogoPath().isBlank()) {
            logoUrl = baseUrl + "/public/company-logo/" + order.getCompany().getId();
            try {
                byte[] data = companyBrandingService.loadLogo(order.getCompany().getId());
                String mime = MediaTypeFactory.getMediaType(order.getCompany().getLogoPath())
                    .map(Object::toString)
                    .orElse("image/png");
                logoInline = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
            } catch (Exception ignored) {
            }
        }
        vars.put("logoUrl", logoUrl);
        vars.put("logoInline", logoInline);
        String html = emailTemplateService.render("public-tracking-link", vars);
        com.printflow.dto.EmailMessage msg = new com.printflow.dto.EmailMessage();
        msg.setTo(order.getClient().getEmail());
        msg.setSubject((serbian ? "Link za praćenje naloga: " : "Order tracking link: ") + order.getOrderNumber());
        msg.setHtmlBody(html);
        msg.setTextBody("Track your order: " + baseUrl + "/public/order/" + order.getPublicToken());
        emailService.send(msg, order.getCompany(), "public-tracking-link");
        return true;
    }

    private boolean isSerbianCompany(com.printflow.entity.Company company) {
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

    @GetMapping("/orders/{id}/email-preview")
    public String previewOrderEmail(@PathVariable Long id,
                                    @RequestParam(defaultValue = "approval") String type,
                                    @RequestParam(required = false) String comment,
                                    Model model) {
        try {
            com.printflow.entity.WorkOrder order = workOrderService.getWorkOrderEntityById(id);
            NotificationService.EmailPreview preview;
            switch (type) {
                case "feedback-approved" -> preview = notificationService.buildDesignFeedbackPreview(order, true, comment);
                case "feedback-changes" -> preview = notificationService.buildDesignFeedbackPreview(order, false, comment);
                default -> preview = notificationService.buildDesignApprovalPreview(order, null);
            }
            model.addAttribute("subject", preview.getSubject());
            model.addAttribute("html", preview.getHtml());
            model.addAttribute("orderId", id);
            model.addAttribute("type", type);
            model.addAttribute("comment", comment);
            String currentUserEmail = null;
            try {
                currentUserEmail = tenantContextService.getCurrentUser().getEmail();
            } catch (Exception ignored) {
                // ignore missing user context
            }
            model.addAttribute("currentUserEmail", currentUserEmail);
            return "admin/email-preview";
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error loading email preview: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/email-preview/send-test")
    public String sendTestEmail(@PathVariable Long id,
                                @RequestParam(defaultValue = "approval") String type,
                                @RequestParam(required = false) String comment,
                                @RequestParam String toEmail,
                                Model model) {
        try {
            if (toEmail == null || toEmail.isBlank() || !toEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                String commentParam = comment != null ? "&comment=" + java.net.URLEncoder.encode(comment, java.nio.charset.StandardCharsets.UTF_8) : "";
                return redirectWithError("/admin/orders/" + id + "/email-preview?type=" + type + commentParam,
                    "Please provide a valid email address.", model);
            }
            com.printflow.entity.WorkOrder order = workOrderService.getWorkOrderEntityById(id);
            NotificationService.EmailPreview preview;
            switch (type) {
                case "feedback-approved" -> preview = notificationService.buildDesignFeedbackPreview(order, true, comment);
                case "feedback-changes" -> preview = notificationService.buildDesignFeedbackPreview(order, false, comment);
                default -> preview = notificationService.buildDesignApprovalPreview(order, null);
            }
            notificationService.sendPreviewEmail(toEmail, preview);
            String commentParam = comment != null ? "&comment=" + java.net.URLEncoder.encode(comment, java.nio.charset.StandardCharsets.UTF_8) : "";
            return redirectWithSuccess("/admin/orders/" + id + "/email-preview?type=" + type + commentParam, "Test email sent", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            String commentParam = comment != null ? "&comment=" + java.net.URLEncoder.encode(comment, java.nio.charset.StandardCharsets.UTF_8) : "";
            return redirectWithError("/admin/orders/" + id + "/email-preview?type=" + type + commentParam, "Error sending test email: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/email-preview/send-test-ajax")
    @ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> sendTestEmailAjax(
            @PathVariable Long id,
            @RequestParam(defaultValue = "approval") String type,
            @RequestParam(required = false) String comment,
            @RequestParam String toEmail) {
        try {
            if (toEmail == null || toEmail.isBlank() || !toEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("status", "error", "message", "Please provide a valid email address."));
            }
            com.printflow.entity.WorkOrder order = workOrderService.getWorkOrderEntityById(id);
            NotificationService.EmailPreview preview;
            switch (type) {
                case "feedback-approved" -> preview = notificationService.buildDesignFeedbackPreview(order, true, comment);
                case "feedback-changes" -> preview = notificationService.buildDesignFeedbackPreview(order, false, comment);
                default -> preview = notificationService.buildDesignApprovalPreview(order, null);
            }
            notificationService.sendPreviewEmail(toEmail, preview);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("status", "ok", "message", "Test email sent"));
        } catch (ResourceNotFoundException e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(java.util.Map.of("status", "error", "message", "Order not found"));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest()
                .body(java.util.Map.of("status", "error", "message", "Error sending test email: " + e.getMessage()));
        }
    }

    @PostMapping("/orders/{id}/assign-worker")
    public String assignWorker(@PathVariable Long id,
                               @RequestParam(required = false) Long assignedToId,
                               Model model) {
        try {
            workOrderService.assignWorker(id, assignedToId);
            return redirectWithSuccess("/admin/orders/" + id, "Assignment updated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error assigning worker: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/delete")
    public String deleteOrder(@PathVariable Long id, Model model) {
        try {
            workOrderService.deleteWorkOrder(id);
            return redirectWithSuccess("/admin/orders", "Order deleted", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error deleting order: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/reorder")
    public String reorderOrder(@PathVariable Long id, Model model) {
        try {
            Long createdById = tenantContextService.getCurrentUser().getId();
            WorkOrderDTO newOrder = workOrderService.reorderWorkOrder(id, createdById, "admin");
            return redirectWithSuccess("/admin/orders/" + newOrder.getId(), "Reorder created", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error creating reorder: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/duplicate")
    public String duplicateOrder(@PathVariable Long id,
                                 @RequestParam(defaultValue = "false") boolean includeAttachments,
                                 Model model) {
        try {
            Long createdById = tenantContextService.getCurrentUser().getId();
            WorkOrderDTO newOrder = workOrderService.duplicateWorkOrder(id, createdById, includeAttachments);
            return redirectWithSuccess("/admin/orders/" + newOrder.getId(), "Order duplicated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/orders/" + id, "Error duplicating order: " + e.getMessage(), model);
        }
    }

    // ==================== USERS (REVIDIRANO) ====================

    @GetMapping("/users")
    public String listUsers(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) Integer size,
                            Model model) {
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        Page<User> userPage = userService.findAll(safePage, pageSize);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("pagination", new PaginationDTO(
                userPage.getNumber(),
                userPage.getSize(),
                (int) userPage.getTotalElements(),
                userPage.getNumber() * userPage.getSize() + 1,
                Math.min((userPage.getNumber() + 1) * userPage.getSize(), (int) userPage.getTotalElements())
        ));
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());

        UserStatisticsDTO stats = userService.getUserStatistics();
        model.addAttribute("stats", stats);

        return "admin/users/list";
    }

    @GetMapping("/users/create")
    public String createUserForm(Model model) {
        model.addAttribute("user", new UserDTO());
        model.addAttribute("roles", getAssignableRoles());
        addCompanyContext(model);
        // Koristimo zajednički user-form
        return "admin/users/user-form"; 
    }

    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute("user") UserDTO userDTO, BindingResult result, Model model) {
    	// Ručno postavljanje username-a ako je on obavezan u bazi/DTO-u
        if (userDTO.getUsername() == null || userDTO.getUsername().isEmpty()) {
            userDTO.setUsername(userDTO.getEmail());
        }

        if (userDTO.getPassword() == null || userDTO.getPassword().isBlank()) {
            result.rejectValue("password", "password.required", "Password is required");
        }
        if (userDTO.getConfirmPassword() == null || userDTO.getConfirmPassword().isBlank()) {
            result.rejectValue("confirmPassword", "confirmPassword.required", "Confirm password is required");
        }
        if (userDTO.getPassword() != null && userDTO.getConfirmPassword() != null &&
            !userDTO.getPassword().equals(userDTO.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "confirmPassword.mismatch", "Passwords do not match");
        }
        if (userDTO.getRole() == null || userDTO.getRole().isBlank()) {
            result.rejectValue("role", "role.required", "Role is required");
        }
    	
    	if (result.hasErrors()) {
            model.addAttribute("roles", getAssignableRoles());
            addCompanyContext(model);
            return "admin/users/user-form";
        }

        try {
            userService.createUser(userDTO);
            
            return redirectWithSuccess("/admin/users", "User created successfully", model);
        } catch (Exception e) {
            model.addAttribute("roles", getAssignableRoles());
            model.addAttribute("errorMessage", "Error creating user: " + e.getMessage());
            addCompanyContext(model);
            return "admin/users/user-form";
        }
    }

    @GetMapping("/users/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        try {
            UserDTO user = userService.getUserById(id);
            model.addAttribute("user", user);
            model.addAttribute("roles", getAssignableRoles());
            addCompanyContext(model);

            UserTaskStats stats = userService.getUserTaskStats(id);
            model.addAttribute("userStats", stats);

            // Koristimo zajednički user-form
            return "admin/users/user-form";
        } catch (Exception e) {
            return redirectWithError("/admin/users", "User not found", model);
        }
    }

    @PostMapping("/users/edit/{id}")
    public String updateUser(@PathVariable Long id,
                            @Valid @ModelAttribute("user") UserDTO userDTO,
                            BindingResult result,
                            Model model) {
        if (result.hasErrors()) {
            model.addAttribute("roles", getAssignableRoles());
            addCompanyContext(model);
            return "admin/users/user-form";
        }

        try {
            userService.updateUser(id, userDTO);
            return redirectWithSuccess("/admin/users", "User updated successfully", model);
        } catch (Exception e) {
            model.addAttribute("roles", getAssignableRoles());
            model.addAttribute("errorMessage", "Error updating user: " + e.getMessage());
            addCompanyContext(model);
            return "admin/users/user-form";
        }
    }

    @GetMapping("/users/permissions/{id}")
    public String legacyUserPermissionsRedirect(@PathVariable Long id) {
        return "redirect:/admin/users/edit/" + id;
    }

    // ==================== TASK REVIEW ====================

    @GetMapping("/tasks")
    public String listTasksForReview(@RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(required = false) Integer size,
                                     Model model) {
        notificationService.markAllAsRead(getCurrentUserId());
        String statusParam = status != null ? status.trim().toUpperCase() : "";
        com.printflow.entity.enums.TaskStatus parsedStatus = parseTaskStatus(statusParam);

        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);

        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<TaskDTO> tasksPage;
        if (statusParam.isEmpty() || "ALL".equals(statusParam) || parsedStatus == null) {
            tasksPage = taskService.getTasksForAdmin(pageable);
        } else if ("OPEN".equals(statusParam)) {
            tasksPage = taskService.getOpenTasksForAdmin(pageable);
        } else {
            tasksPage = taskService.getTasksByStatusForAdmin(parsedStatus, pageable);
        }
        if (safePage >= tasksPage.getTotalPages() && tasksPage.getTotalPages() > 0) {
            safePage = tasksPage.getTotalPages() - 1;
            pageable = org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
            if (statusParam.isEmpty() || "ALL".equals(statusParam) || parsedStatus == null) {
                tasksPage = taskService.getTasksForAdmin(pageable);
            } else if ("OPEN".equals(statusParam)) {
                tasksPage = taskService.getOpenTasksForAdmin(pageable);
            } else {
                tasksPage = taskService.getTasksByStatusForAdmin(parsedStatus, pageable);
            }
        }

        model.addAttribute("tasksPage", tasksPage);
        model.addAttribute("tasks", tasksPage.getContent());
        String resolvedStatus = (statusParam.isEmpty() || parsedStatus == null) ? "ALL" : statusParam;
        model.addAttribute("status", resolvedStatus);
        model.addAttribute("currentPage", tasksPage.getNumber());
        model.addAttribute("totalPages", tasksPage.getTotalPages());
        model.addAttribute("totalItems", tasksPage.getTotalElements());
        model.addAttribute("lastPage", Math.max(0, tasksPage.getTotalPages() - 1));
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        if (!tenantContextService.isSuperAdmin()) {
            Long companyId = tenantContextService.requireCompanyId();
            model.addAttribute("scopeCompanyName", companyService.getCompanyById(companyId).getName());
        }
        return "admin/tasks/list";
    }

    private com.printflow.entity.enums.TaskStatus parseTaskStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return null;
        }
        try {
            return com.printflow.entity.enums.TaskStatus.valueOf(statusParam);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @GetMapping("/tasks/{id}")
    public String taskDetails(@PathVariable Long id, Model model) {
        try {
            TaskDetailsDTO task = taskService.getTaskDetails(id);
            model.addAttribute("task", task);
            model.addAttribute("activities", taskService.getTaskActivities(id));
            notificationService.markUnreadByLink(getCurrentUserId(), "/admin/tasks/" + id);
            List<AttachmentDTO> taskAttachments = fileStorageService.getAttachmentsByTask(id);
            List<AttachmentDTO> proofAttachments = taskAttachments.stream()
                .filter(a -> a.getAttachmentType() == com.printflow.entity.enums.AttachmentType.PROOF_OF_WORK)
                .toList();
            List<AttachmentDTO> otherTaskAttachments = taskAttachments.stream()
                .filter(a -> a.getAttachmentType() != com.printflow.entity.enums.AttachmentType.PROOF_OF_WORK)
                .toList();
            model.addAttribute("proofAttachments", proofAttachments);
            model.addAttribute("taskAttachments", otherTaskAttachments);
            if (task.getWorkOrderId() != null) {
                model.addAttribute("orderAttachments", fileStorageService.getAttachmentsByWorkOrder(task.getWorkOrderId()));
            } else {
                model.addAttribute("orderAttachments", java.util.List.of());
            }
            return "admin/tasks/details";
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/tasks", "Task not found", model);
        }
    }

    @GetMapping("/tasks/edit/{id}")
    public String editTaskForm(@PathVariable Long id, Model model) {
        try {
            model.addAttribute("task", taskService.getTaskForEdit(id));
            model.addAttribute("workers", userService.getWorkers());
            model.addAttribute("priorities", com.printflow.entity.enums.TaskPriority.values());
            notificationService.markUnreadByLink(getCurrentUserId(), "/admin/tasks/" + id);
            return "admin/tasks/edit";
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/tasks", "Task not found", model);
        }
    }

    @PostMapping("/tasks/edit/{id}")
    public String updateTask(@PathVariable Long id,
                             @ModelAttribute("task") TaskDTO taskDTO,
                             @RequestParam(required = false) String assignedToId,
                             Model model) {
        try {
            Long assignedTo = parseNullableId(assignedToId);
            taskService.updateTaskFromAdmin(id, taskDTO, assignedTo);
            return redirectWithSuccess("/admin/tasks/" + id, "Task updated successfully", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            model.addAttribute("task", taskDTO);
            model.addAttribute("workers", userService.getWorkers());
            model.addAttribute("priorities", com.printflow.entity.enums.TaskPriority.values());
            model.addAttribute("errorMessage", "Error updating task: " + e.getMessage());
            return "admin/tasks/edit";
        }
    }

    @GetMapping("/tasks/create")
    public String createTaskForm(@RequestParam(required = false) Long workOrderId, Model model) {
        model.addAttribute("task", new TaskDTO());
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(0, 200, org.springframework.data.domain.Sort.by("createdAt").descending());
        List<WorkOrderDTO> orders = workOrderService.getWorkOrders(pageable).getContent();
        List<UserDTO> workers = userService.getWorkers();
        model.addAttribute("orders", orders);
        model.addAttribute("workers", workers);
        model.addAttribute("priorities", com.printflow.entity.enums.TaskPriority.values());
        model.addAttribute("selectedWorkOrderId", workOrderId);
        Long suggestedAssignedToId = suggestWorkerId(workOrderId, orders, workers);
        model.addAttribute("selectedAssignedToId", suggestedAssignedToId);
        model.addAttribute("suggestedWorkerName", resolveWorkerName(suggestedAssignedToId, workers));
        return "admin/tasks/create";
    }

    @PostMapping("/tasks/create")
    public String createTask(@ModelAttribute("task") TaskDTO taskDTO,
                             @RequestParam(required = false) String workOrderId,
                             @RequestParam(required = false) String assignedToId,
                             Model model) {
        try {
            Long createdById = tenantContextService.getCurrentUser().getId();
            Long workOrder = parseNullableId(workOrderId);
            Long assignedTo = parseNullableId(assignedToId);
            taskService.createTask(taskDTO, assignedTo, workOrder, createdById);
            return redirectWithSuccess("/admin/tasks", "Task created successfully", model);
        } catch (Exception e) {
            model.addAttribute("task", taskDTO);
            org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 200, org.springframework.data.domain.Sort.by("createdAt").descending());
            List<WorkOrderDTO> orders = workOrderService.getWorkOrders(pageable).getContent();
            List<UserDTO> workers = userService.getWorkers();
            model.addAttribute("orders", orders);
            model.addAttribute("workers", workers);
            model.addAttribute("priorities", com.printflow.entity.enums.TaskPriority.values());
            Long selectedWorkOrderId = parseNullableId(workOrderId);
            model.addAttribute("selectedWorkOrderId", selectedWorkOrderId);
            Long selectedAssignedToId = parseNullableId(assignedToId);
            if (selectedAssignedToId == null) {
                selectedAssignedToId = suggestWorkerId(selectedWorkOrderId, orders, workers);
            }
            model.addAttribute("selectedAssignedToId", selectedAssignedToId);
            model.addAttribute("suggestedWorkerName", resolveWorkerName(selectedAssignedToId, workers));
            model.addAttribute("errorMessage", "Error creating task: " + e.getMessage());
            return "admin/tasks/create";
        }
    }

    @PostMapping("/tasks/{id}/approve")
    public String approveTask(@PathVariable Long id, Model model) {
        try {
            Long adminId = tenantContextService.getCurrentUser().getId();
            taskService.approveTaskCompletion(id, adminId);
            return redirectWithSuccess("/admin/tasks", "Task approved and client notified", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/tasks", "Error approving task: " + e.getMessage(), model);
        }
    }

    @PostMapping("/tasks/{id}/reject")
    public String rejectTask(@PathVariable Long id,
                             @RequestParam String reason,
                             Model model) {
        try {
            Long adminId = tenantContextService.getCurrentUser().getId();
            taskService.rejectTaskCompletion(id, adminId, reason);
            return redirectWithSuccess("/admin/tasks/" + id, "Task rejected and worker notified", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/tasks/" + id, "Error rejecting task: " + e.getMessage(), model);
        }
    }

    @PostMapping("/tasks/{id}/delete")
    public String deleteTask(@PathVariable Long id, Model model) {
        try {
            taskService.deleteTask(id);
            return redirectWithSuccess("/admin/tasks", "Task deleted", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError("/admin/tasks/" + id, "Error deleting task: " + e.getMessage(), model);
        }
    }

    private void addCompanyContext(Model model) {
        boolean isSuperAdmin = tenantContextService.isSuperAdmin();
        model.addAttribute("isSuperAdmin", isSuperAdmin);
        if (isSuperAdmin) {
            model.addAttribute("companies", companyService.getCompanies(null));
        }
    }

    private Long parseNullableId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long suggestWorkerId(Long workOrderId, List<WorkOrderDTO> orders, List<UserDTO> workers) {
        if (workOrderId == null || workers == null || workers.isEmpty()) {
            return null;
        }
        WorkOrderDTO order = orders.stream()
            .filter(o -> o.getId() != null && o.getId().equals(workOrderId))
            .findFirst()
            .orElse(null);
        if (order == null) {
            return null;
        }

        String preferredRole = preferredWorkerRole(order);
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

    private String preferredWorkerRole(WorkOrderDTO order) {
        if (order == null) {
            return "WORKER_GENERAL";
        }
        if (order.getStatus() != null) {
            Set<com.printflow.entity.enums.OrderStatus> designStatuses = Set.of(
                com.printflow.entity.enums.OrderStatus.IN_DESIGN,
                com.printflow.entity.enums.OrderStatus.WAITING_CLIENT_APPROVAL
            );
            if (designStatuses.contains(order.getStatus())) {
                return "WORKER_DESIGN";
            }
            Set<com.printflow.entity.enums.OrderStatus> printStatuses = Set.of(
                com.printflow.entity.enums.OrderStatus.APPROVED_FOR_PRINT,
                com.printflow.entity.enums.OrderStatus.IN_PRINT,
                com.printflow.entity.enums.OrderStatus.WAITING_QUALITY_CHECK
            );
            if (printStatuses.contains(order.getStatus())) {
                return "WORKER_PRINT";
            }
        }
        if (order.getPrintType() == com.printflow.entity.enums.PrintType.DTF
            || order.getPrintType() == com.printflow.entity.enums.PrintType.LASER) {
            return "WORKER_PRINT";
        }
        return "WORKER_GENERAL";
    }

    private String resolveWorkerName(Long workerId, List<UserDTO> workers) {
        if (workerId == null || workers == null) {
            return null;
        }
        return workers.stream()
            .filter(w -> workerId.equals(w.getId()))
            .map(UserDTO::getFullName)
            .findFirst()
            .orElse(null);
    }

    private com.printflow.entity.User.Role[] getAssignableRoles() {
        if (tenantContextService.isSuperAdmin()) {
            return com.printflow.entity.User.Role.values();
        }
        return java.util.Arrays.stream(com.printflow.entity.User.Role.values())
            .filter(role -> role != com.printflow.entity.User.Role.SUPER_ADMIN)
            .toArray(com.printflow.entity.User.Role[]::new);
    }

    // Helper method to get current user ID
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof com.printflow.entity.User) {
            return ((com.printflow.entity.User) principal).getId();
        }
        return 1L; // Fallback
    }
}
