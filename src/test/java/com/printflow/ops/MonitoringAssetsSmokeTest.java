package com.printflow.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringAssetsSmokeTest {

    @Test
    void monitoringHandoffFilesExistAndContainKeyRules() throws IOException {
        Path alertsPath = Path.of("ops", "monitoring", "prometheus-alerts.yml");
        Path dashboardPath = Path.of("ops", "monitoring", "grafana-dashboard.json");
        Path readmePath = Path.of("ops", "monitoring", "README.md");

        assertTrue(Files.exists(alertsPath), "Missing alerts file: " + alertsPath);
        assertTrue(Files.exists(dashboardPath), "Missing dashboard file: " + dashboardPath);
        assertTrue(Files.exists(readmePath), "Missing monitoring README: " + readmePath);

        String alerts = Files.readString(alertsPath);
        assertTrue(alerts.contains("PrintFlowEmailFailuresWarning"));
        assertTrue(alerts.contains("PrintFlowRateLimitAutoBanCritical"));

        String dashboard = Files.readString(dashboardPath);
        assertTrue(dashboard.contains("printflow_email_send_failures_total"));
        assertTrue(dashboard.contains("printflow_rate_limit_auto_ban_total"));
    }
}
