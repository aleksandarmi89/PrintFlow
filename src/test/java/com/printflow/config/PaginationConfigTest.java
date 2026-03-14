package com.printflow.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginationConfigTest {

    @Test
    void normalizeSizeHandlesNullAllowedSizes() {
        PaginationConfig config = new PaginationConfig();
        config.setDefaultSize(20);
        config.setMaxSize(50);
        config.setAllowedSizes(null);

        assertEquals(20, config.normalizeSize(null));
        assertEquals(30, config.normalizeSize(30));
        assertEquals(50, config.normalizeSize(99));
    }

    @Test
    void normalizeSizeUsesWhitelistWhenConfigured() {
        PaginationConfig config = new PaginationConfig();
        config.setDefaultSize(20);
        config.setMaxSize(100);
        config.setAllowedSizes(List.of(10, 20, 50));

        assertEquals(20, config.normalizeSize(30));
        assertEquals(50, config.normalizeSize(50));
    }
}
