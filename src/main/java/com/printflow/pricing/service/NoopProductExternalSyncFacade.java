package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.pricing.dto.ProductSyncProbeResult;
import com.printflow.pricing.dto.ProductSyncResult;

public class NoopProductExternalSyncFacade implements ProductExternalSyncFacade {
    @Override
    public ProductSyncResult syncFromExternalProvider(Company company) {
        // Explicitly fail fast so UI can show a clear message until provider integration is added.
        throw new UnsupportedOperationException("External product sync provider is not configured.");
    }

    @Override
    public ProductSyncProbeResult testConnection(Company company) {
        throw new UnsupportedOperationException("External product sync provider is not configured.");
    }
}
