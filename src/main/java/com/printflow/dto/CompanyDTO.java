package com.printflow.dto;

import java.time.LocalDateTime;

public class CompanyDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String website;
    private String primaryColor;
    private String logoPath;
    private String currency;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUser;
    private String smtpPassword;
    private Boolean smtpTls;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private com.printflow.entity.enums.PlanTier plan;
    private boolean billingOverrideActive;
    private LocalDateTime billingOverrideUntil;
    private long usersCount;
    private long activeUsersCount;
    private long clientsCount;
    private long ordersCount;

    public CompanyDTO() {}

    public CompanyDTO(Long id, String name, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public CompanyDTO(Long id, String name, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt,
                      long usersCount, long activeUsersCount, long clientsCount, long ordersCount) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.usersCount = usersCount;
        this.activeUsersCount = activeUsersCount;
        this.clientsCount = clientsCount;
        this.ordersCount = ordersCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }

    public String getSmtpUser() { return smtpUser; }
    public void setSmtpUser(String smtpUser) { this.smtpUser = smtpUser; }

    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }

    public Boolean getSmtpTls() { return smtpTls; }
    public void setSmtpTls(Boolean smtpTls) { this.smtpTls = smtpTls; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public com.printflow.entity.enums.PlanTier getPlan() { return plan; }
    public void setPlan(com.printflow.entity.enums.PlanTier plan) { this.plan = plan; }

    public boolean isBillingOverrideActive() { return billingOverrideActive; }
    public void setBillingOverrideActive(boolean billingOverrideActive) { this.billingOverrideActive = billingOverrideActive; }

    public LocalDateTime getBillingOverrideUntil() { return billingOverrideUntil; }
    public void setBillingOverrideUntil(LocalDateTime billingOverrideUntil) { this.billingOverrideUntil = billingOverrideUntil; }

    public long getUsersCount() { return usersCount; }
    public void setUsersCount(long usersCount) { this.usersCount = usersCount; }

    public long getActiveUsersCount() { return activeUsersCount; }
    public void setActiveUsersCount(long activeUsersCount) { this.activeUsersCount = activeUsersCount; }

    public long getClientsCount() { return clientsCount; }
    public void setClientsCount(long clientsCount) { this.clientsCount = clientsCount; }

    public long getOrdersCount() { return ordersCount; }
    public void setOrdersCount(long ordersCount) { this.ordersCount = ordersCount; }
}
