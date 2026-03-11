# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Runtime metrics for email and rate-limiter flows:
  - `printflow_email_send_retries_total`
  - `printflow_email_send_failures_total`
  - `printflow_rate_limit_denied_total`
  - `printflow_rate_limit_auto_ban_total`
- Unit tests for the new metrics counters in email and rate-limit services.
- `PublicMessagesConsistencyTest` to guard SR/EN key coverage for public error messages.
- `PublicMessagesConsistencyTest` now also validates placeholder-count parity between SR/EN public keys.
- `PublicMessagesConsistencyTest` now covers public track-form validation keys (`track.error.required`, `track.error.company_mismatch`, `track.error.invalid_code`).
- `MonitoringAssetsSmokeTest` to verify monitoring handoff files and key metric rules are present.
- GitHub Actions workflow `.github/workflows/quality-gates.yml` for focused regression smoke tests on push/PR (Linux + Windows matrix).

### Changed
- Email pipeline reliability improved:
  - async notifications are now emitted after transaction commit,
  - transient SMTP send failures are retried before marking outbox as failed.
  - optional retry backoff added via `app.notification.email.retry-backoff-ms`.
  - test profile uses synchronous email executor for deterministic integration runs.
- Structured logs expanded for:
  - public request lifecycle,
  - pricing calculation start/end,
  - billing checkout/webhook/subscription upsert,
  - rate-limit denials and auto-ban events.
- Maven/test encoding hardened to UTF-8 in `pom.xml` (Surefire + project encodings).
- `README.md` extended with observability/Actuator usage and metric query examples.
- Added baseline alert thresholds for email failure spikes and rate-limit auto-ban spikes.
- Added starter Prometheus alert rules at `ops/monitoring/prometheus-alerts.yml`.
- Added starter Grafana dashboard at `ops/monitoring/grafana-dashboard.json`.
- Added monitoring handoff guide at `ops/monitoring/README.md`.
- Maven build now enforces minimum Java/Maven versions via `maven-enforcer-plugin`.
- Maven enforcer now blocks duplicate dependency declarations in `pom.xml`.
- Public track form now preserves selected UI language on submit via hidden `lang` field (prevents locale fallback on validation errors).
- CI quality workflow now also runs `PublicErrorStatusIntegrationTest` and `PublicUploadReferenceIntegrationTest`.
- Public order error page now uses a dedicated heading for `public.error.*` cases (instead of always "Order not found").

## [1.2.3] - 2026-03-10

### Added
- Multi-tenant foundation with company context handling, tenant guardrails, and tenant-aware admin modules.
- Billing domain and Stripe integration (plans, subscriptions, access checks, billing UI).
- Public/portal flows:
  - public order request submission and conversion,
  - tracking token improvements,
  - client portal access,
  - invite and password reset flows.
- Email infrastructure:
  - outbox model,
  - template rendering,
  - notification-driven delivery,
  - tenant-aware sender resolution.
- Pricing and product modules:
  - pricing engine endpoints,
  - pricing admin screens,
  - product import/sync settings and API controllers.
- Production planner module and related DTO/service/controller stack.
- Static assets and expanded UI templates for admin/public/auth/error/pricing/products.
- DB migration set (`V1` through `V31`) and schema support files.

### Changed
- Core controllers, services, repositories, and templates refactored to support tenant isolation and new domains.
- Security and public endpoint behavior tightened (rate-limit, token handling, error paths).
- Build/run documentation improved (`README.md`, `mvn.cmd` usage notes).

### Testing
- Integration and service test coverage significantly expanded.
- Verified with full test suite:
  - `mvn.cmd test`
  - `Tests run: 116, Failures: 0, Errors: 0, Skipped: 0`

### Tags
- Release tag: `v1.2.3`
