# Roadmap

## v1.2.4 (next)

1. Stabilization and hardening
- Resolve async email outbox FK race conditions seen in integration logs.
- Add retries/backoff and stricter transactional boundaries for outbox writes.

2. Observability and operations
- Add structured logging around public request, pricing, and billing flows.
- Add lightweight dashboards/alerts for rate-limit, email failures, and webhook processing.

3. UX and workflow polish
- Improve admin/public validation messages and i18n consistency (SR/EN).
- Finish edge-case handling in public upload/request forms and pricing bulk flows.

