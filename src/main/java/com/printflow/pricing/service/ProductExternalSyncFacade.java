package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.pricing.dto.ProductSyncProbeResult;
import com.printflow.pricing.dto.ProductSyncResult;

public interface ProductExternalSyncFacade {
    /**
     * Implementations may support pull/webhook/scheduled provider synchronization.
     */
    ProductSyncResult syncFromExternalProvider(Company company);

    default ProductSyncProbeResult testConnection(Company company) {
        throw new UnsupportedOperationException("External product sync provider is not configured.");
    }
}
