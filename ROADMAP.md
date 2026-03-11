# Roadmap

## v1.2.4 (next)

1. Stabilization and hardening
- [x] Resolve async email outbox FK race conditions seen in integration logs.
- [x] Add retry behavior for transient SMTP failures before outbox failure state.
- [x] Add optional backoff between retries (`app.notification.email.retry-backoff-ms`).

2. Observability and operations
- [x] Add structured logging around public request, pricing, and billing flows.
- [x] Add structured logging for rate-limit denials and auto-ban activity.
- [x] Add lightweight counters for email failures/retries and rate-limit events.
- [x] Add smoke test for custom counter presence on `/management/metrics`.
- [x] Define baseline alert thresholds for email failures and auto-ban spikes.
- [ ] Wire dashboards/alerts for the new counters in the target monitoring stack.
: Added starter Prometheus alert rules in `ops/monitoring/prometheus-alerts.yml` for handoff.

3. UX and workflow polish
- [ ] Improve admin/public validation messages and i18n consistency (SR/EN).
- [ ] Finish edge-case handling in public upload/request forms and pricing bulk flows.
: Added regression test `PublicMessagesConsistencyTest` to enforce SR/EN key presence for public error flows.

## Next Focus (recommended)
- Standardize SR/EN public error texts (rate limit, invalid token, upload validation).
- Wire dashboard and alert rules in your monitoring stack (Prometheus/Grafana or equivalent).
