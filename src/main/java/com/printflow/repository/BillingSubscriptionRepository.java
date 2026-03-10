package com.printflow.repository;

import com.printflow.entity.BillingSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingSubscriptionRepository extends JpaRepository<BillingSubscription, Long> {
    Optional<BillingSubscription> findByCompany_Id(Long companyId);
    Optional<BillingSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
