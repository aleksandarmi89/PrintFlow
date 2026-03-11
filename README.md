# PrintFlow
## Run locally
Windows (recommended in this repo):
`mvn.cmd clean spring-boot:run`

Cross-platform:
`mvn spring-boot:run`

App runs on http://localhost:8088

## Profiles and Seeding
By default, seeding is disabled. To run in dev with default users:
`mvn.cmd spring-boot:run -Dspring-boot.run.profiles=dev`

To enable sample data (dev only), set these in `src/main/resources/application-dev.yaml`:
`app.seed.sample.enabled=true`
`app.seed.sample.force=true`

Production should run with the `prod` profile and seeding disabled:
`mvn.cmd spring-boot:run -Dspring-boot.run.profiles=prod`

## Database Migrations (Flyway)
Flyway is disabled by default/dev to allow startup on MySQL 5.5. It is enabled in `prod` and requires MySQL **>= 5.7**.

## Zero-Date Safety (MySQL)
Legacy rows with `0000-00-00 00:00:00` in DATETIME columns will crash Connector/J reads.
We fix this via Flyway and keep a JDBC safety-net:
- Migration: `V13__products_fix_zero_dates.sql` replaces zero dates and sets sane defaults.
- JDBC URL includes `zeroDateTimeBehavior=CONVERT_TO_NULL` as a backup.

Manual verification:
```
SHOW FULL COLUMNS FROM products;
SHOW FULL COLUMNS FROM work_order_items;

SELECT id, created_at, updated_at
FROM products
WHERE created_at = '0000-00-00 00:00:00'
   OR updated_at = '0000-00-00 00:00:00';

SELECT id, created_at, price_calculated_at
FROM work_order_items
WHERE created_at = '0000-00-00 00:00:00'
   OR price_calculated_at = '0000-00-00 00:00:00';
```

## Caching
Notification unread count cache (Caffeine):
`app.cache.notification-count-ttl-seconds` (default 20s)
`app.cache.notification-count-max-size` (default 10000)

Invalidation: counts are evicted on notification create, mark-read, and bulk read/delete operations.

## Stripe Billing (Skeleton)
Required env vars (prod):
`STRIPE_MODE` = `live` (default in prod profile)
`STRIPE_LIVE_API_KEY`
`STRIPE_LIVE_WEBHOOK_SECRET`
`STRIPE_SUCCESS_URL` (optional, default https://example.com/billing/success)
`STRIPE_CANCEL_URL` (optional, default https://example.com/billing/cancel)

Test mode:
`STRIPE_MODE` = `test`
`STRIPE_TEST_API_KEY`
`STRIPE_TEST_WEBHOOK_SECRET`

Dev testing modes:
A) Dev mode (Flyway off, Stripe test ok)
1. Set `STRIPE_TEST_API_KEY` and `STRIPE_TEST_WEBHOOK_SECRET`.
2. Run normally (no profile) or `-Dspring-boot.run.profiles=dev`.
3. Use Stripe CLI to forward webhooks to `http://localhost:8088/webhooks/stripe`.

B) Prod-like mode (Flyway on, requires MySQL >= 5.7)
1. Set `STRIPE_MODE=live`, `STRIPE_LIVE_API_KEY`, `STRIPE_LIVE_WEBHOOK_SECRET`, and prod URLs if needed.
2. Run `-Dspring-boot.run.profiles=prod`.
3. Use Stripe CLI to forward webhooks to `http://localhost:8088/webhooks/stripe`.

## SMTP (Per Company)
Emails are sent using SMTP configured on the company profile.
Fallback to global mail sender can be enabled with:
`APP_EMAIL_FALLBACK=true` (default: false in prod, true in dev)

Email retry tuning:
`app.notification.email.send-attempts` (default `2`)
`app.notification.email.retry-backoff-ms` (default `0`)

## Plans and Limits
Plan limits (defaults, configurable in `app.plans.*`):

| Plan | Max Users | Max Monthly Orders | Max Storage |
| --- | --- | --- | --- |
| FREE | 3 | 50 | 512MB |
| PRO | 20 | 500 | 10GB |
| TEAM | 100 | 2000 | 100GB |

## Trial and Billing Enforcement
Rules:
- Trial is active from `trial_start` to `trial_end`.
- If trial is expired and subscription status is not active (`active`, `trialing`, `past_due`), premium actions are blocked.
- Basic view/read access remains available.

Enforced in:
- `UserService.createUser` (max users + billing active)
- `WorkOrderService.createWorkOrder` (monthly order limit + billing active)
- `FileStorageService` uploads (storage limit + billing active)

Admins see a banner in the header when billing is inactive.

## Security
Session cookies (prod profile):
`server.servlet.session.cookie.http-only=true`
`server.servlet.session.cookie.secure=true`
`server.servlet.session.cookie.same-site=Lax`
Session timeout (prod profile): `server.servlet.session.timeout=30m`

Additional security config:
`app.security.cors.enabled` (default false)
`app.security.cors.allowed-origins` (comma-separated list)
`app.security.hsts.enabled` (true in prod profile)

Public order tracking tokens:
`app.public-order.token.ttl-days` (default 30)
`app.public-order.token.bytes` (default 32; URL-safe token length)
Admins can rotate a tracking link from the order details page to invalidate the old token.

## Observability
Actuator base path:
`/management`

Useful endpoints:
- `GET /management/health`
- `GET /management/info`
- `GET /management/metrics`

Custom counters added:
- `printflow_email_send_retries_total`
- `printflow_email_send_failures_total`
- `printflow_rate_limit_denied_total`
- `printflow_rate_limit_auto_ban_total`

Quick checks:
`curl http://localhost:8088/management/metrics/printflow_email_send_retries_total`
`curl http://localhost:8088/management/metrics/printflow_rate_limit_denied_total`

Alert baseline (recommended):
- Email failure spike (warning): `increase(printflow_email_send_failures_total[5m]) > 5`
- Email failure spike (critical): `increase(printflow_email_send_failures_total[5m]) > 20`
- Rate-limit abuse spike (warning): `increase(printflow_rate_limit_auto_ban_total[10m]) > 3`
- Rate-limit abuse spike (critical): `increase(printflow_rate_limit_auto_ban_total[10m]) > 10`

Operational note:
- Track `printflow_email_send_retries_total` alongside failures. Rising retries with low failures usually indicates upstream SMTP instability before user-visible impact.

Prometheus starter rule file:
- `ops/monitoring/prometheus-alerts.yml`
