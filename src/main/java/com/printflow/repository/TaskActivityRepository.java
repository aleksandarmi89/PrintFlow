package com.printflow.repository;

import com.printflow.entity.TaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.id = :id AND ta.task.company.id = :companyId")
    Optional<TaskActivity> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
    
    // Osnovne metode
    List<TaskActivity> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    Page<TaskActivity> findByTaskIdOrderByCreatedAtDesc(Long taskId, Pageable pageable);
    
    List<TaskActivity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<TaskActivity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // Kombinovane metode
    List<TaskActivity> findByTaskIdAndUserIdOrderByCreatedAtDesc(Long taskId, Long userId);
    List<TaskActivity> findByTaskIdAndActionOrderByCreatedAtDesc(Long taskId, String action);
    
    // Custom query za nedavne aktivnosti
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.user.id = :userId ORDER BY ta.createdAt DESC")
    List<TaskActivity> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);
    
    // Aktivnosti po vremenskom periodu
    List<TaskActivity> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
    List<TaskActivity> findByTaskIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long taskId, LocalDateTime start, LocalDateTime end);
    List<TaskActivity> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime start, LocalDateTime end);
    
    // Aktivnosti po akciji
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.action = :action ORDER BY ta.createdAt DESC")
    List<TaskActivity> findByActionOrderByCreatedAtDesc(@Param("action") String action);
    
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.task.id = :taskId AND ta.action = :action ORDER BY ta.createdAt DESC")
    List<TaskActivity> findByTaskIdAndAction(@Param("taskId") Long taskId, @Param("action") String action);
    
    // Statistika
    @Query("SELECT COUNT(ta) FROM TaskActivity ta WHERE ta.task.id = :taskId")
    long countByTaskId(@Param("taskId") Long taskId);
    
    @Query("SELECT COUNT(ta) FROM TaskActivity ta WHERE ta.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    @Query("SELECT ta.action, COUNT(ta) FROM TaskActivity ta WHERE ta.user.id = :userId GROUP BY ta.action")
    List<Object[]> countActionsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT ta.action, COUNT(ta) FROM TaskActivity ta WHERE ta.task.id = :taskId GROUP BY ta.action")
    List<Object[]> countActionsByTaskId(@Param("taskId") Long taskId);

    @Modifying
    @Transactional
    void deleteByTaskId(Long taskId);
    
    // Najnovjih N aktivnosti
    default List<TaskActivity> findTopNByUserId(Long userId, int limit) {
        int safeLimit = Math.max(limit, 1);
        return findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, safeLimit)).getContent();
    }

    default List<TaskActivity> findTopNByTaskId(Long taskId, int limit) {
        int safeLimit = Math.max(limit, 1);
        return findByTaskIdOrderByCreatedAtDesc(taskId, org.springframework.data.domain.PageRequest.of(0, safeLimit)).getContent();
    }
    
    // Aktivnosti za više taskova
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.task.id IN :taskIds ORDER BY ta.createdAt DESC")
    List<TaskActivity> findByTaskIdsOrderByCreatedAtDesc(@Param("taskIds") List<Long> taskIds);
    
    // Cleanup metode
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.createdAt < :date")
    List<TaskActivity> findOlderThan(@Param("date") LocalDateTime date);
}
