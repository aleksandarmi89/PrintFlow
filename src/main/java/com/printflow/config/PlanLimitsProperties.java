package com.printflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.plans")
public class PlanLimitsProperties {
    private PlanLimits free = new PlanLimits();
    private PlanLimits pro = new PlanLimits();
    private PlanLimits team = new PlanLimits();

    public PlanLimits getFree() { return free; }
    public void setFree(PlanLimits free) { this.free = free; }

    public PlanLimits getPro() { return pro; }
    public void setPro(PlanLimits pro) { this.pro = pro; }

    public PlanLimits getTeam() { return team; }
    public void setTeam(PlanLimits team) { this.team = team; }

    public static class PlanLimits {
        private int maxUsers;
        private int maxMonthlyOrders;
        private long maxStorageBytes;

        public int getMaxUsers() { return maxUsers; }
        public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }

        public int getMaxMonthlyOrders() { return maxMonthlyOrders; }
        public void setMaxMonthlyOrders(int maxMonthlyOrders) { this.maxMonthlyOrders = maxMonthlyOrders; }

        public long getMaxStorageBytes() { return maxStorageBytes; }
        public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
    }
}
