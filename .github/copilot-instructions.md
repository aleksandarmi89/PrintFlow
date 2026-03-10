# PrintFlow SaaS - AI Copilot Instructions

## Project Overview
**PrintFlow** is a Spring Boot 3.2 (Java 17) SaaS platform for managing print shop workflows. It uses MySQL, Thymeleaf templating, and role-based access control (RBAC). Core domain: work orders → tasks → time tracking for workers, with admin and public client interfaces.

**Key Stack:**
- Spring Boot 3.2, Spring Security, Spring Data JPA
- MySQL 8.0+ with Hibernate ORM
- Thymeleaf for server-side templating with layout dialects
- Lombok for boilerplate reduction
- i18n: English + Serbian (sr) with fallback locales
- File uploads: 100MB max via local `./uploads/` directory

---

## Architecture & Data Flow

### Domain Model Structure
```
WorkOrder (main entity)
├── id, orderNumber, title, status (NEW, IN_PROGRESS, COMPLETED, etc.)
├── Client (ManyToOne) - external order requester
├── Tasks (OneToMany) - work breakdown
├── Attachments, Comments (OneToMany) - collaboration
└── publicToken (UUID) - unauthenticated tracking link

Task (work unit)
├── WorkOrder (ManyToOne)
├── assignedTo (User/Worker, ManyToOne)
├── status (NEW, ASSIGNED, IN_PROGRESS, COMPLETED, etc.)
├── priority (1-10 scale, 10 = highest)
├── actualMinutes, timerStartedAt - time tracking for workers
└── TaskActivity (OneToMany) - audit log

User (role-based)
├── Role: ADMIN, WORKER_DESIGN, WORKER_PRINT, WORKER_PACK, CLIENT
├── languagePreference (sr/en)
├── available flag - worker availability status
└── credentials: username, email, phone, department

Client (external entity)
└── companyName, contactPerson, email, phone
```

### Key Service Layer Patterns
1. **Service-DTO-Repository pattern:** All controllers work with DTOs, never expose entities directly (e.g., `ClientService.convertToDTO()`)
2. **@Transactional propagation:** Services use `@Transactional` for batch operations; avoid N+1 via eager loading in repositories
3. **Domain validation:** Unique constraints (email, company ID) checked in service before DB save, exception-based error handling
4. **File handling:** `FileStorageService` centralizes disk I/O; uploads mapped in `MvcConfig` to `./uploads/` directory

### Request Flow Example: Create Work Order
1. `AdminController.createWorkOrder()` validates form input
2. Maps form → `WorkOrderDTO`
3. Calls `WorkOrderService.createWorkOrder(dto)`
4. Service → repository save + task/attachment creation
5. Response → redirect with flash message or Thymeleaf model attribute

---

## Developer Workflows

### Build & Run (Maven)
```bash
# Clean build
mvn clean install

# Run locally (port 8088)
mvn spring-boot:run

# Run tests (if present in src/test)
mvn test
```

### Database
- **Auto-migration:** `spring.jpa.hibernate.ddl-auto: update` applies schema changes automatically
- **MySQL connection:** Default localhost:3306/printflow_db with SSL disabled for dev
- **Caching:** Simple cache for `clients`, `users`, `orders` (refresh on data changes)

### File Uploads
- Physical path: `./uploads/` (relative to app root)
- Web access: `/uploads/**` mapped in `MvcConfig`
- Max size: 100MB (configured in application.yaml)
- **Pattern:** `FileStorageService` handles save/delete; call it from controllers

---

## Project-Specific Conventions

### Security & Authorization
- **Request matchers in `SecurityConfig`:**
  - Public: `/`, `/public/**`, `/static/**`, `/login`, `/register`
  - Admin only: `/admin/**` → `Role.ADMIN`
  - Worker: `/worker/**` → `WORKER_DESIGN`, `WORKER_PRINT`, `WORKER_PACK`
  - Client: `/public/order-tracking/**` (public token-based)
- **Pattern:** Never hardcode auth checks; use `@PreAuthorize` or `SecurityConfig` matchers

### Naming Conventions
- **Controllers:** `{Feature}Controller` (e.g., `AdminController`, `WorkerController`, `PublicController`)
- **Services:** `{Entity}Service` (e.g., `ClientService`, `WorkOrderService`)
- **Repositories:** `{Entity}Repository` (e.g., `ClientRepository`)
- **DTOs:** `{Entity}DTO` (e.g., `WorkOrderDTO`, `TaskDTO`)
- **Entities:** Singular form, `@Entity @Table(name = "plural_snake_case")`

### Thymeleaf & i18n
- **Message keys:** Use snake_case in `messages.properties` and `messages_sr.properties`
- **Access in templates:** `[[#{key}]]` for message lookup with fallback to English
- **Language preference:** User language stored in `User.languagePreference`; refresh after change
- **Layout inheritance:** All templates extend `layout/base.html` via Thymeleaf layout dialect

