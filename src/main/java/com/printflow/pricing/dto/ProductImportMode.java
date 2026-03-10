package com.printflow.pricing.dto;

public enum ProductImportMode {
    ADD_NEW_ONLY,
    UPSERT_BY_SKU,
    REPLACE_IF_MATCHED,
    SKIP_DUPLICATES
}
