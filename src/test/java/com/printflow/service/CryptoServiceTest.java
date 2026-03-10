package com.printflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    @Test
    void usesFallbackKeyWhenProdProfileIsNotActive() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("default");

        CryptoService cryptoService = new CryptoService("", false, env);
        String enc = cryptoService.encrypt("secret");

        assertThat(enc).isNotBlank();
        assertThat(cryptoService.decrypt(enc)).isEqualTo("secret");
    }

    @Test
    void failsWithoutKeyWhenKeyIsRequired() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new CryptoService("", true, env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MAIL_CRYPT_KEY is required");
    }

    @Test
    void nonProdProfileUsesFallbackEvenWhenKeyIsMarkedRequired() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("default");

        CryptoService cryptoService = new CryptoService("", true, env);
        String enc = cryptoService.encrypt("secret");

        assertThat(enc).isNotBlank();
        assertThat(cryptoService.decrypt(enc)).isEqualTo("secret");
    }
}
