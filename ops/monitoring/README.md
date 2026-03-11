# Monitoring Handoff (Prometheus + Grafana)

This folder contains starter observability assets for PrintFlow:
- `prometheus-alerts.yml`
- `grafana-dashboard.json`

## 1) Prometheus alerts
1. Copy `prometheus-alerts.yml` into your Prometheus rules directory.
2. Include it in `prometheus.yml` under `rule_files`.
3. Reload Prometheus config.

Example snippet:
```yaml
rule_files:
  - /etc/prometheus/rules/prometheus-alerts.yml
```

## 2) Grafana dashboard
1. Open Grafana: `Dashboards -> New -> Import`.
2. Upload `grafana-dashboard.json`.
3. Select your Prometheus datasource.
4. Save dashboard in your target folder (for example: `PrintFlow`).

## 3) Recommended checks after import
- Trigger one known email send failure in a non-prod environment.
- Confirm `printflow_email_send_failures_total` moves.
- Confirm the panel "Email Send Failures (5m increase)" shows non-zero data.
- Simulate repeated public requests from the same IP and confirm:
  - `printflow_rate_limit_denied_total` increases
  - `printflow_rate_limit_auto_ban_total` increases (if auto-ban is enabled)

## 4) Alert tuning guidance
- Keep warning thresholds for early signal.
- Raise/lower critical thresholds based on real traffic after 7-14 days.
- If retries trend up while failures stay low, investigate SMTP latency/instability.
