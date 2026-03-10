package com.printflow.dto;

public class MailSettingsDTO {
    private Boolean enabled;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private Boolean smtpUseTls;
    private Boolean smtpUseSsl;
    private String fromEmail;
    private String fromName;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
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
}
