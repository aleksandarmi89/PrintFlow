package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSource;
import com.printflow.pricing.dto.ProductImportMode;
import com.printflow.pricing.dto.ProductImportResult;
import com.printflow.pricing.dto.ProductImportRow;
import com.printflow.pricing.entity.Product;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Transactional
public class ProductImportService {

    private static final List<String> TEMPLATE_HEADERS = List.of(
        "name", "sku", "description", "category", "unit", "basePrice", "currency", "active", "externalId"
    );

    private final ProductUpsertService upsertService;

    public ProductImportService(ProductUpsertService upsertService) {
        this.upsertService = upsertService;
    }

    public ProductImportResult importFile(MultipartFile file, Company company, ProductImportMode mode) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        List<ProductImportRow> rows;
        try {
            if (filename.endsWith(".csv")) {
                rows = parseCsv(file.getInputStream());
            } else if (filename.endsWith(".xlsx")) {
                rows = parseXlsx(file.getInputStream());
            } else {
                throw new IllegalArgumentException("Unsupported file type. Use CSV or XLSX.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read import file.", e);
        }
        return importRows(rows, company, mode);
    }

    public ProductImportResult importRows(List<ProductImportRow> rows, Company company, ProductImportMode mode) {
        ProductImportResult result = new ProductImportResult();
        result.setTotalRows(rows.size());
        Set<String> fileSkus = new HashSet<>();

        for (ProductImportRow row : rows) {
            try {
                normalizeRow(row);
                validateRow(row, fileSkus, mode);
                processRow(company, row, mode, result);
            } catch (IllegalArgumentException ex) {
                result.addError(row.getRowNumber(), ex.getMessage());
            } catch (Exception ex) {
                result.addError(row.getRowNumber(), "Unexpected error: " + ex.getMessage());
            }
        }
        return result;
    }

    public String templateCsv() {
        return String.join(",", TEMPLATE_HEADERS) + "\n";
    }

