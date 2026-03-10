package com.printflow.repository;

import com.printflow.entity.WorkOrder;
import com.printflow.pricing.dto.WorkOrderSelectRow;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.User;
import com.printflow.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy", "company"})
    Optional<WorkOrder> findWithRelationsById(Long id);

    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy", "company"})
    Optional<WorkOrder> findWithRelationsByIdAndCompany_Id(Long id, Long companyId);

    Optional<WorkOrder> findByOrderNumber(String orderNumber);
    Optional<WorkOrder> findByPublicToken(String publicToken);
    @EntityGraph(attributePaths = {"client", "company"})
    Optional<WorkOrder> findWithClientAndCompanyByPublicToken(String publicToken);
    Optional<WorkOrder> findByPublicTokenAndCompany_Id(String publicToken, Long companyId);
    Optional<WorkOrder> findByPublicTokenAndCompany_IdAndPublicTokenExpiresAtAfter(String publicToken, Long companyId, LocalDateTime now);
    Optional<WorkOrder> findByPublicTokenAndPublicTokenExpiresAtAfter(String publicToken, LocalDateTime now);
    Optional<WorkOrder> findByIdAndPublicTokenAndPublicTokenExpiresAtAfter(Long id, String publicToken, LocalDateTime now);
    Optional<WorkOrder> findByOrderNumberIgnoreCase(String orderNumber);
    @Query("SELECT wo.company.id FROM WorkOrder wo WHERE wo.publicToken = :token")
    Optional<Long> findCompanyIdByPublicToken(@Param("token") String token);
    @Query("SELECT wo.company.id FROM WorkOrder wo WHERE wo.publicToken = :token AND wo.publicTokenExpiresAt > :now")
    Optional<Long> findCompanyIdByPublicTokenAndPublicTokenExpiresAtAfter(@Param("token") String token, @Param("now") LocalDateTime now);
    Optional<WorkOrder> findByIdAndCompany_Id(Long id, Long companyId);
    @Query("SELECT wo.id as id, wo.orderNumber as orderNumber, wo.title as title " +
           "FROM WorkOrder wo " +
           "WHERE wo.company.id = :companyId " +
           "ORDER BY wo.createdAt DESC")
    List<WorkOrderSelectRow> findSelectRowsByCompanyId(@Param("companyId") Long companyId);
    List<WorkOrder> findByClient_IdAndCompany_Id(Long clientId, Long companyId);
    Optional<WorkOrder> findByIdAndClient_IdAndCompany_Id(Long id, Long clientId, Long companyId);
    Optional<WorkOrder> findByPublicTokenAndClient_IdAndCompany_Id(String publicToken, Long clientId, Long companyId);
    List<WorkOrder> findByCompany_Id(Long companyId);
    long countByCompany_Id(Long companyId);
    List<WorkOrder> findByCompany_IdAndStatus(Long companyId, OrderStatus status);
    
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

    @Query("SELECT wo FROM WorkOrder wo WHERE wo.company.id = :companyId AND " +
           "(LOWER(wo.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(wo.orderNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(wo.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<WorkOrder> searchByCompany(@Param("companyId") Long companyId, @Param("query") String query);

    @Query("SELECT wo FROM WorkOrder wo WHERE wo.company.id = :companyId AND " +
           "(LOWER(wo.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(wo.orderNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(wo.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> searchByCompanyAll(@Param("companyId") Long companyId,
                                       @Param("query") String query,
                                       Pageable pageable);
    
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.assignedTo.id = :userId AND wo.status = :status")
    List<WorkOrder> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") OrderStatus status);
    
    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.status = :status")
    long countByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.company.id = :companyId AND wo.status = :status")
    long countByStatusAndCompanyId(@Param("companyId") Long companyId, @Param("status") OrderStatus status);
    
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.priority >= :minPriority ORDER BY wo.priority DESC, wo.deadline ASC")
    List<WorkOrder> findHighPriorityOrders(@Param("minPriority") Integer minPriority);
    
    // Count methods
    int countByAssignedToId(Long id);
    int countByAssignedToIdAndStatus(Long id, OrderStatus completed);
    int countByAssignedToIdAndCompany_Id(Long id, Long companyId);
    int countByAssignedToIdAndStatusAndCompany_Id(Long id, OrderStatus status, Long companyId);
    
    // Existence check for work order assignment
    boolean existsByIdAndAssignedToId(Long workOrderId, Long userId);
    
    // Paging methods
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByCompany_IdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    
    // Assigned to user
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.assignedTo.id = :userId")
    Page<WorkOrder> findPageByAssignedToId(@Param("userId") Long userId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByAssignedToId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByAssignedToIdAndCompany_Id(Long userId, Long companyId, Pageable pageable);
    List<WorkOrder> findByAssignedToId(Long userId);
    List<WorkOrder> findByAssignedToIdAndCompany_Id(Long userId, Long companyId);
    
    // Unassigned
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByAssignedToIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByAssignedToIsNullAndCompany_Id(Long companyId, Pageable pageable);
    List<WorkOrder> findByAssignedToIsNull();
    List<WorkOrder> findByAssignedToIsNullAndCompany_Id(Long companyId);
    
    // Status-based
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByStatusAndCompany_Id(OrderStatus status, Long companyId, Pageable pageable);
    List<WorkOrder> findByStatus(OrderStatus status);
    List<WorkOrder> findByStatusAndCompany_Id(OrderStatus status, Long companyId);

    @Query("SELECT wo FROM WorkOrder wo " +
           "LEFT JOIN FETCH wo.client " +
           "LEFT JOIN FETCH wo.assignedTo " +
           "WHERE wo.status NOT IN :excludedStatuses " +
           "AND wo.deadline BETWEEN :start AND :end " +
           "ORDER BY wo.deadline ASC")
    List<WorkOrder> findDueSoonOrders(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("excludedStatuses") List<OrderStatus> excludedStatuses,
                                      Pageable pageable);

    @Query("SELECT wo FROM WorkOrder wo " +
           "LEFT JOIN FETCH wo.client " +
           "LEFT JOIN FETCH wo.assignedTo " +
           "WHERE wo.company.id = :companyId " +
           "AND wo.status NOT IN :excludedStatuses " +
           "AND wo.deadline BETWEEN :start AND :end " +
           "ORDER BY wo.deadline ASC")
    List<WorkOrder> findDueSoonOrdersByCompany(@Param("companyId") Long companyId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               @Param("excludedStatuses") List<OrderStatus> excludedStatuses,
                                               Pageable pageable);
    
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
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
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

    @Query("SELECT wo FROM WorkOrder wo WHERE wo.company.id = :companyId AND wo.deadline < :now AND wo.status NOT IN :excludedStatuses")
    List<WorkOrder> findOverdueOrdersByCompany(@Param("companyId") Long companyId,
                                               @Param("now") LocalDateTime now,
                                               @Param("excludedStatuses") List<OrderStatus> excludedStatuses);

    @Query("SELECT wo FROM WorkOrder wo WHERE wo.deadline < :now AND wo.status NOT IN :excludedStatuses")
    List<WorkOrder> findOverdueOrders(@Param("now") LocalDateTime now,
                                      @Param("excludedStatuses") List<OrderStatus> excludedStatuses,
                                      Pageable pageable);

    @Query("SELECT wo FROM WorkOrder wo WHERE wo.company.id = :companyId AND wo.deadline < :now AND wo.status NOT IN :excludedStatuses")
    List<WorkOrder> findOverdueOrdersByCompany(@Param("companyId") Long companyId,
                                               @Param("now") LocalDateTime now,
                                               @Param("excludedStatuses") List<OrderStatus> excludedStatuses,
                                               Pageable pageable);

    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.deadline < :now AND wo.status NOT IN :excludedStatuses")
    long countOverdueOrders(@Param("now") LocalDateTime now,
                            @Param("excludedStatuses") List<OrderStatus> excludedStatuses);

    @Query("SELECT COUNT(wo) FROM WorkOrder wo WHERE wo.company.id = :companyId AND wo.deadline < :now AND wo.status NOT IN :excludedStatuses")
    long countOverdueOrdersByCompany(@Param("companyId") Long companyId,
                                     @Param("now") LocalDateTime now,
                                     @Param("excludedStatuses") List<OrderStatus> excludedStatuses);

    long countByCompany_IdAndCreatedAtAfter(Long companyId, LocalDateTime createdAt);
    
    // Date range
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.createdAt BETWEEN :start AND :end")
    List<WorkOrder> findByDateRange(@Param("start") LocalDateTime start, 
                                    @Param("end") LocalDateTime end);

    @Query("SELECT wo FROM WorkOrder wo WHERE wo.company.id = :companyId AND wo.createdAt BETWEEN :start AND :end")
    List<WorkOrder> findByDateRangeAndCompany(@Param("companyId") Long companyId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(wo.price), 0) FROM WorkOrder wo WHERE wo.status = :status AND wo.createdAt BETWEEN :start AND :end")
    double sumPriceByStatusAndDateRange(@Param("status") OrderStatus status,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(wo.price), 0) FROM WorkOrder wo WHERE wo.company.id = :companyId AND wo.status = :status AND wo.createdAt BETWEEN :start AND :end")
    double sumPriceByStatusAndDateRangeAndCompany(@Param("companyId") Long companyId,
                                                  @Param("status") OrderStatus status,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(wo.price), 0) FROM WorkOrder wo WHERE wo.status NOT IN :excludedStatuses")
    double sumPriceByStatusNotIn(@Param("excludedStatuses") List<OrderStatus> excludedStatuses);

    @Query("SELECT COALESCE(SUM(wo.price), 0) FROM WorkOrder wo WHERE wo.company.id = :companyId AND wo.status NOT IN :excludedStatuses")
    double sumPriceByStatusNotInAndCompany(@Param("companyId") Long companyId,
                                           @Param("excludedStatuses") List<OrderStatus> excludedStatuses);
    
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

    @Query("SELECT new com.printflow.dto.PlannerWorkerLoadDTO(u.fullName, COUNT(wo)) " +
           "FROM WorkOrder wo JOIN wo.assignedTo u " +
           "WHERE wo.status NOT IN :excludedStatuses " +
           "GROUP BY u.fullName " +
           "ORDER BY COUNT(wo) DESC")
    List<com.printflow.dto.PlannerWorkerLoadDTO> findWorkerLoad(@Param("excludedStatuses") List<OrderStatus> excludedStatuses,
                                                                Pageable pageable);

    @Query("SELECT new com.printflow.dto.PlannerWorkerLoadDTO(u.fullName, COUNT(wo)) " +
           "FROM WorkOrder wo JOIN wo.assignedTo u " +
           "WHERE wo.company.id = :companyId " +
           "AND wo.status NOT IN :excludedStatuses " +
           "GROUP BY u.fullName " +
           "ORDER BY COUNT(wo) DESC")
    List<com.printflow.dto.PlannerWorkerLoadDTO> findWorkerLoadByCompany(@Param("companyId") Long companyId,
                                                                         @Param("excludedStatuses") List<OrderStatus> excludedStatuses,
                                                                         Pageable pageable);
    
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
