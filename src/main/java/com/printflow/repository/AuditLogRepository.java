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
    
    @Query("SELECT al FROM AuditLog al WHERE al.createdAt >= :startDate AND al.createdAt <= :endDate")
    List<AuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT al FROM AuditLog al WHERE al.user.id = :userId AND al.createdAt >= :startDate")
    List<AuditLog> findByUserAndDateRange(@Param("userId") Long userId, 
                                         @Param("startDate") LocalDateTime startDate);
}