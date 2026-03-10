package com.printflow.pricing.repository;

import com.printflow.pricing.entity.ProductSyncSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSyncSettingsRepository extends JpaRepository<ProductSyncSettings, Long> {
    Optional<ProductSyncSettings> findByCompany_Id(Long companyId);
}
