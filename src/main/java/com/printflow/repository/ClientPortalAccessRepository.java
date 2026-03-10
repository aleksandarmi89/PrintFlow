package com.printflow.repository;

import com.printflow.entity.ClientPortalAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientPortalAccessRepository extends JpaRepository<ClientPortalAccess, Long> {
    Optional<ClientPortalAccess> findByAccessToken(String accessToken);
    Optional<ClientPortalAccess> findByClient_IdAndCompany_Id(Long clientId, Long companyId);
}
