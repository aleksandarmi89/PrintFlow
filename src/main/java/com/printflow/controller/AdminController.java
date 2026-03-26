package com.printflow.controller;

import com.printflow.dto.*;
import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder.DeliveryType;
import com.printflow.entity.WorkOrder.ShipmentStatus;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.QuoteStatus;
import com.printflow.config.PaginationConfig;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.service.*;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.repository.PublicOrderRequestRepository;
import com.printflow.shipping.ShipmentFacade;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaTypeFactory;

import java.io.IOException;
import java.math.BigDecimal;
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
    private final OrderPdfService orderPdfService;
    private final ShipmentFacade shipmentFacade;
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
                           OrderPdfService orderPdfService,
                           ShipmentFacade shipmentFacade,
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
        this.orderPdfService = orderPdfService;
        this.shipmentFacade = shipmentFacade;
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
            Client clientEntity = clientService.getClientEntity(id);
            Long companyId = clientEntity.getCompany() != null
                ? clientEntity.getCompany().getId()
                : tenantContextService.requireCompanyId();
            model.addAttribute("client", client);
            model.addAttribute("pricingVariants", productVariantRepository.findAllByCompany_Id(companyId));
            model.addAttribute("pricingProfiles", clientPricingProfileService.getProfilesForClient(id, companyId));
            var access = clientPortalService.getAccessForClient(id, companyId);
            if (access != null) {
                model.addAttribute("portalLink", baseUrl + "/portal/" + access.getAccessToken());
            }
            return "admin/clients/edit";
        } catch (ResourceNotFoundException e) {
            return redirectWithError("/admin/clients", "admin.clients.flash.not_found", model);
        } catch (Exception e) {
            return redirectWithError("/admin/clients", "admin.clients.flash.not_found", model);
        }
    }

    @PostMapping("/clients/{id}/portal-link")
    public String generatePortalLink(@PathVariable Long id, Model model) {
        try {
            ClientDTO client = clientService.getClientById(id);
            Client clientEntity = clientService.getClientEntity(id);
            Company company = clientEntity.getCompany() != null
                ? clientEntity.getCompany()
                : tenantContextService.getCurrentCompany();
            if (company == null) {
                throw new ResourceNotFoundException("Client company not found");
            }
            var access = clientPortalService.createOrRefreshAccess(
                clientEntity,
                company,
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
            Client clientEntity = clientService.getClientEntity(id);
            Long companyId = clientEntity.getCompany() != null
                ? clientEntity.getCompany().getId()
                : tenantContextService.requireCompanyId();
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
                         @RequestParam(required = false) QuoteStatus quoteStatus,
                         @RequestParam(required = false) ShipmentStatus shipmentStatus,
                         @RequestParam(required = false) Long clientId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate createdFromDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate createdToDate,
                         @RequestParam(defaultValue = "false") boolean overdueOnly,
                         @RequestParam(required = false) DeliveryType deliveryType,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) Integer size) {
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);

        if (createdFromDate != null && createdToDate != null && createdFromDate.isAfter(createdToDate)) {
            java.time.LocalDate tmp = createdFromDate;
            createdFromDate = createdToDate;
            createdToDate = tmp;
            model.addAttribute("errorMessage", "orders.list.invalid_date_range");
        }

        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<WorkOrderDTO> ordersPage;

        String normalizedSearch = search == null ? null : search.trim();
        if (normalizedSearch != null && normalizedSearch.isEmpty()) {
            normalizedSearch = null;
        }
        boolean hasSearch = normalizedSearch != null;
        java.time.LocalDateTime createdFrom = createdFromDate != null ? createdFromDate.atStartOfDay() : null;
        java.time.LocalDateTime createdTo = createdToDate != null ? createdToDate.atTime(java.time.LocalTime.MAX) : null;
        boolean hasFilters = hasSearch || status != null || quoteStatus != null || shipmentStatus != null
            || clientId != null || deliveryType != null || createdFrom != null || createdTo != null || overdueOnly;
        if (hasFilters) {
            ordersPage = workOrderService.getWorkOrdersByFilters(
                normalizedSearch, status, quoteStatus, shipmentStatus, clientId, createdFrom, createdTo, overdueOnly, deliveryType, pageable);
            model.addAttribute("search", normalizedSearch);
            model.addAttribute("status", status);
            model.addAttribute("quoteStatus", quoteStatus);
            model.addAttribute("shipmentStatus", shipmentStatus);
            model.addAttribute("clientId", clientId);
            model.addAttribute("createdFromDate", createdFromDate);
            model.addAttribute("createdToDate", createdToDate);
            model.addAttribute("overdueOnly", overdueOnly);
            model.addAttribute("deliveryType", deliveryType);
        } else {
            ordersPage = workOrderService.getWorkOrders(pageable);
        }

        if (safePage >= ordersPage.getTotalPages() && ordersPage.getTotalPages() > 0) {
            safePage = ordersPage.getTotalPages() - 1;
            pageable = org.springframework.data.domain.PageRequest.of(safePage, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());
            if (hasFilters) {
                ordersPage = workOrderService.getWorkOrdersByFilters(
                    normalizedSearch, status, quoteStatus, shipmentStatus, clientId, createdFrom, createdTo, overdueOnly, deliveryType, pageable);
            } else {
                ordersPage = workOrderService.getWorkOrders(pageable);
            }
        }
        applyItemTotalsForOrderList(ordersPage.getContent());

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
        model.addAttribute("quoteStatus", quoteStatus);
        model.addAttribute("shipmentStatus", shipmentStatus);
        model.addAttribute("clientId", clientId);
        model.addAttribute("createdFromDate", createdFromDate);
        model.addAttribute("createdToDate", createdToDate);
        model.addAttribute("overdueOnly", overdueOnly);
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
        model.addAttribute("quoteStatuses", QuoteStatus.values());
        model.addAttribute("shipmentStatuses", ShipmentStatus.values());
        model.addAttribute("deliveryTypes", DeliveryType.values());
        java.util.List<com.printflow.dto.ClientDTO> activeClients = clientService.getActiveClients();
        model.addAttribute("activeClients", activeClients);
        if (clientId != null) {
            String selectedClientName = activeClients.stream()
                .filter(c -> java.util.Objects.equals(c.getId(), clientId))
                .map(com.printflow.dto.ClientDTO::getCompanyName)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse("#" + clientId);
            model.addAttribute("selectedClientName", selectedClientName);
        }
        model.addAttribute("newCount", workOrderService.countByStatus(OrderStatus.NEW));
        model.addAttribute("designCount", workOrderService.countByStatus(OrderStatus.IN_DESIGN));
        model.addAttribute("printCount", workOrderService.countByStatus(OrderStatus.IN_PRINT));
        model.addAttribute("readyCount", workOrderService.countByStatus(OrderStatus.READY_FOR_DELIVERY));
        model.addAttribute("quotePreparingCount", workOrderService.countByQuoteStatus(QuoteStatus.PREPARING));
        model.addAttribute("quoteReadyCount", workOrderService.countByQuoteStatus(QuoteStatus.READY));
        model.addAttribute("quoteSentCount", workOrderService.countByQuoteStatus(QuoteStatus.SENT));
        model.addAttribute("overdueCount", workOrderService.countOverdueOrders());
        model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
            ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
        return "admin/orders/list";
    }

    private void applyItemTotalsForOrderList(java.util.List<WorkOrderDTO> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        java.util.List<Long> orderIds = orders.stream()
            .map(WorkOrderDTO::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
        if (orderIds.isEmpty()) {
            return;
        }

        java.util.Map<Long, Double> priceMap;
        java.util.Map<Long, Double> costMap;
        java.util.Map<Long, Long> countMap;
        if (tenantContextService.isSuperAdmin()) {
            priceMap = toDoubleMap(workOrderItemRepository.sumPriceByWorkOrderIds(orderIds));
            costMap = toDoubleMap(workOrderItemRepository.sumCostByWorkOrderIds(orderIds));
            countMap = toLongMap(workOrderItemRepository.countItemsByWorkOrderIds(orderIds));
        } else {
            Long companyId = tenantContextService.getCurrentCompanyId();
            if (companyId == null) {
                return;
            }
            priceMap = toDoubleMap(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(orderIds, companyId));
            costMap = toDoubleMap(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(orderIds, companyId));
            countMap = toLongMap(workOrderItemRepository.countItemsByWorkOrderIdsAndCompanyId(orderIds, companyId));
        }

        for (WorkOrderDTO order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }
            Long orderId = order.getId();
            if (countMap.getOrDefault(orderId, 0L) <= 0L) {
                continue;
            }
            order.setPrice(priceMap.getOrDefault(orderId, 0.0d));
            order.setCost(costMap.getOrDefault(orderId, 0.0d));
        }
    }

    private java.util.Map<Long, Double> toDoubleMap(java.util.List<Object[]> rows) {
        java.util.Map<Long, Double> result = new java.util.HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            try {
                result.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
            } catch (Exception ignored) {
                // Ignore malformed aggregate rows and keep DTO values.
            }
        }
        return result;
    }

    private java.util.Map<Long, Long> toLongMap(java.util.List<Object[]> rows) {
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            try {
                result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            } catch (Exception ignored) {
                // Ignore malformed aggregate rows and keep DTO values.
            }
        }
        return result;
    }

    @GetMapping("/orders/create")
    public String createOrderForm(Model model) {
        model.addAttribute("order", new WorkOrderDTO());
        model.addAttribute("clients", clientService.getActiveClients());
        model.addAttribute("workers", userService.getAssignableUsers());
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
            normalizeDeliveryFields(orderDTO);

            workOrderService.createWorkOrder(orderDTO);
            return redirectWithSuccess("/admin/orders", "Order created successfully", model);
        } catch (Exception e) {
            return redirectWithError("/admin/orders/create", "Error creating order: " + e.getMessage(), model);
        }
    }

    @GetMapping("/orders/{id}")
    public String orderDetails(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean autocopyPublicLink,
                               @RequestParam(required = false) String back,
                               Model model) {
        try {
            workOrderService.ensureTotalsSynced(id);
            WorkOrderDTO order = workOrderService.getWorkOrderById(id);
            com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityById(id);
            String activePublicToken = workOrderService.ensureActivePublicToken(id);
            order.setPublicToken(activePublicToken);
            orderEntity = workOrderService.getWorkOrderEntityById(id);
            com.printflow.entity.Company orderCompany = orderEntity.getCompany();
            if (orderCompany == null) {
                throw new ResourceNotFoundException("Order company not found");
            }
            Long orderCompanyId = orderCompany.getId();
            List<AttachmentDTO> attachments = fileStorageService.getAttachmentsByWorkOrder(id);
            List<UserDTO> workers = userService.getAssignableUsers();
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
            model.addAttribute("quoteStatuses", QuoteStatus.values());
            model.addAttribute("workers", workers);
            model.addAttribute("assignedWorker", assignedWorker);
            model.addAttribute("orderActivities", taskService.getActivitiesByWorkOrder(id));
            List<com.printflow.entity.WorkOrderItem> orderItems = tenantContextService.isSuperAdmin()
                ? workOrderItemRepository.findAllByWorkOrder_Id(id)
                : workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(id, orderCompanyId);
            model.addAttribute("orderItems", orderItems);
            WorkOrderProfitService.ProfitResult profitSummary = workOrderProfitService.calculateRealProfit(
                id, orderCompany);
            model.addAttribute("profitSummary", profitSummary);
            model.addAttribute("displayOrderPrice", resolveDisplayTotalPrice(orderItems, order.getPrice()));
            model.addAttribute("displayOrderCost", resolveDisplayTotalCost(orderItems, order.getCost()));
            model.addAttribute("activityFeed", activityLogService.getForWorkOrder(id));
            model.addAttribute("companyCurrency", orderCompany != null
                ? orderCompany.getCurrency() : "RSD");
            model.addAttribute("variants", productVariantRepository.findAllByCompany_Id(orderCompanyId));
            String trackingLang = resolvePublicTrackingLang(orderCompany);
            model.addAttribute("publicTrackingUrl", baseUrl + "/public/order/" + activePublicToken + "?lang=" + trackingLang);
            model.addAttribute("publicOrderToken", activePublicToken);
            model.addAttribute("autocopyPublicLink", autocopyPublicLink);
            model.addAttribute("backToOrders", resolveOrdersListRedirect(back));
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
                if (log.getDescription() != null && log.getDescription().toLowerCase(java.util.Locale.ROOT).contains("assignment")) {
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
            return redirectWithError("/admin/orders", "admin.orders.flash.not_found", model);
        } catch (Exception e) {
            return redirectWithError("/admin/orders", "admin.orders.flash.not_found", model);
        }
    }

    @GetMapping("/orders/{id}/items-fragment")
    public String orderItemsFragment(@PathVariable Long id, Model model) {
        workOrderService.ensureTotalsSynced(id);
        com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityById(id);
        WorkOrderDTO order = workOrderService.getWorkOrderById(id);
        com.printflow.entity.Company orderCompany = orderEntity.getCompany();
        if (orderCompany == null) {
            throw new ResourceNotFoundException("Order company not found");
        }
        Long orderCompanyId = orderCompany.getId();
        List<com.printflow.entity.WorkOrderItem> orderItems = tenantContextService.isSuperAdmin()
            ? workOrderItemRepository.findAllByWorkOrder_Id(id)
            : workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(id, orderCompanyId);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("profitSummary", workOrderProfitService.calculateRealProfit(id, orderCompany));
        model.addAttribute("companyCurrency", orderCompany != null ? orderCompany.getCurrency() : "RSD");
        model.addAttribute("displayOrderPrice", resolveDisplayTotalPrice(orderItems, order.getPrice()));
        model.addAttribute("displayOrderCost", resolveDisplayTotalCost(orderItems, order.getCost()));
        model.addAttribute("orderId", id);
        return "admin/orders/items-fragment :: itemsTable";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                    @RequestParam OrderStatus status,
                                    @RequestParam(required = false) String notes,
                                    @RequestParam(required = false) String returnTo,
                                    @RequestHeader(value = "Referer", required = false) String referer,
                                    Model model) {
        boolean backToList = "list".equalsIgnoreCase(returnTo);
        String detailsPath = "/admin/orders/" + id;
        String listPath = resolveOrdersListRedirect(referer);
        String successPath = backToList ? listPath : detailsPath;
        String errorPath = backToList ? listPath : detailsPath;
        try {
            workOrderService.updateWorkOrderStatus(id, status, notes);
            return redirectWithSuccess(successPath, "Order status updated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError(errorPath, "Error updating status: " + e.getMessage(), model);
        }
    }

    @GetMapping("/orders/{id}/edit")
    public String editOrderForm(@PathVariable Long id,
                                @RequestParam(required = false) String back,
                                Model model) {
        try {
            WorkOrderDTO order = workOrderService.getWorkOrderById(id);
            com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityById(id);
            String backToOrders = resolveOrdersListRedirect(back);
            model.addAttribute("order", order);
            model.addAttribute("clients", clientService.getActiveClients());
            model.addAttribute("workers", userService.getAssignableUsers());
            model.addAttribute("orderStatuses", OrderStatus.values());
            model.addAttribute("quoteStatuses", QuoteStatus.values());
            model.addAttribute("backToOrders", backToOrders);
            model.addAttribute("editBackProvided", back != null && !back.isBlank());
            model.addAttribute("companyCurrency", tenantContextService.getCurrentCompany() != null
                ? tenantContextService.getCurrentCompany().getCurrency() : "RSD");
            Long companyId = orderEntity.getCompany() != null ? orderEntity.getCompany().getId() : null;
            boolean priceManagedByItems = companyId != null
                && !workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(id, companyId).isEmpty();
            model.addAttribute("priceManagedByItems", priceManagedByItems);
            return "admin/orders/edit";
        } catch (ResourceNotFoundException e) {
            return redirectWithError("/admin/orders", "admin.orders.flash.not_found", model);
        } catch (Exception e) {
            return redirectWithError("/admin/orders", "admin.orders.flash.not_found", model);
        }
    }

    @PostMapping("/orders/{id}/edit")
    public String updateOrder(@PathVariable Long id,
                              @ModelAttribute WorkOrderDTO orderDTO,
                              @RequestParam Long clientId,
                              @RequestParam(required = false) Long assignedToId,
                              @RequestParam(required = false) String back,
                              Model model) {
        String backToOrders = resolveOrdersListRedirect(back);
        boolean backToList = back != null && !back.isBlank();
        String successPath = backToList ? backToOrders : "/admin/orders/" + id;
        String editPath = "/admin/orders/" + id + "/edit";
        String editWithBackPath = backToList ? (editPath + "?back=" + java.net.URLEncoder.encode(backToOrders, java.nio.charset.StandardCharsets.UTF_8)) : editPath;
        try {
            if (orderDTO.getPrice() != null && orderDTO.getPrice() < 0) {
                return redirectWithError(editWithBackPath, "Price cannot be negative", model);
            }
            if (orderDTO.getCost() != null && orderDTO.getCost() < 0) {
                return redirectWithError(editWithBackPath, "Cost cannot be negative", model);
            }
            orderDTO.setClientId(clientId);
            orderDTO.setAssignedToId(assignedToId);
            normalizeDeliveryFields(orderDTO);
            workOrderService.updateWorkOrder(id, orderDTO);
            return redirectWithSuccess(successPath, "Order updated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError(editWithBackPath, "Error updating order: " + e.getMessage(), model);
        }
    }

    @PostMapping("/orders/{id}/quote-status")
    public String updateQuoteStatus(@PathVariable Long id,
                                    @RequestParam QuoteStatus quoteStatus,
                                    @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime quoteValidUntil,
                                    @RequestParam(required = false) String returnTo,
                                    @RequestHeader(value = "Referer", required = false) String referer,
                                    Model model) {
        boolean backToList = "list".equalsIgnoreCase(returnTo);
        String detailsPath = "/admin/orders/" + id;
        String listPath = resolveOrdersListRedirect(referer);
        String successPath = backToList ? listPath : detailsPath;
        String errorPath = backToList ? listPath : detailsPath;
        try {
            workOrderService.updateQuoteStatus(id, quoteStatus, quoteValidUntil);
            return redirectWithSuccess(successPath, "Quote status updated", model);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return redirectWithError(errorPath, "Error updating quote status: " + e.getMessage(), model);
        }
    }

    private void normalizeDeliveryFields(WorkOrderDTO orderDTO) {
        if (orderDTO == null) {
            return;
        }
        if (orderDTO.getDeliveryType() == null) {
            orderDTO.setDeliveryType(DeliveryType.PICKUP);
        }
        orderDTO.setCourierName(trimToNull(orderDTO.getCourierName()));
        orderDTO.setTrackingNumber(trimToNull(orderDTO.getTrackingNumber()));
        orderDTO.setDeliveryAddress(trimToNull(orderDTO.getDeliveryAddress()));
        orderDTO.setDeliveryRecipientName(trimToNull(orderDTO.getDeliveryRecipientName()));
        orderDTO.setDeliveryRecipientPhone(trimToNull(orderDTO.getDeliveryRecipientPhone()));
        orderDTO.setDeliveryCity(trimToNull(orderDTO.getDeliveryCity()));
        orderDTO.setDeliveryPostalCode(trimToNull(orderDTO.getDeliveryPostalCode()));
        orderDTO.setShippingNote(trimToNull(orderDTO.getShippingNote()));
        shipmentFacade.normalize(orderDTO);
        if (orderDTO.getDeliveryType() == DeliveryType.PICKUP) {
            return;
        }
        if (orderDTO.getDeliveryAddress() == null) {
            throw new IllegalArgumentException("Delivery address is required for courier delivery.");
        }
        if (orderDTO.getDeliveryRecipientName() == null) {
            throw new IllegalArgumentException("Recipient name is required for courier delivery.");
        }
        if (orderDTO.getDeliveryRecipientPhone() == null) {
            throw new IllegalArgumentException("Recipient phone is required for courier delivery.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double resolveDisplayTotalPrice(List<com.printflow.entity.WorkOrderItem> items, Double fallback) {
        if (items == null || items.isEmpty()) {
            return fallback != null ? fallback : 0.0d;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (com.printflow.entity.WorkOrderItem item : items) {
            if (item == null || item.getCalculatedPrice() == null) {
                continue;
            }
            total = total.add(item.getCalculatedPrice());
        }
        return total.doubleValue();
    }

    private Double resolveDisplayTotalCost(List<com.printflow.entity.WorkOrderItem> items, Double fallback) {
        if (items == null || items.isEmpty()) {
            return fallback != null ? fallback : 0.0d;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (com.printflow.entity.WorkOrderItem item : items) {
            if (item == null || item.getCalculatedCost() == null) {
                continue;
            }
            total = total.add(item.getCalculatedCost());
        }
        return total.doubleValue();
    }

    private String resolveOrdersListRedirect(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/admin/orders";
        }
        try {
            java.net.URI uri = java.net.URI.create(referer);
            if (!"/admin/orders".equals(uri.getPath())) {
                return "/admin/orders";
            }
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return "/admin/orders";
            }
            return "/admin/orders?" + query;
        } catch (IllegalArgumentException ex) {
            return "/admin/orders";
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

    @GetMapping("/orders/{id}/pdf/quote")
    public ResponseEntity<ByteArrayResource> downloadQuotePdf(@PathVariable Long id,
                                                              @RequestParam(required = false) String lang,
                                                              java.util.Locale locale) {
        workOrderService.ensureTotalsSynced(id);
        com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityById(id);
        WorkOrderDTO order = workOrderService.getWorkOrderById(id);
        Company company = orderEntity.getCompany();
        List<com.printflow.entity.WorkOrderItem> items = resolveOrderItemsForPdf(id, company);
        byte[] pdf = orderPdfService.generateQuotePdf(order, company, items, resolveRequestedLocale(lang, locale));
        auditLogService.log(com.printflow.entity.enums.AuditAction.DOWNLOAD, "WorkOrder", id,
            null, null, "Quote PDF downloaded", company);
        ByteArrayResource resource = new ByteArrayResource(pdf);
        String fileName = "quote-" + (order.getOrderNumber() != null ? order.getOrderNumber() : id) + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdf.length)
            .body(resource);
    }

    @GetMapping("/orders/{id}/pdf/summary")
    public ResponseEntity<ByteArrayResource> downloadSummaryPdf(@PathVariable Long id,
                                                                @RequestParam(required = false) String lang,
                                                                java.util.Locale locale) {
        workOrderService.ensureTotalsSynced(id);
        com.printflow.entity.WorkOrder orderEntity = workOrderService.getWorkOrderEntityById(id);
        WorkOrderDTO order = workOrderService.getWorkOrderById(id);
        Company company = orderEntity.getCompany();
        List<com.printflow.entity.WorkOrderItem> items = resolveOrderItemsForPdf(id, company);
        byte[] pdf = orderPdfService.generateOrderSummaryPdf(order, company, items, resolveRequestedLocale(lang, locale));
        auditLogService.log(com.printflow.entity.enums.AuditAction.DOWNLOAD, "WorkOrder", id,
            null, null, "Order summary PDF downloaded", company);
        ByteArrayResource resource = new ByteArrayResource(pdf);
        String fileName = "order-summary-" + (order.getOrderNumber() != null ? order.getOrderNumber() : id) + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdf.length)
            .body(resource);
    }

    private List<com.printflow.entity.WorkOrderItem> resolveOrderItemsForPdf(Long workOrderId, Company company) {
        if (tenantContextService.isSuperAdmin()) {
            return workOrderItemRepository.findAllByWorkOrder_Id(workOrderId);
        }
        Long companyId = company != null ? company.getId() : null;
        if (companyId == null) {
            return workOrderItemRepository.findAllByWorkOrder_Id(workOrderId);
        }
        return workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(workOrderId, companyId);
    }

    private boolean sendTrackingEmailForOrder(com.printflow.entity.WorkOrder order) {
        if (order == null || order.getClient() == null || order.getClient().getEmail() == null || order.getClient().getEmail().isBlank()) {
            return false;
        }
        String publicToken = workOrderService.ensureActivePublicToken(order.getId());
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
        String trackingLang = resolvePublicTrackingLang(order.getCompany());
        vars.put("trackingUrl", baseUrl + "/public/order/" + publicToken + "?lang=" + trackingLang);
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
        msg.setTextBody("Track your order: " + baseUrl + "/public/order/" + publicToken + "?lang=" + trackingLang);
        emailService.send(msg, order.getCompany(), "public-tracking-link");
        return true;
    }

    private String resolvePublicTrackingLang(com.printflow.entity.Company company) {
        return isSerbianCompany(company) ? "sr" : "en";
    }

    private java.util.Locale resolveRequestedLocale(String lang, java.util.Locale fallback) {
        if (lang != null && !lang.isBlank()) {
            String normalized = lang.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("sr")) {
                return new java.util.Locale("sr");
            }
            if (normalized.startsWith("en")) {
                return java.util.Locale.ENGLISH;
            }
        }
        return fallback != null ? fallback : java.util.Locale.ENGLISH;
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
        String lower = address.toLowerCase(java.util.Locale.ROOT);
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
    public String legacyUserPermissionsRedirect(@PathVariable Long id, Model model) {
        try {
            userService.getUserById(id);
            return "redirect:/admin/users/edit/" + id;
        } catch (RuntimeException ex) {
            return redirectWithError("/admin/users", "admin.users.flash.not_found", model);
        }
    }

    @PostMapping("/users/{id}/toggle-active")
    public String toggleUserActive(@PathVariable Long id, Model model) {
        try {
            UserDTO user = userService.getUserById(id);
            if (user.isActive()) {
                userService.deactivateUser(id);
                return redirectWithSuccess("/admin/users", "admin.users.flash.deactivated", model);
            }
            userService.activateUser(id);
            return redirectWithSuccess("/admin/users", "admin.users.flash.activated", model);
        } catch (RuntimeException e) {
            return redirectWithError("/admin/users", "admin.users.flash.not_found", model);
        }
    }

    // ==================== TASK REVIEW ====================

    @GetMapping("/tasks")
    public String listTasksForReview(@RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(required = false) Integer size,
                                     Model model) {
        notificationService.markAllAsRead(getCurrentUserId());
        String statusParam = status != null ? status.trim().toUpperCase(java.util.Locale.ROOT) : "";
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
            model.addAttribute("workers", userService.getAssignableUsers());
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
            model.addAttribute("workers", userService.getAssignableUsers());
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
        List<UserDTO> workers = userService.getAssignableUsers();
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
            List<UserDTO> workers = userService.getAssignableUsers();
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
