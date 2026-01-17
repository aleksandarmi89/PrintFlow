package com.printflow.repository;

import com.printflow.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByEmail(String email);
    Optional<Client> findByCompanyId(String companyId);
    List<Client> findByActiveTrue();
    List<Client> findByCompanyNameContainingIgnoreCase(String companyName);
    
    @Query("SELECT c FROM Client c WHERE c.active = true AND " +
           "(LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.contactPerson) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Client> searchActive(@Param("query") String query);
    
    @Query("SELECT c FROM Client c WHERE c.active = true ORDER BY c.createdAt DESC")
    List<Client> findAllActiveOrderByCreatedAtDesc();
    
    @Query("SELECT COUNT(c) FROM Client c WHERE c.active = true")
    long countActiveClients();
    
    boolean existsByEmail(String email);
    boolean existsByCompanyId(String companyId);
}