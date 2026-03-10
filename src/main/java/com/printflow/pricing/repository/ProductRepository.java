package com.printflow.pricing.repository;

import com.printflow.pricing.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findAllByCompany_Id(Long companyId);
    Optional<Product> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<Product> findByCompany_IdAndSkuIgnoreCase(Long companyId, String sku);
    Optional<Product> findByCompany_IdAndExternalIdIgnoreCase(Long companyId, String externalId);
    boolean existsByCompany_IdAndSkuIgnoreCase(Long companyId, String sku);
    boolean existsByCompany_IdAndSkuIgnoreCaseAndIdNot(Long companyId, String sku, Long id);
}
