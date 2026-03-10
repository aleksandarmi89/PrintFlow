package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSource;
import com.printflow.pricing.dto.ProductImportMode;
import com.printflow.pricing.dto.ProductImportResult;
import com.printflow.pricing.dto.ProductImportRow;
import com.printflow.pricing.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImportServiceTest {

    @Mock
    private ProductUpsertService upsertService;

    private ProductImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ProductImportService(upsertService);
    }

    @Test
    void addNewOnlyImportsRowsAndSkipsExistingSku() {
        Company company = new Company();
        company.setId(1L);
        ProductImportRow row1 = row(2, "Banner", "SKU-1", "10.00");
        ProductImportRow row2 = row(3, "Poster", "SKU-2", "20.00");

        when(upsertService.findBySku(1L, "SKU-1")).thenReturn(Optional.empty());
        when(upsertService.findBySku(1L, "SKU-2")).thenReturn(Optional.of(new Product()));

        ProductImportResult result = importService.importRows(List.of(row1, row2), company, ProductImportMode.ADD_NEW_ONLY);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(upsertService, times(1)).upsertImportRow(eq(company), any(ProductImportRow.class), eq(ProductSource.IMPORT));
    }

    @Test
    void upsertBySkuUpdatesExistingAndCreatesMissing() {
        Company company = new Company();
        company.setId(1L);
        Product existing = new Product();
        ProductImportRow row1 = row(2, "Banner", "SKU-1", "10.00");
        ProductImportRow row2 = row(3, "Poster", "SKU-2", "20.00");

        when(upsertService.findBySku(1L, "SKU-1")).thenReturn(Optional.of(existing));
        when(upsertService.findBySku(1L, "SKU-2")).thenReturn(Optional.empty());

        ProductImportResult result = importService.importRows(List.of(row1, row2), company, ProductImportMode.UPSERT_BY_SKU);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(upsertService).updateFromImport(existing, row1, ProductSource.IMPORT);
        verify(upsertService).upsertImportRow(company, row2, ProductSource.IMPORT);
    }

    @Test
    void invalidRowsAreReported() {
        Company company = new Company();
        company.setId(1L);
        ProductImportRow missingName = row(2, "", "SKU-1", "10.00");
        ProductImportRow invalidPrice = row(3, "X", "SKU-2", null);
        ProductImportRow duplicateSku = row(4, "Y", "SKU-2", "2.00");
        ProductImportRow valid = row(5, "Z", "SKU-3", "5.00");

        when(upsertService.findBySku(1L, "SKU-3")).thenReturn(Optional.empty());

        ProductImportResult result = importService.importRows(
            List.of(missingName, invalidPrice, duplicateSku, valid), company, ProductImportMode.ADD_NEW_ONLY);

        assertThat(result.getFailedCount()).isEqualTo(3);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(3);
    }

    @Test
    void skipDuplicatesModeSkipsExistingSku() {
        Company company = new Company();
        company.setId(1L);
        ProductImportRow row = row(2, "Banner", "SKU-1", "10.00");
        when(upsertService.findBySku(1L, "SKU-1")).thenReturn(Optional.of(new Product()));

        ProductImportResult result = importService.importRows(List.of(row), company, ProductImportMode.SKIP_DUPLICATES);

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getImportedCount()).isZero();
        verify(upsertService, never()).upsertImportRow(any(), any(), any());
    }

    @Test
    void replaceIfMatchedUpdatesWhenSkuExists() {
        Company company = new Company();
        company.setId(1L);
        Product existing = new Product();
        ProductImportRow row = row(2, "Banner", "SKU-1", "10.00");
        when(upsertService.findBySku(1L, "SKU-1")).thenReturn(Optional.of(existing));

        ProductImportResult result = importService.importRows(List.of(row), company, ProductImportMode.REPLACE_IF_MATCHED);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        verify(upsertService).updateFromImport(existing, row, ProductSource.IMPORT);
    }

    private ProductImportRow row(int rowNo, String name, String sku, String price) {
        ProductImportRow row = new ProductImportRow();
        row.setRowNumber(rowNo);
        row.setName(name);
        row.setSku(sku);
        row.setBasePrice(price == null ? null : new BigDecimal(price));
        row.setCurrency("RSD");
        row.setActive(true);
        return row;
    }
}