    private void processRow(Company company, ProductImportRow row, ProductImportMode mode, ProductImportResult result) {
        Optional<Product> existingBySku = upsertService.findBySku(company.getId(), row.getSku());
        switch (mode) {
            case ADD_NEW_ONLY -> {
                if (existingBySku.isPresent()) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    return;
                }
                upsertService.upsertImportRow(company, row, ProductSource.IMPORT);
                result.setImportedCount(result.getImportedCount() + 1);
            }
            case SKIP_DUPLICATES -> {
                if (existingBySku.isPresent()) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    return;
                }
                upsertService.upsertImportRow(company, row, ProductSource.IMPORT);
                result.setImportedCount(result.getImportedCount() + 1);
            }
            case UPSERT_BY_SKU -> {
                if (row.getSku() == null || row.getSku().isBlank()) {
                    throw new IllegalArgumentException("SKU is required for UPSERT_BY_SKU.");
                }
                if (existingBySku.isPresent()) {
                    upsertService.updateFromImport(existingBySku.get(), row, ProductSource.IMPORT);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                } else {
                    upsertService.upsertImportRow(company, row, ProductSource.IMPORT);
                    result.setImportedCount(result.getImportedCount() + 1);
                }
            }
            case REPLACE_IF_MATCHED -> {
                if (row.getSku() == null || row.getSku().isBlank()) {
                    throw new IllegalArgumentException("SKU is required for REPLACE_IF_MATCHED.");
                }
                if (existingBySku.isPresent()) {
                    Product existing = existingBySku.get();
                    row.setDescription(row.getDescription() == null ? null : row.getDescription());
                    row.setCategory(row.getCategory() == null ? null : row.getCategory());
                    row.setUnit(row.getUnit() == null ? null : row.getUnit());
                    row.setCurrency(row.getCurrency() == null ? "RSD" : row.getCurrency());
                    upsertService.updateFromImport(existing, row, ProductSource.IMPORT);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                } else {
                    upsertService.upsertImportRow(company, row, ProductSource.IMPORT);
                    result.setImportedCount(result.getImportedCount() + 1);
                }
            }
        }
    }

    private void normalizeRow(ProductImportRow row) {
        row.setName(trim(row.getName()));
        row.setSku(trim(row.getSku()));
        row.setDescription(trim(row.getDescription()));
        row.setCategory(trim(row.getCategory()));
        row.setUnit(trim(row.getUnit()));
        row.setCurrency(trim(row.getCurrency()));
        row.setExternalId(trim(row.getExternalId()));
        if (row.getCurrency() == null || row.getCurrency().isBlank()) {
            row.setCurrency("RSD");
        }
        if (row.getActive() == null) {
            row.setActive(Boolean.TRUE);
        }
    }

    private void validateRow(ProductImportRow row, Set<String> fileSkus, ProductImportMode mode) {
        if (row.getName() == null || row.getName().isBlank()) {
            throw new IllegalArgumentException("name missing");
        }
        if (row.getBasePrice() == null) {
            throw new IllegalArgumentException("invalid basePrice");
        }
        if (row.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("basePrice must be >= 0");
        }
        if (row.getSku() != null && !row.getSku().isBlank()) {
            String skuKey = row.getSku().toLowerCase(Locale.ROOT);
            if (!fileSkus.add(skuKey)) {
                throw new IllegalArgumentException("duplicate sku in file");
            }
        }
        if ((mode == ProductImportMode.UPSERT_BY_SKU || mode == ProductImportMode.REPLACE_IF_MATCHED)
            && (row.getSku() == null || row.getSku().isBlank())) {
            throw new IllegalArgumentException("sku required for selected mode");
        }
    }

    private List<ProductImportRow> parseCsv(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            List<String> headers = parseCsvLine(headerLine);
            Map<String, Integer> index = headerIndex(headers);
            List<ProductImportRow> rows = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                rows.add(toRow(values, index, rowNumber));
            }
            return rows;
        }
    }

    private List<ProductImportRow> parseXlsx(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                return List.of();
            }
            List<String> headerValues = new ArrayList<>();
            for (Cell cell : header) {
                headerValues.add(cell.getStringCellValue());
            }
            Map<String, Integer> index = headerIndex(headerValues);
            List<ProductImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                int maxColumns = Math.max(index.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1, 9);
                for (int col = 0; col < maxColumns; col++) {
                    values.add(readCellAsString(row.getCell(col)));
                }
                rows.add(toRow(values, index, i + 1));
            }
            return rows;
        } catch (Exception ex) {
            throw new IOException("Invalid XLSX file.", ex);
        }
    }

    private String readCellAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield String.valueOf(cell.getDateCellValue().getTime());
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = headers.get(i) == null ? "" : headers.get(i).trim().toLowerCase(Locale.ROOT);
            map.put(normalized, i);
        }
        if (!map.containsKey("name") || !map.containsKey("baseprice")) {
            throw new IllegalArgumentException("Headers must include name and basePrice.");
        }
        return map;
    }

    private ProductImportRow toRow(List<String> values, Map<String, Integer> index, int rowNumber) {
        ProductImportRow row = new ProductImportRow();
        row.setRowNumber(rowNumber);
        row.setName(value(values, index, "name"));
        row.setSku(value(values, index, "sku"));
        row.setDescription(value(values, index, "description"));
        row.setCategory(value(values, index, "category"));
        row.setUnit(value(values, index, "unit"));
        row.setBasePrice(parseBigDecimal(value(values, index, "baseprice")));
        row.setCurrency(value(values, index, "currency"));
        row.setActive(parseBoolean(value(values, index, "active")));
        row.setExternalId(value(values, index, "externalid"));
        return row;
    }

    private String value(List<String> values, Map<String, Integer> index, String header) {
        Integer i = index.get(header);
        if (i == null || i < 0 || i >= values.size()) {
            return null;
        }
        return values.get(i);
    }

    private BigDecimal parseBigDecimal(String value) {
        String normalized = trim(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String replaced = normalized.replace(" ", "").replace(",", ".");
        try {
            return new BigDecimal(replaced);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        String normalized = trim(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String v = normalized.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "1", "yes", "da", "y" -> true;
            case "false", "0", "no", "ne", "n" -> false;
            default -> null;
        };
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
