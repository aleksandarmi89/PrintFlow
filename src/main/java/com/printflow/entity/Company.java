package com.printflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.printflow.entity.enums.PlanTier;
import com.printflow.util.SlugUtil;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    private PlanTier plan = PlanTier.FREE;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    @Column(name = "currency", length = 10)
    private String currency = "RSD";

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_user", length = 255)
    private String smtpUser;

    @Column(name = "smtp_password", length = 255)
    private String smtpPassword;

    @Column(name = "smtp_tls")
    private Boolean smtpTls = true;

    @Column(name = "plan_updated_at")
    private LocalDateTime planUpdatedAt;

    @Column(name = "trial_start")
    private LocalDateTime trialStart;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    @Column(name = "billing_override_active", nullable = false)
    private boolean billingOverrideActive = false;

    @Column(name = "billing_override_until")
    private LocalDateTime billingOverrideUntil;

    public Company() {}

    public Company(Long id, String name, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (slug == null || slug.isBlank()) {
            slug = SlugUtil.toSlug(name);
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public PlanTier getPlan() { return plan; }
    public void setPlan(PlanTier plan) { this.plan = plan; }

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

    public LocalDateTime getPlanUpdatedAt() { return planUpdatedAt; }
    public void setPlanUpdatedAt(LocalDateTime planUpdatedAt) { this.planUpdatedAt = planUpdatedAt; }

    public LocalDateTime getTrialStart() { return trialStart; }
    public void setTrialStart(LocalDateTime trialStart) { this.trialStart = trialStart; }

    public LocalDateTime getTrialEnd() { return trialEnd; }
    public void setTrialEnd(LocalDateTime trialEnd) { this.trialEnd = trialEnd; }

    public boolean isBillingOverrideActive() { return billingOverrideActive; }
    public void setBillingOverrideActive(boolean billingOverrideActive) { this.billingOverrideActive = billingOverrideActive; }

    public LocalDateTime getBillingOverrideUntil() { return billingOverrideUntil; }
    public void setBillingOverrideUntil(LocalDateTime billingOverrideUntil) { this.billingOverrideUntil = billingOverrideUntil; }
}
