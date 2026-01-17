package com.printflow.repository;

import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.User;
import com.printflow.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    Optional<WorkOrder> findByOrderNumber(String orderNumber);
    Optional<WorkOrder> findByPublicToken(String publicToken);
    
    // Methods with User entity
    List<WorkOrder> findByAssignedTo(User user);
    List<WorkOrder> findByAssignedToAndStatus(User user, OrderStatus status);
    List<WorkOrder> findByClient(Client client);
    
    // Search
    @Query("SELECT wo FROM WorkOrder wo WHERE " +
           "LOWER(wo.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(wo.orderNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(wo.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<WorkOrder> search(@Param("query") String query);
    
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.assignedTo.id = :userId AND wo.status = :status")
    List<WorkOrder> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") OrderStatus status);
    
    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.status = :status")
    long countByStatus(@Param("status") OrderStatus status);
    
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.priority >= :minPriority ORDER BY wo.priority DESC, wo.deadline ASC")
    List<WorkOrder> findHighPriorityOrders(@Param("minPriority") Integer minPriority);
    
    // Count methods
    int countByAssignedToId(Long id);
    int countByAssignedToIdAndStatus(Long id, OrderStatus completed);
    
    // Existence check for work order assignment
    boolean existsByIdAndAssignedToId(Long workOrderId, Long userId);
    
    // Paging methods
    Page<WorkOrder> findByOrderByCreatedAtDesc(Pageable pageable);
    
    // Assigned to user
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.assignedTo.id = :userId")
    Page<WorkOrder> findPageByAssignedToId(@Param("userId") Long userId, Pageable pageable);
    
    Page<WorkOrder> findByAssignedToId(Long userId, Pageable pageable);
    List<WorkOrder> findByAssignedToId(Long userId);
    
    // Unassigned
    Page<WorkOrder> findByAssignedToIsNull(Pageable pageable);
    List<WorkOrder> findByAssignedToIsNull();
    
    // Status-based
    Page<WorkOrder> findByStatus(OrderStatus status, Pageable pageable);
    List<WorkOrder> findByStatus(OrderStatus status);
    
    Page<WorkOrder> findByStatusIn(List<OrderStatus> statuses, Pageable pageable);
    List<WorkOrder> findByStatusIn(List<OrderStatus> statuses);
    
    // Assigned user + status
    Page<WorkOrder> findByAssignedToIdAndStatus(Long userId, OrderStatus status, Pageable pageable);
    List<WorkOrder> findByAssignedToIdAndStatus(Long userId, OrderStatus status);
    
    // Client-based
    Page<WorkOrder> findByClientId(Long clientId, Pageable pageable);
    List<WorkOrder> findByClientId(Long clientId);
    
    // Search with paging
    @Query("SELECT wo FROM WorkOrder wo WHERE " +
           "(LOWER(wo.orderNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(wo.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(wo.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<WorkOrder> searchAll(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.assignedTo.id = :userId AND " +
           "(LOWER(wo.orderNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(wo.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(wo.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<WorkOrder> searchByUser(@Param("userId") Long userId, @Param("searchTerm") String searchTerm, Pageable pageable);
    
    // Overdue orders - using deadline
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.assignedTo.id = :userId AND wo.deadline < :now AND wo.status NOT IN :excludedStatuses")
    List<WorkOrder> findOverdueOrdersByUser(@Param("userId") Long userId, 
                                           @Param("now") LocalDateTime now, 
                                           @Param("excludedStatuses") List<OrderStatus> excludedStatuses);
    
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.deadline < :now AND wo.status NOT IN :excludedStatuses")
    List<WorkOrder> findOverdueOrders(@Param("now") LocalDateTime now, 
                                     @Param("excludedStatuses") List<OrderStatus> excludedStatuses);
    
    // Date range
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.createdAt BETWEEN :start AND :end")
    List<WorkOrder> findByDateRange(@Param("start") LocalDateTime start, 
                                    @Param("end") LocalDateTime end);
    
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.assignedTo.id = :userId AND wo.createdAt BETWEEN :start AND :end")
    List<WorkOrder> findByUserAndDateRange(@Param("userId") Long userId, 
                                           @Param("start") LocalDateTime start, 
                                           @Param("end") LocalDateTime end);
    
    // REMOVED: Task assignment check - this belongs in TaskRepository
    // @Query("SELECT COUNT(wo) > 0 FROM WorkOrder wo JOIN wo.tasks t WHERE t.id = :taskId AND wo.assignedTo.id = :userId")
    // boolean existsTaskAssignedToUser(@Param("taskId") Long taskId, @Param("userId") Long userId);
    
    // Statistics
    @Query("SELECT wo.status, COUNT(wo) FROM WorkOrder wo WHERE wo.assignedTo.id = :userId GROUP BY wo.status")
    List<Object[]> countOrdersByStatusForUser(@Param("userId") Long userId);
    
    // Recent orders - native query
    @Query(value = "SELECT * FROM work_orders WHERE assigned_to_id = :userId ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<WorkOrder> findRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);
    
    // Additional useful methods
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.completedAt BETWEEN :startDate AND :endDate")
    List<WorkOrder> findCompletedOrdersBetween(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.assignedTo.id = :userId AND wo.completedAt IS NOT NULL")
    long countCompletedOrdersByUser(@Param("userId") Long userId);
    
    // For dashboard statistics
    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.deadline < CURRENT_TIMESTAMP AND wo.status NOT IN ('COMPLETED', 'CANCELLED')")
    long countOverdueOrders();
    
    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.status = 'NEW'")
    long countNewOrders();
    
    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.assignedTo.id IS NULL")
    long countUnassignedOrders();
}