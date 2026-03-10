package com.printflow.repository;

import com.printflow.entity.Client;
import com.printflow.pricing.dto.ClientSelectRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByEmail(String email);
    Optional<Client> findByEmailAndCompany_Id(String email, Long companyId);
    Optional<Client> findByPhoneAndCompany_Id(String phone, Long companyId);
    boolean existsByEmailAndCompany_Id(String email, Long companyId);
    Optional<Client> findByCompanyId(String companyId);
    List<Client> findByActiveTrue();
    Page<Client> findByActiveTrue(Pageable pageable);
    List<Client> findByCompany_Id(Long companyId);
    List<Client> findByCompany_IdAndActiveTrue(Long companyId);
    Page<Client> findByCompany_IdAndActiveTrue(Long companyId, Pageable pageable);
    long countByCompany_Id(Long companyId);
    List<Client> findByCompanyNameContainingIgnoreCase(String companyName);
    Optional<Client> findByIdAndCompany_Id(Long id, Long companyId);

    @Query("SELECT c.id as id, c.companyName as companyName " +
           "FROM Client c " +
           "WHERE c.company.id = :companyId AND c.active = true " +
           "ORDER BY c.companyName")
    List<ClientSelectRow> findActiveSelectRowsByCompanyId(@Param("companyId") Long companyId);
    
    @Query("SELECT c FROM Client c WHERE c.active = true AND " +
           "(LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.contactPerson) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Client> searchActive(@Param("query") String query);

    @Query("SELECT c FROM Client c WHERE c.active = true AND " +
           "(LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.contactPerson) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Client> searchActive(@Param("query") String query, Pageable pageable);

    @Query("SELECT c FROM Client c WHERE c.company.id = :companyId AND c.active = true AND " +
           "(LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.contactPerson) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Client> searchActiveByCompany(@Param("companyId") Long companyId, @Param("query") String query);

    @Query("SELECT c FROM Client c WHERE c.company.id = :companyId AND c.active = true AND " +
           "(LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.contactPerson) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Client> searchActiveByCompany(@Param("companyId") Long companyId, @Param("query") String query, Pageable pageable);
    
    @Query("SELECT c FROM Client c WHERE c.active = true ORDER BY c.createdAt DESC")
    List<Client> findAllActiveOrderByCreatedAtDesc();
    
    @Query("SELECT COUNT(c) FROM Client c WHERE c.active = true")
    long countActiveClients();

    @Query("SELECT COUNT(c) FROM Client c WHERE c.company.id = :companyId AND c.active = true")
    long countActiveClientsByCompany(@Param("companyId") Long companyId);
    
    boolean existsByEmail(String email);
    boolean existsByCompanyId(String companyId);
}
