package com.printflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyValidatorTest {

    @Test
    void doesNothingOutsideProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "update");

        ProductionSafetyValidator validator = new ProductionSafetyValidator(env);
        assertThatCode(validator::validateOrThrow).doesNotThrowAnyException();
    }

    @Test
    void failsInProdWhenUnsafeSettingsArePresent() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        env.setProperty("spring.sql.init.mode", "always");
        env.setProperty("app.seed.enabled", "true");
        env.setProperty("app.mail.require-crypt-key", "false");

        ProductionSafetyValidator validator = new ProductionSafetyValidator(env);

        assertThatThrownBy(validator::validateOrThrow)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production safety validation failed");
    }

    @Test
    void passesInProdWithSafeSettings() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        env.setProperty("spring.sql.init.mode", "never");
        env.setProperty("app.seed.enabled", "false");
        env.setProperty("app.seed.sample.enabled", "false");
        env.setProperty("app.mail.require-crypt-key", "true");
        env.setProperty("MAIL_CRYPT_KEY", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY=");

        ProductionSafetyValidator validator = new ProductionSafetyValidator(env);
        assertThatCode(validator::validateOrThrow).doesNotThrowAnyException();
    }
}
