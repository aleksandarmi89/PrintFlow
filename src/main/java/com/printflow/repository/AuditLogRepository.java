package com.printflow.repository;

import com.printflow.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserId(Long userId);
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
    List<AuditLog> findByCompany_IdOrderByCreatedAtDesc(Long companyId);
    List<AuditLog> findByCompany_IdAndActionOrderByCreatedAtDesc(Long companyId, com.printflow.entity.enums.AuditAction action);
    List<AuditLog> findByActionOrderByCreatedAtDesc(com.printflow.entity.enums.AuditAction action);

    @Query("SELECT al FROM AuditLog al LEFT JOIN FETCH al.user LEFT JOIN FETCH al.company WHERE al.entityType = :entityType AND al.entityId = :entityId ORDER BY al.createdAt DESC")
    List<AuditLog> findByEntityTypeAndEntityIdWithUser(@Param("entityType") String entityType,
                                                       @Param("entityId") Long entityId);

    @Query("SELECT al FROM AuditLog al LEFT JOIN FETCH al.user LEFT JOIN FETCH al.company WHERE al.company.id = :companyId ORDER BY al.createdAt DESC")
    List<AuditLog> findByCompanyIdWithUser(@Param("companyId") Long companyId);

    @Query("SELECT al FROM AuditLog al LEFT JOIN FETCH al.user LEFT JOIN FETCH al.company WHERE al.company.id = :companyId AND al.action = :action ORDER BY al.createdAt DESC")
    List<AuditLog> findByCompanyIdAndActionWithUser(@Param("companyId") Long companyId,
                                                    @Param("action") com.printflow.entity.enums.AuditAction action);

    @Query("""
        SELECT al FROM AuditLog al
        LEFT JOIN FETCH al.user
        LEFT JOIN FETCH al.company
        WHERE (:companyId IS NULL OR al.company.id = :companyId)
          AND (:action IS NULL OR al.action = :action)
          AND (:userId IS NULL OR al.user.id = :userId)
          AND (:entityId IS NULL OR al.entityId = :entityId)
          AND (:entityType IS NULL OR :entityType = '' OR LOWER(al.entityType) = LOWER(:entityType))
          AND (
            :query IS NULL OR :query = '' OR
            LOWER(al.description) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(al.entityType) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(al.user.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(al.user.username) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY al.createdAt DESC
        """)
    org.springframework.data.domain.Page<AuditLog> searchAuditLogs(@Param("companyId") Long companyId,
                                                                    @Param("action") com.printflow.entity.enums.AuditAction action,
                                                                    @Param("query") String query,
                                                                    @Param("userId") Long userId,
                                                                    @Param("entityId") Long entityId,
                                                                    @Param("entityType") String entityType,
                                                                    org.springframework.data.domain.Pageable pageable);

    @Query("SELECT al FROM AuditLog al LEFT JOIN FETCH al.user LEFT JOIN FETCH al.company WHERE al.action = :action ORDER BY al.createdAt DESC")
    List<AuditLog> findByActionWithUser(@Param("action") com.printflow.entity.enums.AuditAction action);

    @Query("SELECT al FROM AuditLog al LEFT JOIN FETCH al.user LEFT JOIN FETCH al.company ORDER BY al.createdAt DESC")
    List<AuditLog> findAllWithUser();

    List<AuditLog> findByEntityTypeAndCreatedAtBetween(String entityType, java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<AuditLog> findByEntityTypeAndCompany_IdAndCreatedAtBetween(String entityType, Long companyId, java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<AuditLog> findByEntityTypeAndActionAndCreatedAtBetween(String entityType, com.printflow.entity.enums.AuditAction action,
                                                                java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<AuditLog> findByEntityTypeAndActionAndCompany_IdAndCreatedAtBetween(String entityType, com.printflow.entity.enums.AuditAction action,
                                                                             Long companyId, java.time.LocalDateTime start, java.time.LocalDateTime end);
    
    @Query("SELECT al FROM AuditLog al WHERE al.createdAt >= :startDate AND al.createdAt <= :endDate")
    List<AuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT al FROM AuditLog al WHERE al.user.id = :userId AND al.createdAt >= :startDate")
    List<AuditLog> findByUserAndDateRange(@Param("userId") Long userId, 
                                         @Param("startDate") LocalDateTime startDate);
}
