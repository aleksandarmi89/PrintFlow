package com.printflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.pagination")
public class PaginationConfig {

    private int defaultSize = 20;
    private int maxSize = 50;
    private List<Integer> allowedSizes = new ArrayList<>(List.of(10, 20, 50));

    public int normalizeSize(Integer requested) {
        int size = requested == null ? defaultSize : requested;
        if (size <= 0) {
            size = defaultSize;
        }
        if (!allowedSizes.isEmpty() && !allowedSizes.contains(size)) {
            size = defaultSize;
        }
        if (size > maxSize) {
            size = maxSize;
        }
        return size;
    }

    public int normalizePage(Integer requested) {
        int page = requested == null ? 0 : requested;
        return Math.max(0, page);
    }

    public int getDefaultSize() {
        return defaultSize;
    }

    public void setDefaultSize(int defaultSize) {
        this.defaultSize = defaultSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public List<Integer> getAllowedSizes() {
        return allowedSizes;
    }

    public void setAllowedSizes(List<Integer> allowedSizes) {
        this.allowedSizes = allowedSizes;
    }
}
