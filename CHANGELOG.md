# Changelog

All notable changes to this project will be documented in this file.

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

