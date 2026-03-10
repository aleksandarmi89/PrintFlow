package com.printflow.pricing.repository;

import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.dto.VariantSelectRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Query("SELECT v.id as id, p.id as productId, p.name as productName, CONCAT(p.name, ' / ', v.name) as name " +
           "FROM ProductVariant v JOIN v.product p " +
           "WHERE v.company.id = :companyId " +
           "ORDER BY p.name, v.name")
    List<VariantSelectRow> findSelectRowsByCompanyId(@Param("companyId") Long companyId);
    List<ProductVariant> findAllByCompany_Id(Long companyId);
    Optional<ProductVariant> findByIdAndCompany_Id(Long id, Long companyId);
    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findWithProductByIdAndCompany_Id(Long id, Long companyId);
    @EntityGraph(attributePaths = "product")
    List<ProductVariant> findAllByProduct_IdAndCompany_Id(Long productId, Long companyId);
    @EntityGraph(attributePaths = "product")
    @Query("select v from ProductVariant v where v.product.id = :productId and v.company.id = :companyId")
    List<ProductVariant> findAllByProductIdAndCompanyIdFetchProduct(@Param("productId") Long productId,
                                                                    @Param("companyId") Long companyId);
    List<ProductVariant> findAllByCompany_IdAndProduct_Category(Long companyId, com.printflow.entity.enums.ProductCategory category);
    void deleteAllByProduct_IdAndCompany_Id(Long productId, Long companyId);
}
