package com.printflow.pricing.repository;

import com.printflow.pricing.entity.PricingComponent;
import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import com.printflow.entity.enums.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingComponentRepository extends JpaRepository<PricingComponent, Long> {
    List<PricingComponent> findAllByVariant_IdAndCompany_Id(Long variantId, Long companyId);
    void deleteAllByVariant_IdAndCompany_Id(Long variantId, Long companyId);
    java.util.Optional<PricingComponent> findByIdAndCompany_Id(Long id, Long companyId);
    java.util.Optional<PricingComponent> findByIdAndVariant_IdAndCompany_Id(Long id, Long variantId, Long companyId);
    boolean existsByVariant_IdAndCompany_Id(Long variantId, Long companyId);

    @EntityGraph(attributePaths = "variant")
    java.util.Optional<PricingComponent> findWithVariantByIdAndCompany_Id(Long id, Long companyId);

    @EntityGraph(attributePaths = "variant")
    java.util.Optional<PricingComponent> findWithVariantByIdAndVariant_IdAndCompany_Id(Long id, Long variantId, Long companyId);

    @Query("""
        select c from PricingComponent c
        where c.company.id = :companyId
          and c.variant.product.category = :category
          and (:type is null or c.type = :type)
          and (:model is null or c.model = :model)
        """)
    List<PricingComponent> findAllByCompanyAndCategory(@Param("companyId") Long companyId,
                                                       @Param("category") ProductCategory category,
                                                       @Param("type") PricingComponentType type,
                                                       @Param("model") PricingModel model);
}
