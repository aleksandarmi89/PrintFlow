package com.printflow.repository;

import com.printflow.entity.BillingPlanConfig;
import com.printflow.entity.enums.PlanTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import com.printflow.entity.enums.BillingInterval;

@Repository
public interface BillingPlanConfigRepository extends JpaRepository<BillingPlanConfig, Long> {
    Optional<BillingPlanConfig> findByPlan(PlanTier plan);
    Optional<BillingPlanConfig> findByPlanAndInterval(PlanTier plan, BillingInterval interval);
    Optional<BillingPlanConfig> findByStripePriceId(String stripePriceId);
}