### DTO/Entity Mapping Pattern
```java
// Service method pattern (e.g., ClientService)
private ClientDTO convertToDTO(Client client) {
    ClientDTO dto = new ClientDTO();
    dto.setId(client.getId());
    // ... manual mapping
    return dto;
}

private Client convertToEntity(ClientDTO dto) {
    // For updates: load existing, modify, save
    // For creates: new Client(), set fields, validate unique constraints
}
```

### Validation & Error Handling
- **Runtime exceptions:** Throw `RuntimeException` with meaningful message for business logic errors (e.g., "Email already exists")
- **Form validation:** Use `@Valid` + `BindingResult` in controllers; check results before processing
- **No try-catch in controllers:** Let `@ControllerAdvice` (if present) or default error handler manage exceptions

### Time & Date Handling
- **Format:** `yyyy-MM-dd HH:mm:ss` (configured in `application.yaml`)
- **Timezone:** UTC with `serverTimezone=UTC` in datasource URL
- **Tracking:** `createdAt`, `updatedAt` timestamps auto-set on entity creation/update

---

## Integration Points & Dependencies

### External Services
- **Email:** Not yet implemented (check `NotificationService` for placeholder)
- **Excel Import:** `ExcelImportService` uses Apache POI for client/order bulk upload (requires XSSF workbook)
- **Time Tracking:** `TaskService` manages timer start/stop with `actualMinutes` calculation

### API Response Pattern
- Controllers return Thymeleaf template names (not REST JSON by default)
- Use `@GetMapping`, `@PostMapping` with `@RequestParam`, `@PathVariable`
- Flash attributes for success/error messages across redirects

### Audit & Logging
- `AuditLog` entity + `TaskActivity` entity track changes
- Service layer should trigger audit record creation on sensitive operations
- Use `@Transactional` to ensure audit record persists with main operation

---

## Common Tasks & Code Examples

### Adding a New Admin Feature (e.g., Reports)
1. Create `ReportService` with business logic
2. Extend `AdminController`, inject `ReportService`
3. Create DTOs: `ReportDTO`, `ReportParamsDTO`
4. Add `@GetMapping("/reports")` method that calls service, populates model
5. Add Thymeleaf template under `templates/admin/reports/`
6. Update security in `SecurityConfig` if needed (already covers `/admin/**`)

### Adding a Worker Feature
1. Add method to `WorkerController` (or similar role-specific controller)
2. Inject relevant service (e.g., `TaskService`, `TimeEntryService`)
3. Service method processes DTO + performs domain logic + updates entity
4. Return Thymeleaf template under `templates/worker/`
5. Security auto-applied via `SecurityConfig` matcher for `/worker/**`

### Querying Multiple Work Orders with Related Data
```java
// Pattern: Use repository custom query or fetch eagerly in service
// Avoid N+1: In repository or service, explicitly fetch relationships
workOrderRepository.findAll() // May lazy-load Tasks on template access
// Better: Custom query with JOIN FETCH or service-level assembly
```

### Handling File Uploads
```java
// Controller pattern
@PostMapping("/upload")
public String uploadFile(@RequestParam("file") MultipartFile file, Model model) {
    String filePath = fileStorageService.saveFile(file);
    // Create Attachment entity, link to WorkOrder, save via repository
    return "redirect:/admin/orders/" + orderId;
}
```

---

## Gotchas & Notes

1. **CSRF disabled in SecurityConfig** → `csrf(AbstractHttpConfigurer::disable)` for simplicity (not production-safe; enable if needed)
2. **allow-circular-references: true** in `application.yaml` → permits circular Spring bean dependencies (clean this up long-term)
3. **Pagination:** `PaginationDTO` exists but not widely used; implement if scaling to large datasets
4. **Status enums:** `OrderStatus`, `TaskStatus`, `TaskPriority` are `@Enumerated(EnumType.STRING)` for readability
5. **Worker availability:** `User.available` flag indicates if worker can accept new tasks
6. **Public token tracking:** Work orders have `publicToken` UUID for unauthenticated `/public/order-tracking/{token}` access

---

## Quick Reference: Key Files

| Purpose | File Path |
|---------|-----------|
| Role config, auth rules | `config/SecurityConfig.java` |
| View mapping, file serving | `config/MvcConfig.java` |
| Password hashing | `config/PasswordConfig.java` |
| Admin dashboard & CRUD | `controller/AdminController.java` |
| Worker task management | `controller/WorkerController.java` |
| Public order tracking | `controller/PublicController.java` |
| Base view attributes | `controller/BaseController.java` |
| Work order business logic | `service/WorkOrderService.java` |
| Time tracking & reporting | `service/TaskService.java`, `DashboardService.java` |
| Bulk data import | `service/ExcelImportService.java` |
| File disk operations | `service/FileStorageService.java` |
| Spring i18n setup | `application.yaml` (messages.basename) |

---

**Last Updated:** 2026-01-28
