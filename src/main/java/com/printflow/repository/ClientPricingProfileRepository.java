package com.printflow.repository;

import com.printflow.entity.ClientPricingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientPricingProfileRepository extends JpaRepository<ClientPricingProfile, Long> {
    Optional<ClientPricingProfile> findByClient_IdAndVariant_IdAndCompany_Id(Long clientId, Long variantId, Long companyId);
    List<ClientPricingProfile> findAllByClient_IdAndCompany_Id(Long clientId, Long companyId);
}
