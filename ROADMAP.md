# Roadmap

## v1.2.4 (next)

1. Stabilization and hardening
- [x] Resolve async email outbox FK race conditions seen in integration logs.
- [x] Add retry behavior for transient SMTP failures before outbox failure state.
- [x] Add optional backoff between retries (`app.notification.email.retry-backoff-ms`).
: Maven build now enforces Java/Maven minimum versions (`maven-enforcer-plugin`).
: Maven enforcer now guards against duplicate dependency declarations in `pom.xml`.

2. Observability and operations
- [x] Add structured logging around public request, pricing, and billing flows.
- [x] Add structured logging for rate-limit denials and auto-ban activity.
- [x] Add lightweight counters for email failures/retries and rate-limit events.
- [x] Add smoke test for custom counter presence on `/management/metrics`.
- [x] Define baseline alert thresholds for email failures and auto-ban spikes.
- [ ] Wire dashboards/alerts for the new counters in the target monitoring stack.
: Added starter Prometheus alert rules in `ops/monitoring/prometheus-alerts.yml` for handoff.
: Added starter Grafana dashboard in `ops/monitoring/grafana-dashboard.json` for handoff.
: Added deployment checklist in `ops/monitoring/README.md`.
: Added `MonitoringAssetsSmokeTest` to guard handoff file presence and key metric references.
: Added CI workflow `quality-gates.yml` to run focused regression smoke tests on push/PR (Linux + Windows matrix).
: Expanded `quality-gates.yml` test set with `PublicErrorStatusIntegrationTest` and `PublicUploadReferenceIntegrationTest`.
: Expanded `quality-gates.yml` test set with `PublicOrderTokenIntegrationTest` for public token/locale flow coverage.
: Expanded `quality-gates.yml` test set with `PublicTrackPageIntegrationTest` to cover public track page render regressions.
: Added track-page regression checks for EN rendering and hidden `lang` fallback to `sr` on unsupported locale input.
: Added upload redirect regression for trimmed locale normalization (`\"  EN  \"` => `en`).
: Added track redirect regression for trimmed locale normalization (`\"  EN  \"` => `en`).
: Public order canonical redirect now keeps supported locale (`sr`/`en`) when token is normalized.
: Public upload success redirect now keeps supported locale (`sr`/`en`) after successful file upload.

3. UX and workflow polish
- [ ] Improve admin/public validation messages and i18n consistency (SR/EN).
- [ ] Finish edge-case handling in public upload/request forms and pricing bulk flows.
: Added regression test `PublicMessagesConsistencyTest` to enforce SR/EN key presence for public error flows.
: Extended i18n regression test to enforce placeholder-count consistency across SR/EN public keys.
: Extended i18n regression test coverage with public track-form validation keys (`track.error.*`).
: Track form POST now carries `lang` so validation errors stay in the selected locale.
: Public order error page now switches heading by error context (`order_not_found.*` vs `public.error.*`).
: Public upload-reference POST now carries `lang` so upload validation errors keep selected locale after redirect.
: Public design feedback response is now fully localized via message keys and dedicated template.
: Public design approval POST form now carries `lang` and has regression coverage in `PublicOrderTokenIntegrationTest`.
: Added locale-level consistency checks so shared public errors (`too_many_requests`, `access_denied`) stay aligned across key variants.
: i18n regression keyset now also explicitly includes `public.error.heading`.
: `public/order-not-found` now preserves locale on action links (`/public/track`, `/public/`) via `lang` parameter.
: Public upload redirect language param is now whitelisted (`sr`, `en`) to avoid propagating unsupported values.
: Added regression test for uppercase locale normalization in upload redirect flow (`EN` => `en`).
: Public track submit now preserves locale on successful redirect to `/public/order/{token}` (for supported locales).
: Track submit locale handling now has explicit tests for unsupported locale ignore and uppercase normalization.
: Hidden locale fields in public forms now clamp to supported values (`en` / `sr` fallback), with regression coverage for unsupported locale fallback.

## Next Focus (recommended)
- Standardize SR/EN public error texts (rate limit, invalid token, upload validation).
- Wire dashboard and alert rules in your monitoring stack (Prometheus/Grafana or equivalent).
