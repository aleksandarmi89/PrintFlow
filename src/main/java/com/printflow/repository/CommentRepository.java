package com.printflow.repository;

import com.printflow.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("SELECT c FROM Comment c WHERE c.id = :id AND c.task.company.id = :companyId")
    Optional<Comment> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
    Optional<Comment> findByIdAndTask_IdAndTask_Company_Id(Long id, Long taskId, Long companyId);
    
    // Osnovne metode
    List<Comment> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    Page<Comment> findByTaskIdOrderByCreatedAtDesc(Long taskId, Pageable pageable);
    
    boolean existsByIdAndUserId(Long commentId, Long userId);
    
    // User komentari
    List<Comment> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<Comment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    List<Comment> findByTaskIdAndUserIdOrderByCreatedAtDesc(Long taskId, Long userId);
    
    // Datum based
    List<Comment> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
    List<Comment> findByTaskIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long taskId, LocalDateTime start, LocalDateTime end);
    List<Comment> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime start, LocalDateTime end);
    
    // Search
    @Query("SELECT c FROM Comment c WHERE c.task.id = :taskId AND " +
           "(LOWER(c.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY c.createdAt DESC")
    List<Comment> searchByTaskIdAndContent(@Param("taskId") Long taskId, @Param("searchTerm") String searchTerm);
    
    @Query("SELECT c FROM Comment c WHERE c.user.id = :userId AND " +
           "(LOWER(c.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY c.createdAt DESC")
    List<Comment> searchByUserIdAndContent(@Param("userId") Long userId, @Param("searchTerm") String searchTerm);
    
    // Statistika
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.task.id = :taskId")
    long countByTaskId(@Param("taskId") Long taskId);
    
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    @Query("SELECT c.task.id, COUNT(c) FROM Comment c WHERE c.user.id = :userId GROUP BY c.task.id")
    List<Object[]> countCommentsPerTaskByUserId(@Param("userId") Long userId);
    
    // Najnovjih N komentara
    default List<Comment> findTopNByTaskId(Long taskId, int limit) {
        int safeLimit = Math.max(limit, 1);
        return findByTaskIdOrderByCreatedAtDesc(taskId, org.springframework.data.domain.PageRequest.of(0, safeLimit)).getContent();
    }

    default List<Comment> findTopNByUserId(Long userId, int limit) {
        int safeLimit = Math.max(limit, 1);
        return findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, safeLimit)).getContent();
    }
    
    // Bulk operacije
    @Modifying
    @Transactional
    @Query("DELETE FROM Comment c WHERE c.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM Comment c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM Comment c WHERE c.createdAt < :date")
    void deleteOlderThan(@Param("date") LocalDateTime date);
    
    // Komentari za više taskova
    @Query("SELECT c FROM Comment c WHERE c.task.id IN :taskIds ORDER BY c.createdAt DESC")
    List<Comment> findByTaskIdsOrderByCreatedAtDesc(@Param("taskIds") List<Long> taskIds);
    
    // Zadnji komentar za task
    @Query("SELECT c FROM Comment c WHERE c.task.id = :taskId ORDER BY c.createdAt DESC")
    Comment findLatestByTaskId(@Param("taskId") Long taskId);
    
    // Zadnji komentar od korisnika za task
    @Query("SELECT c FROM Comment c WHERE c.task.id = :taskId AND c.user.id = :userId ORDER BY c.createdAt DESC")
    Comment findLatestByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);
}
