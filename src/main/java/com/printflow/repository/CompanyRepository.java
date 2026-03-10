package com.printflow.repository;

import com.printflow.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByName(String name);
    Optional<Company> findBySlug(String slug);
    Optional<Company> findBySlugAndActiveTrue(String slug);
    boolean existsByName(String name);
    boolean existsBySlug(String slug);
    List<Company> findByActiveTrue();
    List<Company> findByNameContainingIgnoreCase(String name);
    Page<Company> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Company> findByPlan(com.printflow.entity.enums.PlanTier plan, Pageable pageable);
    Page<Company> findByBillingOverrideActive(boolean billingOverrideActive, Pageable pageable);
    Page<Company> findByPlanAndBillingOverrideActive(com.printflow.entity.enums.PlanTier plan, boolean billingOverrideActive, Pageable pageable);
    Page<Company> findByNameContainingIgnoreCaseAndPlan(String name, com.printflow.entity.enums.PlanTier plan, Pageable pageable);
    Page<Company> findByNameContainingIgnoreCaseAndBillingOverrideActive(String name, boolean billingOverrideActive, Pageable pageable);
    Page<Company> findByNameContainingIgnoreCaseAndPlanAndBillingOverrideActive(String name, com.printflow.entity.enums.PlanTier plan, boolean billingOverrideActive, Pageable pageable);

    @Query("select c.trialEnd as trialEnd, c.billingOverrideActive as billingOverrideActive, c.billingOverrideUntil as billingOverrideUntil from Company c where c.id = :id")
    Optional<CompanyBillingView> findBillingViewById(@Param("id") Long id);
}
