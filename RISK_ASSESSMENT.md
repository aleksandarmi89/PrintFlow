# Risk Assessment

**Biggest Architectural Risks**
- Multi-tenancy is enforced in services but not globally at repository level; missing tenant filters can leak data across companies.
- Controllers are large and contain business logic decisions; it is easy to bypass service invariants or duplicate logic inconsistently.
- File handling and uploads are tightly coupled to local filesystem paths; this will not scale horizontally without shared storage.
- Public endpoints and token-based access (`/public/order/{token}`) rely on long-lived tokens without explicit expiration or rotation.
- Database access patterns are likely N+1 and lack explicit fetch strategies or pagination in some flows, risking performance collapse.

**What Would Break Under 10,000 Users**
- Synchronous file uploads/downloads and thumbnail generation on the web thread will saturate CPU/IO.
- Notification and dashboard queries can become hot spots without caching or optimized indexes.
- Audit logging and activity feeds will bloat tables and slow primary queries without partitioning or archiving.
- Global model attributes fetch notification counts on every request for authenticated users, adding load to all pages.
- Single-node session and local file storage will fail under horizontal scaling.

**What Is Not Production Ready**
- CSRF is disabled globally; this is not acceptable for a browser-based app with state-changing POSTs.
- Default seed users and fixed passwords are enabled by default; this is unsafe in production.
- Error handling is mostly controller-level redirects; no consistent error responses or structured logging strategy.
- Rate limit allow/ban lists exist, but no request filtering is shown in the web layer.
- No visible database migrations (only `schema.sql`); schema changes are not managed.

**Where Security Vulnerabilities Are Likely**
- CSRF disabled on a form-login app.
- Missing tenant scoping in repositories or queries (data leakage across companies).
- File download/view/print endpoints may allow IDOR if not validating ownership/tenant.
- Public order tracking token endpoints could be scraped if tokens are weakly protected or not rotated.
- User input passed into templates without strict validation or encoding rules could lead to stored XSS.

**What Should Be Refactored Before Scaling**
- Enforce tenant scoping in repositories or via Hibernate filters and central query guards.
- Move cross-cutting security (IP bans, rate limiting) into filters/interceptors.
- Split monolithic controllers into smaller, resource-focused controllers with service-level invariants.
- Introduce background jobs for file processing, email, and notifications.
- Add pagination and indexing strategy for orders, tasks, notifications, and audit logs.
