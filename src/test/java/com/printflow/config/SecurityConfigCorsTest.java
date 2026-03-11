package com.printflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    private static final UserDetailsService NOOP_USER_DETAILS = username -> {
        throw new UnsupportedOperationException("not used in this test");
    };

    @Test
    void blankOriginsDoNotRegisterCorsConfiguration() {
        SecurityConfig securityConfig = new SecurityConfig(
            NOOP_USER_DETAILS,
            true,
            List.of("", "   "),
            false
        );

        UrlBasedCorsConfigurationSource source =
            (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();

        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/test"));
        assertThat(cors).isNull();
    }

    @Test
    void trimsAndKeepsOnlyValidOrigins() {
        SecurityConfig securityConfig = new SecurityConfig(
            NOOP_USER_DETAILS,
            true,
            List.of(" https://a.example.com ", "", "https://b.example.com"),
            false
        );

        UrlBasedCorsConfigurationSource source =
            (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();

        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/test"));
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://a.example.com", "https://b.example.com");
    }
}

