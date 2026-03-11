package com.printflow.repository;

import com.printflow.entity.TimeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    @Query("SELECT t FROM TimeEntry t WHERE t.id = :id AND t.task.company.id = :companyId")
    Optional<TimeEntry> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
    
    // ==================== BASIC QUERIES ====================
    
    List<TimeEntry> findByUserId(Long userId);
    Page<TimeEntry> findByUserId(Long userId, Pageable pageable);
    
    List<TimeEntry> findByTaskId(Long taskId);
    Page<TimeEntry> findByTaskId(Long taskId, Pageable pageable);
    Page<TimeEntry> findByUserIdOrderByDateDesc(Long userId, Pageable pageable);
    Page<TimeEntry> findByTaskIdOrderByDateDesc(Long taskId, Pageable pageable);
    
    List<TimeEntry> findByUserIdAndTaskId(Long userId, Long taskId);

    @Query("SELECT te FROM TimeEntry te JOIN te.task t JOIN t.workOrder wo " +
           "WHERE wo.id = :workOrderId AND wo.company.id = :companyId")
    List<TimeEntry> findByWorkOrderIdAndCompanyId(@Param("workOrderId") Long workOrderId,
                                                  @Param("companyId") Long companyId);
    
    // ==================== DATE-BASED QUERIES ====================
    
    List<TimeEntry> findByUserIdAndDateBetween(Long userId, LocalDateTime start, LocalDateTime end);
    Page<TimeEntry> findByUserIdAndDateBetween(Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    List<TimeEntry> findByTaskIdAndDateBetween(Long taskId, LocalDateTime start, LocalDateTime end);
    List<TimeEntry> findByUserIdAndTaskIdAndDateBetween(Long userId, Long taskId, LocalDateTime start, LocalDateTime end);
    
    // Po konkretnom datumu
    @Query("SELECT te FROM TimeEntry te WHERE te.user.id = :userId AND DATE(te.date) = :date")
    List<TimeEntry> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Modifying
    @Transactional
    void deleteByTaskId(Long taskId);
    
    @Query("SELECT te FROM TimeEntry te WHERE te.task.id = :taskId AND DATE(te.date) = :date")
    List<TimeEntry> findByTaskIdAndDate(@Param("taskId") Long taskId, @Param("date") LocalDate date);
    
    // ==================== STATISTICS & AGGREGATION ====================
    
    @Query("SELECT SUM(te.hours * 60 + te.minutes) FROM TimeEntry te WHERE te.user.id = :userId")
    Long sumTotalMinutesByUserId(@Param("userId") Long userId);
    
    @Query("SELECT SUM(te.hours * 60 + te.minutes) FROM TimeEntry te WHERE te.task.id = :taskId")
    Long sumTotalMinutesByTaskId(@Param("taskId") Long taskId);
    
    @Query("SELECT SUM(te.hours * 60 + te.minutes) FROM TimeEntry te WHERE te.user.id = :userId AND te.date BETWEEN :start AND :end")
    Long sumMinutesByUserIdAndDateBetween(@Param("userId") Long userId, 
                                          @Param("start") LocalDateTime start, 
                                          @Param("end") LocalDateTime end);
    
    @Query("SELECT SUM(te.hours * 60 + te.minutes) FROM TimeEntry te WHERE te.task.id = :taskId AND te.date BETWEEN :start AND :end")
    Long sumMinutesByTaskIdAndDateBetween(@Param("taskId") Long taskId, 
                                          @Param("start") LocalDateTime start, 
                                          @Param("end") LocalDateTime end);
    
    // Dnevni totali
    @Query("SELECT DATE(te.date), SUM(te.hours * 60 + te.minutes) FROM TimeEntry te " +
           "WHERE te.user.id = :userId AND te.date BETWEEN :start AND :end " +
           "GROUP BY DATE(te.date) ORDER BY DATE(te.date)")
    List<Object[]> getDailyTotalsByUserId(@Param("userId") Long userId, 
                                          @Param("start") LocalDateTime start, 
                                          @Param("end") LocalDateTime end);
    
    // Mjesečni totali
    @Query("SELECT YEAR(te.date), MONTH(te.date), SUM(te.hours * 60 + te.minutes) FROM TimeEntry te " +
           "WHERE te.user.id = :userId AND te.date BETWEEN :start AND :end " +
           "GROUP BY YEAR(te.date), MONTH(te.date) ORDER BY YEAR(te.date), MONTH(te.date)")
    List<Object[]> getMonthlyTotalsByUserId(@Param("userId") Long userId, 
                                            @Param("start") LocalDateTime start, 
                                            @Param("end") LocalDateTime end);
    
    // ==================== SEARCH METHODS ====================
    
    @Query("SELECT te FROM TimeEntry te WHERE te.user.id = :userId AND " +
           "(LOWER(te.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<TimeEntry> searchByDescription(@Param("userId") Long userId, @Param("searchTerm") String searchTerm);
    
    @Query("SELECT te FROM TimeEntry te WHERE te.task.id = :taskId AND " +
           "(LOWER(te.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<TimeEntry> searchByTaskIdAndDescription(@Param("taskId") Long taskId, @Param("searchTerm") String searchTerm);
    
    // ==================== RECENT ENTRIES ====================
    
    default List<TimeEntry> findRecentByUserId(Long userId, int limit) {
        int safeLimit = Math.max(limit, 1);
        return findByUserIdOrderByDateDesc(userId, org.springframework.data.domain.PageRequest.of(0, safeLimit)).getContent();
    }

    default List<TimeEntry> findRecentByTaskId(Long taskId, int limit) {
        int safeLimit = Math.max(limit, 1);
        return findByTaskIdOrderByDateDesc(taskId, org.springframework.data.domain.PageRequest.of(0, safeLimit)).getContent();
    }
    
    // ==================== BULK OPERATIONS ====================
    
    @Query("SELECT te FROM TimeEntry te WHERE te.date < :date")
    List<TimeEntry> findOlderThan(@Param("date") LocalDateTime date);
    
    // ==================== TASK STATISTICS ====================
    
    @Query("SELECT te.task.id, SUM(te.hours * 60 + te.minutes) FROM TimeEntry te " +
           "WHERE te.user.id = :userId AND te.date BETWEEN :start AND :end " +
           "GROUP BY te.task.id ORDER BY SUM(te.hours * 60 + te.minutes) DESC")
    List<Object[]> getTaskTimeSummary(@Param("userId") Long userId, 
                                      @Param("start") LocalDateTime start, 
                                      @Param("end") LocalDateTime end);
    
    // ==================== VALIDATION METHODS ====================
    
    @Query("SELECT COUNT(te) > 0 FROM TimeEntry te WHERE te.user.id = :userId AND te.task.id = :taskId " +
           "AND te.date BETWEEN :startOfDay AND :endOfDay")
    boolean existsEntryForUserAndTaskOnDate(@Param("userId") Long userId, 
                                            @Param("taskId") Long taskId,
                                            @Param("startOfDay") LocalDateTime startOfDay,
                                            @Param("endOfDay") LocalDateTime endOfDay);
}
