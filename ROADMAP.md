# Roadmap

## v1.2.4 (next)

1. Stabilization and hardening
- [x] Resolve async email outbox FK race conditions seen in integration logs.
- [x] Add retry behavior for transient SMTP failures before outbox failure state.
- [ ] Add optional backoff between retries (if needed after production telemetry).

2. Observability and operations
- [x] Add structured logging around public request, pricing, and billing flows.
- [x] Add structured logging for rate-limit denials and auto-ban activity.
- [x] Add lightweight counters for email failures/retries and rate-limit events.
- [ ] Wire dashboards/alerts for the new counters in the target monitoring stack.

3. UX and workflow polish
- [ ] Improve admin/public validation messages and i18n consistency (SR/EN).
- [ ] Finish edge-case handling in public upload/request forms and pricing bulk flows.

## Next Focus (recommended)
- Standardize SR/EN public error texts (rate limit, invalid token, upload validation).
- Add one smoke test for metrics endpoint presence on custom counters.
- Define alert thresholds (example): 5xx email failures/min and repeated auto-ban spikes.
