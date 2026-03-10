package com.printflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mail_settings",
    uniqueConstraints = @UniqueConstraint(name = "uk_mail_settings_company", columnNames = "company_id"))
public class MailSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Column(name = "smtp_password_enc", length = 2048)
    private String smtpPasswordEnc;

    @Column(name = "smtp_use_tls")
    private Boolean smtpUseTls = true;

    @Column(name = "smtp_use_ssl")
    private Boolean smtpUseSsl = false;

    @Column(name = "from_email", length = 255)
    private String fromEmail;

    @Column(name = "from_name", length = 255)
    private String fromName;

    @Column(name = "enabled")
    private Boolean enabled = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPasswordEnc() {
        return smtpPasswordEnc;
    }

    public void setSmtpPasswordEnc(String smtpPasswordEnc) {
        this.smtpPasswordEnc = smtpPasswordEnc;
    }

    public Boolean getSmtpUseTls() {
        return smtpUseTls;
    }

    public void setSmtpUseTls(Boolean smtpUseTls) {
        this.smtpUseTls = smtpUseTls;
    }

    public Boolean getSmtpUseSsl() {
        return smtpUseSsl;
    }

    public void setSmtpUseSsl(Boolean smtpUseSsl) {
        this.smtpUseSsl = smtpUseSsl;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
