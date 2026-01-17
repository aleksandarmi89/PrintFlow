package com.printflow.repository;

import com.printflow.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // Osnovne metode
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);
    int countByUserIdAndReadFalse(Long userId);
    List<Notification> findByUserIdAndReadFalse(Long userId);
    
    // Metode za filtriranje po tipu
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);
    
    // Metode za vremenski period
    List<Notification> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime date);
    List<Notification> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime start, LocalDateTime end);
    
    // Metode za starje notifikacije
    List<Notification> findByCreatedAtBeforeAndReadTrue(LocalDateTime date);
    List<Notification> findByCreatedAtBefore(LocalDateTime date);
    
    // Custom query za brojanje u periodu
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.createdAt >= :date")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("date") LocalDateTime date);
    
    // Custom query za najnovjih N notifikacija - OVO JE ISPRAVLJENO IME METODE
    @Query(value = "SELECT * FROM notifications WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<Notification> findTopNByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("limit") int limit);
    
    // Metode za bulk operacije
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.read = false AND n.createdAt < :date")
    List<Notification> findOldUnreadNotifications(@Param("userId") Long userId, @Param("date") LocalDateTime date);
    
    // Statistika
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.type = :type")
    long countByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);
    
    @Query("SELECT n.type, COUNT(n) FROM Notification n WHERE n.user.id = :userId GROUP BY n.type")
    List<Object[]> countByTypeGroupedByUserId(@Param("userId") Long userId);
    
    // Metode za više korisnika
    @Query("SELECT n FROM Notification n WHERE n.user.id IN :userIds ORDER BY n.createdAt DESC")
    List<Notification> findByUserIdsOrderByCreatedAtDesc(@Param("userIds") List<Long> userIds);
    
    // Custom metoda za pagination sa pretragom
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND " +
           "(:type IS NULL OR n.type = :type) AND " +
           "(:read IS NULL OR n.read = :read) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("read") Boolean read,
            Pageable pageable);
    
    // Ukupan broj notifikacija po korisniku
    long countByUserId(Long userId);
    
    // Dodatne helper metode
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.createdAt >= :startDate AND n.createdAt <= :endDate")
    List<Notification> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    // Broj notifikacija po tipu za korisnika
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.type = :type AND n.read = :read")
    long countByUserIdAndTypeAndRead(@Param("userId") Long userId, 
                                     @Param("type") String type, 
                                     @Param("read") boolean read);
}