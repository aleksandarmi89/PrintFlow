package com.printflow.controller;

import com.printflow.dto.*;
import com.printflow.entity.User;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.service.*;
import jakarta.servlet.http.HttpServletRequest;
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

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController extends BaseController {

    private final DashboardService dashboardService;
    private final ClientService clientService;
    private final WorkOrderService workOrderService;
    private final UserService userService;
    private final ExcelImportService excelImportService;
    private final FileStorageService fileStorageService;

    public AdminController(DashboardService dashboardService,
                           ClientService clientService,
                           WorkOrderService workOrderService,
                           UserService userService,
                           ExcelImportService excelImportService,
                           FileStorageService fileStorageService) {
        this.dashboardService = dashboardService;
        this.clientService = clientService;
        this.workOrderService = workOrderService;
        this.userService = userService;
        this.excelImportService = excelImportService;
        this.fileStorageService = fileStorageService;
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardStatsDTO stats = dashboardService.getDashboardStats();
        model.addAttribute("stats", stats);

        List<WorkOrderDTO> recentOrders = workOrderService.getAllWorkOrders().stream()
                .limit(10)
                .toList();
        model.addAttribute("recentOrders", recentOrders);

        List<WorkOrderDTO> overdueOrders = workOrderService.getOverdueOrders().stream()
                .limit(5)
                .toList();
        model.addAttribute("overdueOrders", overdueOrders);

        return "admin/dashboard";
    }

    // ==================== CLIENTS ====================

    @GetMapping("/clients")
    public String clients(Model model, @RequestParam(required = false) String search) {
        List<ClientDTO> clients;
        if (search != null && !search.trim().isEmpty()) {
            clients = clientService.searchClients(search);
            model.addAttribute("search", search);
        } else {
            clients = clientService.getActiveClients();
        }

        model.addAttribute("clients", clients);
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
            return "admin/clients/edit";
        } catch (Exception e) {
            return redirectWithError("/admin/clients", "Client not found", model);
        }
    }

    @PostMapping("/clients/edit/{id}")
    public String updateClient(@PathVariable Long id, @ModelAttribute ClientDTO clientDTO, Model model) {
        try {
            clientService.updateClient(id, clientDTO);
            return redirectWithSuccess("/admin/clients", "Client updated successfully", model);
        } catch (Exception e) {
            return redirectWithError("/admin/clients/edit/" + id, "Error updating client: " + e.getMessage(), model);
        }
    }

    // ==================== ORDERS ====================

    @GetMapping("/orders")
    public String orders(Model model,
                         @RequestParam(required = false) String search,
                         @RequestParam(required = false) OrderStatus status) {
        List<WorkOrderDTO> orders;

        if (search != null && !search.trim().isEmpty()) {
            orders = workOrderService.searchWorkOrders(search);
            model.addAttribute("search", search);
        } else if (status != null) {
            orders = workOrderService.getWorkOrdersByStatus(status);
            model.addAttribute("status", status);
        } else {
            orders = workOrderService.getAllWorkOrders();
        }

        model.addAttribute("orders", orders);
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "admin/orders/list";
    }

    @GetMapping("/orders/create")
    public String createOrderForm(Model model) {
        model.addAttribute("order", new WorkOrderDTO());
        model.addAttribute("clients", clientService.getActiveClients());
        model.addAttribute("users", userService.getActiveUsers());
        return "admin/orders/create";
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
    public String orderDetails(@PathVariable Long id, Model model) {
        try {
            WorkOrderDTO order = workOrderService.getWorkOrderById(id);
            List<AttachmentDTO> attachments = fileStorageService.getAttachmentsByWorkOrder(id);

            model.addAttribute("order", order);
            model.addAttribute("attachments", attachments);
            model.addAttribute("orderStatuses", OrderStatus.values());
            model.addAttribute("users", userService.getActiveUsers());

            return "admin/orders/details";
        } catch (Exception e) {
            return redirectWithError("/admin/orders", "Order not found", model);
        }
    }

    // ==================== USERS (REVIDIRANO) ====================

    @GetMapping("/users")
    public String listUsers(@RequestParam(defaultValue = "0") int page, Model model) {
        int pageSize = 20;
        Page<User> userPage = userService.findAll(page, pageSize);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("pagination", new PaginationDTO(
                userPage.getNumber(),
                userPage.getSize(),
                (int) userPage.getTotalElements(),
                userPage.getNumber() * userPage.getSize() + 1,
                Math.min((userPage.getNumber() + 1) * userPage.getSize(), (int) userPage.getTotalElements())
        ));

        UserStatisticsDTO stats = userService.getUserStatistics();
        model.addAttribute("stats", stats);

        return "admin/users/list";
    }

    @GetMapping("/users/create")
    public String createUserForm(Model model) {
        model.addAttribute("user", new UserDTO());
        model.addAttribute("roles", com.printflow.entity.User.Role.values());
        // Koristimo zajednički user-form
        return "admin/users/user-form"; 
    }

    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute("user") UserDTO userDTO, BindingResult result, Model model) {
    	// Ručno postavljanje username-a ako je on obavezan u bazi/DTO-u
        if (userDTO.getUsername() == null || userDTO.getUsername().isEmpty()) {
            userDTO.setUsername(userDTO.getEmail());
        }
    	
    	if (result.hasErrors()) {
            model.addAttribute("roles", com.printflow.entity.User.Role.values());
            return "admin/users/user-form";
        }

        try {
            userService.createUser(userDTO);
            
            return redirectWithSuccess("/admin/users", "User created successfully", model);
        } catch (Exception e) {
            model.addAttribute("roles", com.printflow.entity.User.Role.values());
            model.addAttribute("errorMessage", "Error creating user: " + e.getMessage());
            return "admin/users/user-form";
        }
    }

    @GetMapping("/users/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        try {
            UserDTO user = userService.getUserById(id);
            model.addAttribute("user", user);
            model.addAttribute("roles", com.printflow.entity.User.Role.values());

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
            model.addAttribute("roles", com.printflow.entity.User.Role.values());
            return "admin/users/user-form";
        }

        try {
            userService.updateUser(id, userDTO);
            return redirectWithSuccess("/admin/users", "User updated successfully", model);
        } catch (Exception e) {
            model.addAttribute("roles", com.printflow.entity.User.Role.values());
            model.addAttribute("errorMessage", "Error updating user: " + e.getMessage());
            return "admin/users/user-form";
        }
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