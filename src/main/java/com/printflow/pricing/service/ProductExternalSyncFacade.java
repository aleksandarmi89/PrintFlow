package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.pricing.dto.ProductSyncProbeResult;
import com.printflow.pricing.dto.ProductSyncResult;

public interface ProductExternalSyncFacade {
    /**
     * TODO: implement when external provider sync is introduced (pull/webhook/scheduled).
     */
    ProductSyncResult syncFromExternalProvider(Company company);

    default ProductSyncProbeResult testConnection(Company company) {
        throw new UnsupportedOperationException("External product sync provider is not configured.");
    }
}
