package com.printflow.repository;

import com.printflow.entity.ClientPortalAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientPortalAccessRepository extends JpaRepository<ClientPortalAccess, Long> {
    Optional<ClientPortalAccess> findByAccessToken(String accessToken);
    @Query("select a from ClientPortalAccess a " +
        "join fetch a.client c " +
        "join fetch a.company co " +
        "where a.accessToken = :accessToken")
    Optional<ClientPortalAccess> findByAccessTokenWithClientAndCompany(String accessToken);
    Optional<ClientPortalAccess> findByClient_IdAndCompany_Id(Long clientId, Long companyId);
}
