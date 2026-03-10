# Architecture

**Main Modules**
- `config`: Spring Boot configuration, security, MVC, and app bootstrapping. Key files: `SecurityConfig`, `WebConfig`, `MvcConfig`, `FileStorageConfig`, `PasswordConfig`, `DataInitializer`, `GlobalModelAttributes`.
- `controller`: HTTP endpoints and Thymeleaf page rendering. Core controllers: `AdminController`, `WorkerController`, `PublicController`, `ProductionPlannerController`, `CompanyController`, `AuditLogController`, `RateLimitAdminController`, `FileController`, `NotificationController`, `HomeController`.
- `service`: Business logic and orchestration. Examples: `WorkOrderService`, `TaskService`, `ClientService`, `DashboardService`, `ProductionPlannerService`, `RateLimitService`, `NotificationService`, `FileStorageService`, `TenantContextService`.
- `repository`: JPA repositories for data access.
- `entity`: Domain model and persistence mapping.
- `dto`: Request/response and view models.
- `resources/templates`: Thymeleaf views for admin, worker, public, and auth flows.
- `resources/static`: CSS and static assets.
- `util`: Helper utilities like file handling and order number generation.

**Security Roles**
- Roles are defined in `User.Role`: `SUPER_ADMIN`, `ADMIN`, `MANAGER`, `WORKER_DESIGN`, `WORKER_PRINT`, `WORKER_GENERAL`.
- Public access: `GET /`, `/public/**`, `/static/**`, `/css/**`, `/js/**`, `/images/**`, `/webjars/**`, `/favicon.ico`, `/error`, `/login`, `/register`.
- `SUPER_ADMIN` only: `/admin/companies/**`, `/admin/audit-logs/**`, `/admin/rate-limit/**`.
- Admin console: `/admin/**` requires `ADMIN`, `MANAGER`, or `SUPER_ADMIN`.
- Worker area: `/worker/**` requires `WORKER_DESIGN`, `WORKER_PRINT`, `WORKER_GENERAL`, `ADMIN`, or `SUPER_ADMIN`.
- File API: `/api/files/download/**` requires authentication; `/api/files/thumbnail/**` is public.
- Default login redirects by role: `SUPER_ADMIN` -> `/admin/companies`, `ADMIN` -> `/admin/dashboard`, workers -> `/worker/dashboard`.

**Request Flow**
1. HTTP request enters Spring MVC and passes through `SecurityFilterChain` (authn/authz, login, logout, access denied).
2. Controller handles route and prepares the model. Shared attributes come from `BaseController` and `GlobalModelAttributes`.
3. Controller calls service layer for business operations and validation.
4. Service layer uses repositories to load/persist entities and maps entities to DTOs where needed.
5. Response is rendered via Thymeleaf templates or returned as file download/stream in `FileController`.
6. Multi-tenant context is resolved via `TenantContextService`, using the authenticated user to scope data by `Company`.

**Database Structure**
- `companies`: tenant root entity.
- `users`: authentication and profile data. Links: `tenant_id` -> `companies.id`.
- `clients`: customer records. Links: `tenant_id` -> `companies.id`.
- `work_orders`: orders for clients. Links: `client_id` -> `clients.id`, `assigned_to` -> `users.id`, `created_by` -> `users.id`, `tenant_id` -> `companies.id`.
- `tasks`: production tasks. Links: `work_order_id` -> `work_orders.id`, `assigned_to_id` -> `users.id`, `created_by_id` -> `users.id`, `tenant_id` -> `companies.id`.
- `task_activities`: task audit trail. Links: `task_id` -> `tasks.id`, `user_id` -> `users.id`.
- `time_entries`: time tracking. Links: `task_id` -> `tasks.id`, `user_id` -> `users.id`.
- `attachments`: files for orders, tasks, or comments. Links: `work_order_id` -> `work_orders.id`, `task_id` -> `tasks.id`, `comment_id` -> `comments.id`, `uploaded_by` -> `users.id`, `tenant_id` -> `companies.id`.
- `comments`: task comments. Links: `task_id` -> `tasks.id`, `user_id` -> `users.id`.
- `notifications`: user notifications. Links: `user_id` -> `users.id`.
- `audit_logs`: admin/audit trail. Links: `user_id` -> `users.id`, `tenant_id` -> `companies.id`.
- `translations`: message keys and localized strings (`message_key` primary key).
- `banned_ips`: rate-limit bans.
- `whitelisted_ips`: rate-limit allowlist.
- `schema.sql`: adjusts the `users.role` column and normalizes legacy role names.

**Migrations**
- Flyway is disabled in default/dev to support MySQL 5.5 startup. Production enables Flyway and requires MySQL **>= 5.7**.
