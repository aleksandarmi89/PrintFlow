package com.printflow.config;

import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.enums.Language;
import com.printflow.repository.UserRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.AuditLogRepository;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.BillingPlanConfigRepository;
import com.printflow.service.NotificationService;
import com.printflow.entity.Company;
import com.printflow.entity.Client;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.Task;
import com.printflow.entity.AuditLog;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.PrintType;
import com.printflow.entity.enums.TaskPriority;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.entity.enums.AuditAction;
import com.printflow.entity.BillingPlanConfig;
import com.printflow.entity.enums.PlanTier;
import com.printflow.entity.enums.BillingInterval;
import com.printflow.util.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Configuration
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {
    
    // Ručni Logger umesto @Slf4j
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    
    private final PasswordEncoder passwordEncoder;

    // Ručni konstruktor umesto @RequiredArgsConstructor
    public DataInitializer(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    
    @Bean
    public CommandLineRunner initData(UserRepository userRepository,
                                      CompanyRepository companyRepository,
                                      BillingPlanConfigRepository billingPlanConfigRepository) {
        return args -> {
            seedBillingPlanConfigs(billingPlanConfigRepository);
            Company defaultCompany = companyRepository.findByName("Default Print Shop")
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName("Default Print Shop");
                    company.setSlug("default-print-shop");
                    company.setActive(true);
                    return companyRepository.save(company);
                });

            Company superAdminCompany = companyRepository.findByName("PrintFlow Admin")
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName("PrintFlow Admin");
                    company.setSlug("printflow-admin");
                    company.setActive(true);
                    return companyRepository.save(company);
                });

            // Ensure all existing users have a company assigned
            userRepository.findByCompanyIsNull().forEach(user -> {
                user.setCompany(defaultCompany);
                userRepository.save(user);
                log.info("Assigned default company to user: {}", user.getUsername());
            });

            if (userRepository.findByUsername("superadmin").isEmpty()) {
                User superAdmin = new User();
                superAdmin.setUsername("superadmin");
                superAdmin.setPassword(passwordEncoder.encode("superadmin123"));
                superAdmin.setFullName("Super Admin");
                superAdmin.setEmail("superadmin@printflow.com");
                superAdmin.setPhone("+381 11 000 0000");
                superAdmin.setRole(Role.SUPER_ADMIN);
                superAdmin.setLanguage(Language.SR);
                superAdmin.setActive(true);
                superAdmin.setCompany(superAdminCompany);

                userRepository.save(superAdmin);
                log.info("Created super admin user: superadmin/superadmin123");
            }

            // Kreiranje admin korisnika ako ne postoji
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setFullName("System Administrator");
                admin.setEmail("admin@printflow.com");
                admin.setPhone("+381 11 123 4567");
                admin.setRole(Role.ADMIN);
                admin.setLanguage(Language.SR);
                admin.setActive(true);
                admin.setCompany(defaultCompany);
                
                userRepository.save(admin);
                log.info("Created admin user: admin/admin123");
            }

            // Dodatni admin korisnik (fallback)
            if (userRepository.findByUsername("admin2").isEmpty()) {
                User admin2 = new User();
                admin2.setUsername("admin2");
                admin2.setPassword(passwordEncoder.encode("admin2123"));
                admin2.setFullName("Secondary Admin");
                admin2.setEmail("admin2@printflow.com");
                admin2.setPhone("+381 11 123 4569");
                admin2.setRole(Role.ADMIN);
                admin2.setLanguage(Language.SR);
                admin2.setActive(true);
                admin2.setCompany(defaultCompany);

                userRepository.save(admin2);
                log.info("Created admin user: admin2/admin2123");
            }
            
            // Kreiranje dizajner korisnika
            if (userRepository.findByUsername("designer").isEmpty()) {
                User designer = new User();
                designer.setUsername("designer");
                designer.setPassword(passwordEncoder.encode("designer123"));
                designer.setFullName("Jane Designer");
                designer.setEmail("designer@printflow.com");
                designer.setPhone("+381 11 123 4568");
                designer.setRole(Role.WORKER_DESIGN);
                designer.setLanguage(Language.SR);
                designer.setActive(true);
                designer.setCompany(defaultCompany);
                
                userRepository.save(designer);
                log.info("Created designer user: designer/designer123");
            }
            
            // Kreiranje štampar korisnika
            if (userRepository.findByUsername("printer").isEmpty()) {
                User printer = new User();
                printer.setUsername("printer");
                printer.setPassword(passwordEncoder.encode("printer123"));
                printer.setFullName("John Printer");
                printer.setEmail("printer@printflow.com");
                printer.setPhone("+381 11 123 4569");
                printer.setRole(Role.WORKER_PRINT);
                printer.setLanguage(Language.SR);
                printer.setActive(true);
                printer.setCompany(defaultCompany);
                
                userRepository.save(printer);
                log.info("Created printer user: printer/printer123");
            }
            
            // Kreiranje general radnika
            if (userRepository.findByUsername("worker").isEmpty()) {
                User worker = new User();
                worker.setUsername("worker");
                worker.setPassword(passwordEncoder.encode("worker123"));
                worker.setFullName("General Worker");
                worker.setEmail("worker@printflow.com");
                worker.setPhone("+381 11 123 4570");
                worker.setRole(Role.WORKER_GENERAL);
                worker.setLanguage(Language.SR);
                worker.setActive(true);
                worker.setCompany(defaultCompany);
                
                userRepository.save(worker);
                log.info("Created worker user: worker/worker123");
            }
            
            log.info("Data initialization completed");
        };
    }

    private void seedBillingPlanConfigs(BillingPlanConfigRepository repository) {
        for (PlanTier plan : PlanTier.values()) {
            for (BillingInterval interval : BillingInterval.values()) {
                if (repository.findByPlanAndInterval(plan, interval).isPresent()) {
                    continue;
                }
                BillingPlanConfig config = new BillingPlanConfig();
                config.setPlan(plan);
                config.setInterval(interval);
                String priceId = "price_test_" + plan.name().toLowerCase() + "_" + interval.name().toLowerCase();
                config.setStripePriceId(priceId);
                config.setActive(true);
                repository.save(config);
            }
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.seed.sample.enabled", havingValue = "true", matchIfMissing = false)
    public CommandLineRunner seedSampleData(UserRepository userRepository,
                                            CompanyRepository companyRepository,
                                            ClientRepository clientRepository,
                                            WorkOrderRepository workOrderRepository,
                                            TaskRepository taskRepository,
                                            AuditLogRepository auditLogRepository,
                                            AttachmentRepository attachmentRepository,
                                            NotificationService notificationService,
                                            @Value("${app.seed.sample.companies:2}") int companiesCount,
                                            @Value("${app.seed.sample.users-per-company:6}") int usersPerCompany,
                                            @Value("${app.seed.sample.clients-per-company:8}") int clientsPerCompany,
                                            @Value("${app.seed.sample.orders-per-company:20}") int ordersPerCompany,
                                            @Value("${app.seed.sample.tasks-per-order:2}") int tasksPerOrder,
                                            @Value("${app.seed.sample.reset:false}") boolean reset,
                                            @Value("${app.seed.sample.force:false}") boolean force) {
        return args -> {
            if (reset) {
                auditLogRepository.deleteAll();
                attachmentRepository.deleteAll();
                taskRepository.deleteAll();
                workOrderRepository.deleteAll();
                clientRepository.deleteAll();
                userRepository.deleteAll();
                companyRepository.deleteAll();
            }

            if (!reset && !force && (workOrderRepository.count() > 0 || clientRepository.count() > 0)) {
                log.info("Sample seed skipped: existing data found.");
                return;
            }

            Random random = new Random(42);
            List<Company> companies = companyRepository.findAll();
            if (companies.size() < companiesCount) {
                for (int i = companies.size(); i < companiesCount; i++) {
                    Company c = new Company();
                    c.setName("PrintFlow Company " + (i + 1));
                    c.setSlug(SlugUtil.toSlug(c.getName()));
                    c.setActive(true);
                    companies.add(companyRepository.save(c));
                }
            }

            Company primaryCompany;
            if (companies.isEmpty()) {
                Company c = new Company();
                c.setName("PrintFlow Company 1");
                c.setSlug(SlugUtil.toSlug(c.getName()));
                c.setActive(true);
                primaryCompany = companyRepository.save(c);
            } else {
                primaryCompany = companies.get(0);
            }
            ensureSuperAdmin(userRepository, primaryCompany);
            ensureUser(userRepository, primaryCompany, "aleksandar", "Aleksandar", "Admin", Role.ADMIN, random);

            for (Company company : companies) {
                String slug = company.getName().toLowerCase().replaceAll("[^a-z0-9]+", "");
                User admin = ensureUser(userRepository, company, slug + "_admin", "Admin", "Owner", Role.ADMIN, random);
                User manager = ensureUser(userRepository, company, slug + "_manager", "Manager", "Lead", Role.MANAGER, random);

                List<Role> workerRoles = List.of(Role.WORKER_DESIGN, Role.WORKER_PRINT, Role.WORKER_GENERAL);
                for (int i = 0; i < Math.max(1, usersPerCompany - 2); i++) {
                    Role role = workerRoles.get(i % workerRoles.size());
                    User worker = ensureUser(userRepository, company,
                        slug + "_worker_" + (i + 1),
                        "Worker", String.valueOf(i + 1),
                        role, random);
                }
            }

            List<Client> clients = new ArrayList<>();
            for (Company company : companies) {
                for (int i = 0; i < clientsPerCompany; i++) {
                    Client client = new Client();
                    client.setCompany(company);
                    client.setCompanyName(company.getName() + " Client " + (i + 1));
                    client.setContactPerson("Contact " + (i + 1));
                    client.setPhone("+381 11 100 " + String.format("%04d", i));
                    client.setEmail("client" + (i + 1) + "@" + company.getName().replaceAll("\\s+", "").toLowerCase() + ".com");
                    client.setCity("Belgrade");
                    client.setCountry("Serbia");
                    client.setCompanyId("C-" + company.getId() + "-" + (i + 1));
                    client.setNotes("Sample client notes.");
                    clients.add(clientRepository.save(client));
                }
            }

            List<OrderStatus> statusFlow = List.of(
                OrderStatus.NEW,
                OrderStatus.IN_DESIGN,
                OrderStatus.WAITING_CLIENT_APPROVAL,
                OrderStatus.IN_PRINT,
                OrderStatus.READY_FOR_DELIVERY,
                OrderStatus.SENT
            );

            List<WorkOrder> orders = new ArrayList<>();
            for (Company company : companies) {
                List<Client> companyClients = clients.stream()
                    .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(company.getId()))
                    .collect(Collectors.toList());
                List<User> companyWorkers = userRepository.findByCompany_IdAndRoleInAndActiveTrue(company.getId(),
                    List.of(Role.WORKER_DESIGN, Role.WORKER_PRINT, Role.WORKER_GENERAL));
                List<User> companyAdmins = userRepository.findByCompany_IdAndRoleInAndActiveTrue(company.getId(),
                    List.of(Role.ADMIN, Role.MANAGER));

                for (int i = 0; i < ordersPerCompany; i++) {
                    WorkOrder order = new WorkOrder();
                    order.setCompany(company);
                    order.setTitle("Order " + (i + 1) + " - " + company.getName());
                    order.setDescription("Sample description for order " + (i + 1));
                    order.setSpecifications("Size: A3, Qty: " + (50 + random.nextInt(200)));
                    order.setPriority(1 + random.nextInt(10));
                    order.setClient(companyClients.get(random.nextInt(companyClients.size())));
                    order.setAssignedTo(companyWorkers.isEmpty() ? null : companyWorkers.get(random.nextInt(companyWorkers.size())));
                    order.setCreatedBy(companyAdmins.isEmpty() ? null : companyAdmins.get(random.nextInt(companyAdmins.size())));
                    order.setPrintType(PrintType.values()[random.nextInt(PrintType.values().length)]);

                    double price = 50 + random.nextInt(450);
                    double cost = Math.max(10, price * (0.4 + random.nextDouble() * 0.4));
                    order.setPrice(price);
                    order.setCost(cost);
                    order.setPaid(random.nextBoolean());

                    LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(60));
                    LocalDateTime deadline = createdAt.plusDays(3 + random.nextInt(20));
                    order.setDeadline(deadline);

                    OrderStatus status = statusFlow.get(random.nextInt(statusFlow.size()));
                    order.setStatus(status);
                    orders.add(workOrderRepository.save(order));
                }
            }

            for (WorkOrder order : orders) {
                LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(60));
                order.setCreatedAt(createdAt);
                order.setUpdatedAt(createdAt.plusDays(random.nextInt(10)));
                workOrderRepository.save(order);

                for (int t = 0; t < tasksPerOrder; t++) {
                    Task task = new Task();
                    task.setCompany(order.getCompany());
                    task.setWorkOrder(order);
                    task.setTitle("Task " + (t + 1) + " for " + order.getOrderNumber());
                    task.setDescription("Sample task description.");
                    task.setPriority(TaskPriority.values()[random.nextInt(TaskPriority.values().length)]);
                    task.setStatus(TaskStatus.NEW);
                    task.setAssignedTo(order.getAssignedTo());
                    task.setCreatedBy(order.getCreatedBy());
                    task.setDueDate(order.getDeadline() != null ? order.getDeadline().minusDays(random.nextInt(3)) : null);
                    Task savedTask = taskRepository.save(task);
                    savedTask.setCreatedAt(createdAt);
                    savedTask.setUpdatedAt(createdAt.plusDays(random.nextInt(5)));
                    taskRepository.save(savedTask);
                    if (savedTask.getAssignedTo() != null) {
                        try {
                            notificationService.sendTaskAssignedNotification(savedTask.getId(),
                                savedTask.getAssignedTo().getId(), savedTask.getTitle());
                        } catch (Exception ex) {
                            log.debug("Seed: skipping task assigned notification (no request scope).");
                        }
                    }
                }

                int statusSteps = 2 + random.nextInt(3);
                LocalDateTime auditTime = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now().minusDays(7);
                for (int s = 0; s < statusSteps; s++) {
                    OrderStatus st = statusFlow.get(Math.min(s, statusFlow.size() - 1));
                    AuditLog logEntry = new AuditLog();
                    logEntry.setAction(AuditAction.STATUS_CHANGE);
                    logEntry.setEntityType("WorkOrder");
                    logEntry.setEntityId(order.getId());
                    logEntry.setNewValue(st.name());
                    logEntry.setDescription("Status changed to " + st.name());
                    logEntry.setUser(order.getAssignedTo());
                    logEntry.setCompany(order.getCompany());
                    logEntry.setIpAddress("127.0.0." + (1 + random.nextInt(50)));
                    logEntry.setUserAgent("SeedRunner/1.0");
                    AuditLog savedLog = auditLogRepository.save(logEntry);
                    savedLog.setCreatedAt(auditTime.plusDays(s));
                    auditLogRepository.save(savedLog);
                }
            }

            log.info("Sample data seed completed: {} companies, {} clients, {} orders.",
                companies.size(), clients.size(), orders.size());
        };
    }

    private void ensureSuperAdmin(UserRepository userRepository, Company company) {
        userRepository.findByUsername("superadmin").orElseGet(() -> {
            User user = new User();
            user.setUsername("superadmin");
            user.setPassword(passwordEncoder.encode("superadmin123"));
            user.setFirstName("Super");
            user.setLastName("Admin");
            user.setFullName("Super Admin");
            user.setEmail("superadmin@printflow.com");
            user.setPhone("+381 11 000 0000");
            user.setRole(Role.SUPER_ADMIN);
            user.setLanguage(Language.SR);
            user.setActive(true);
            user.setAvailable(true);
            user.setCompany(company);
            return userRepository.save(user);
        });
    }

    private User ensureUser(UserRepository userRepository, Company company, String username,
                            String firstName, String lastName, Role role, Random random) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode("pass123"));
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setFullName(firstName + " " + lastName);
            user.setEmail(username + "@printflow.local");
            user.setPhone("+381 11 200 " + String.format("%04d", random.nextInt(9999)));
            user.setRole(role);
            user.setLanguage(Language.SR);
            user.setActive(true);
            user.setAvailable(true);
            user.setCompany(company);
            return userRepository.save(user);
        });
    }
}
