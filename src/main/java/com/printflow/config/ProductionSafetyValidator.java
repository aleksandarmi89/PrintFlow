package com.printflow.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductionSafetyValidator implements ApplicationRunner {

    private final Environment environment;

    public ProductionSafetyValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateOrThrow();
    }

    void validateOrThrow() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        List<String> violations = new ArrayList<>();
        String ddlAuto = prop("spring.jpa.hibernate.ddl-auto", "none");
        if (isRiskyDdlMode(ddlAuto)) {
            violations.add("spring.jpa.hibernate.ddl-auto must not be " + ddlAuto + " in prod");
        }

        String sqlInitMode = prop("spring.sql.init.mode", "never");
        if (!"never".equalsIgnoreCase(sqlInitMode)) {
            violations.add("spring.sql.init.mode must be 'never' in prod");
        }

        if (Boolean.parseBoolean(prop("app.seed.enabled", "false"))) {
            violations.add("app.seed.enabled must be false in prod");
        }

        if (Boolean.parseBoolean(prop("app.seed.sample.enabled", "false"))) {
            violations.add("app.seed.sample.enabled must be false in prod");
        }

        if (!Boolean.parseBoolean(prop("app.mail.require-crypt-key", "false"))) {
            violations.add("app.mail.require-crypt-key must be true in prod");
        }

        String cryptKey = prop("MAIL_CRYPT_KEY", "");
        if (cryptKey.isBlank()) {
            violations.add("MAIL_CRYPT_KEY must be set in prod");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException("Production safety validation failed: " + String.join("; ", violations));
        }
    }

    private String prop(String key, String defaultValue) {
        String value = environment.getProperty(key);
        return value == null ? defaultValue : value.trim();
    }

    private boolean isRiskyDdlMode(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "create".equals(normalized) || "create-drop".equals(normalized) || "update".equals(normalized);
    }
}
