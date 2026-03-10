package com.printflow.pricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSource;
import com.printflow.entity.enums.ProductSyncAuthType;
import com.printflow.pricing.dto.ProductImportRow;
import com.printflow.pricing.dto.ProductSyncProbeResult;
import com.printflow.pricing.dto.ProductSyncResult;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductSyncSettings;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
public class GenericProductExternalSyncFacade implements ProductExternalSyncFacade {

    private final ProductSyncSettingsService settingsService;
    private final ProductUpsertService upsertService;
    private final ObjectMapper objectMapper;

    public GenericProductExternalSyncFacade(ProductSyncSettingsService settingsService,
                                            ProductUpsertService upsertService,
                                            ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.upsertService = upsertService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProductSyncResult syncFromExternalProvider(Company company) {
        ProductSyncSettings settings = validatedSettings(company, true);

        String body = fetchRawJson(settings);
        JsonNode root = readJson(body);
        JsonNode items = resolveItems(root, settings.getPayloadRoot());
        if (items == null || !items.isArray()) {
            throw new IllegalStateException("API response must be a JSON array or contain an array in payload root.");
        }

        ProductSyncResult result = new ProductSyncResult();
        result.setTotalRows(items.size());
        int index = 0;
        for (JsonNode item : items) {
            index++;
            try {
                ProductImportRow row = toRow(item, index);
                if (row.getName() == null || row.getName().isBlank()) {
                    throw new IllegalArgumentException("name missing");
                }
                if (row.getBasePrice() == null) {
                    throw new IllegalArgumentException("invalid basePrice");
                }
                if (row.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("basePrice must be >= 0");
                }

                Optional<Product> byExternalId = upsertService.findByExternalId(company.getId(), row.getExternalId());
                Optional<Product> bySku = upsertService.findBySku(company.getId(), row.getSku());
                Optional<Product> existing = byExternalId.isPresent() ? byExternalId : bySku;

                if (existing.isPresent()) {
                    upsertService.updateFromImport(existing.get(), row, ProductSource.API);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                } else {
                    upsertService.upsertImportRow(company, row, ProductSource.API);
                    result.setImportedCount(result.getImportedCount() + 1);
                }
            } catch (Exception ex) {
                result.addError("Row " + index + ": " + ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public ProductSyncProbeResult testConnection(Company company) {
        ProductSyncSettings settings = validatedSettings(company, false);
        String body = fetchRawJson(settings);
        JsonNode root = readJson(body);
        JsonNode items = resolveItems(root, settings.getPayloadRoot());
        if (items == null || !items.isArray()) {
            throw new IllegalStateException("Connection OK, but JSON payload does not contain array data.");
        }
        return new ProductSyncProbeResult(true, items.size(), "Connection successful.");
    }

    private ProductSyncSettings validatedSettings(Company company, boolean requireEnabled) {
        if (company == null || company.getId() == null) {
            throw new IllegalArgumentException("Company is required.");
        }
        ProductSyncSettings settings = settingsService.getOrCreateCurrentTenantSettings();
        if (requireEnabled && !settings.isEnabled()) {
            throw new UnsupportedOperationException("Product API sync is disabled for this company.");
        }
        if (settings.getEndpointUrl() == null || settings.getEndpointUrl().isBlank()) {
            throw new UnsupportedOperationException("Product API endpoint is not configured.");
        }
        return settings;
    }

    private String fetchRawJson(ProductSyncSettings settings) {
        if (settings.getEndpointUrl() != null && settings.getEndpointUrl().toLowerCase(Locale.ROOT).startsWith("demo://")) {
            return """
                {
                  "items": [
                    {
                      "id": "demo-001",
                      "name": "Demo vizit karta",
                      "sku": "DEMO-CARD-001",
                      "description": "Standardna vizit karta 90x50mm",
                      "category": "Vizit karte",
                      "unit": "kom",
                      "price": 2500.00,
                      "currency": "RSD",
                      "active": true
                    },
                    {
                      "id": "demo-002",
                      "name": "Demo flajer A5",
                      "sku": "DEMO-FLYER-A5",
                      "description": "Flajer A5, 4/4 boje",
                      "category": "Flajeri",
                      "unit": "kom",
                      "price": 4200.00,
                      "currency": "RSD",
                      "active": true
                    }
                  ]
                }
                """;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(settings.getConnectTimeoutMs()))
                .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(settings.getEndpointUrl()))
                .timeout(Duration.ofMillis(settings.getReadTimeoutMs()))
                .header("Accept", "application/json")
                .GET();

            String token = settingsService.resolveToken(settings);
            if (settings.getAuthType() == ProductSyncAuthType.BEARER && token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            } else if (settings.getAuthType() == ProductSyncAuthType.API_KEY_HEADER && token != null && !token.isBlank()) {
                String headerName = settings.getAuthHeaderName() != null && !settings.getAuthHeaderName().isBlank()
                    ? settings.getAuthHeaderName()
                    : "X-API-Key";
                requestBuilder.header(headerName, token);
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("API returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to fetch product sync payload: " + ex.getMessage(), ex);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid JSON payload.");
        }
    }

    private JsonNode resolveItems(JsonNode root, String payloadRoot) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        String rootKey = payloadRoot == null ? null : payloadRoot.trim();
        if (rootKey != null && !rootKey.isBlank() && root.has(rootKey) && root.get(rootKey).isArray()) {
            return root.get(rootKey);
        }
        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }
        if (root.has("data") && root.get("data").isArray()) {
            return root.get("data");
        }
        return null;
    }

    private ProductImportRow toRow(JsonNode item, int rowNumber) {
        ProductImportRow row = new ProductImportRow();
        row.setRowNumber(rowNumber);
        row.setName(text(item, "name", "title"));
        row.setSku(text(item, "sku", "code"));
        row.setDescription(text(item, "description", "desc"));
        row.setCategory(text(item, "category", "categoryName"));
        row.setUnit(text(item, "unit", "unitType"));
        row.setBasePrice(number(item, "basePrice", "price", "unitPrice"));
        row.setCurrency(text(item, "currency"));
        row.setActive(bool(item, "active", "enabled", "isActive"));
        String externalId = text(item, "externalId", "external_id", "id");
        row.setExternalId(externalId);
        return row;
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String value = node.get(key).asText(null);
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isBlank()) {
                        return trimmed;
                    }
                }
            }
        }
        return null;
    }

    private BigDecimal number(JsonNode node, String... keys) {
        for (String key : keys) {
            if (!node.has(key) || node.get(key).isNull()) {
                continue;
            }
            JsonNode value = node.get(key);
            if (value.isNumber()) {
                return value.decimalValue();
            }
            String asText = value.asText(null);
            if (asText == null || asText.isBlank()) {
                continue;
            }
            try {
                return new BigDecimal(asText.trim().replace(" ", "").replace(",", "."));
            } catch (NumberFormatException ignored) {
                // try next key
            }
        }
        return null;
    }

    private Boolean bool(JsonNode node, String... keys) {
        for (String key : keys) {
            if (!node.has(key) || node.get(key).isNull()) {
                continue;
            }
            JsonNode value = node.get(key);
            if (value.isBoolean()) {
                return value.booleanValue();
            }
            if (value.isNumber()) {
                return value.asInt() != 0;
            }
            String raw = value.asText("");
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "da".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "ne".equals(normalized)) {
                return false;
            }
        }
        return null;
    }
}
