package com.printflow.repository;

import com.printflow.entity.BillingCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingCustomerRepository extends JpaRepository<BillingCustomer, Long> {
    Optional<BillingCustomer> findByCompany_Id(Long companyId);
    Optional<BillingCustomer> findByStripeCustomerId(String stripeCustomerId);
}
