package com.printflow.repository;

import com.printflow.entity.Task;
import com.printflow.entity.enums.TaskPriority;
import com.printflow.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    
    // ==================== BASIC QUERIES ====================
    
    // User taskovi
    @EntityGraph(attributePaths = {"workOrder", "workOrder.client", "assignedTo", "createdBy"})
    java.util.Optional<Task> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"workOrder", "workOrder.client", "assignedTo", "createdBy"})
    java.util.Optional<Task> findWithDetailsByIdAndCompany_Id(Long id, Long companyId);

    java.util.Optional<Task> findByIdAndCompany_Id(Long id, Long companyId);
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByAssignedToId(Long userId, Pageable pageable);
    List<Task> findByAssignedToId(Long userId);
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByAssignedToIdAndCompany_Id(Long userId, Long companyId, Pageable pageable);
    List<Task> findByAssignedToIdAndCompany_Id(Long userId, Long companyId);
    
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByAssignedToIdAndStatus(Long userId, TaskStatus status, Pageable pageable);
    List<Task> findByAssignedToIdAndStatus(Long userId, TaskStatus status);
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByAssignedToIdAndStatusAndCompany_Id(Long userId, TaskStatus status, Long companyId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"workOrder", "workOrder.client", "assignedTo"})
    Page<Task> findAll(@Nullable Specification<Task> spec, Pageable pageable);
    List<Task> findByAssignedToIdAndStatusAndCompany_Id(Long userId, TaskStatus status, Long companyId);
    
    // Validacija
    
    
    // ==================== STATUS QUERIES ====================
    
    List<Task> findByStatus(TaskStatus status);
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    List<Task> findByStatusAndCompany_Id(TaskStatus status, Long companyId);
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByStatusAndCompany_Id(TaskStatus status, Long companyId, Pageable pageable);
    
    List<Task> findByStatusIn(List<TaskStatus> statuses);
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByStatusIn(List<TaskStatus> statuses, Pageable pageable);
    Page<Task> findByStatusInAndCompany_Id(List<TaskStatus> statuses, Long companyId, Pageable pageable);
    
    List<Task> findByAssignedToIdAndStatusIn(Long userId, List<TaskStatus> statuses);
    @EntityGraph(attributePaths = {"workOrder", "assignedTo"})
    Page<Task> findByAssignedToIdAndStatusIn(Long userId, List<TaskStatus> statuses, Pageable pageable);
    
    // ==================== PRIORITY QUERIES ====================
    
    List<Task> findByPriority(TaskPriority priority);
    List<Task> findByAssignedToIdAndPriority(Long userId, TaskPriority priority);
    List<Task> findByStatusAndPriority(TaskStatus status, TaskPriority priority);
    
    // ==================== AVAILABLE TASKS (UNASSIGNED) ====================
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' AND t.company.id = :companyId")
    Page<Task> findAvailableTasks(@Param("companyId") Long companyId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING'")
    Page<Task> findAvailableTasksAll(Pageable pageable);
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' AND t.priority = :priority AND t.company.id = :companyId")
    Page<Task> findAvailableTasksByPriority(@Param("companyId") Long companyId, @Param("priority") TaskPriority priority, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' AND t.priority = :priority")
    Page<Task> findAvailableTasksByPriorityAll(@Param("priority") TaskPriority priority, Pageable pageable);
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' AND LOWER(t.requiredSkills) LIKE LOWER(CONCAT('%', :skill, '%')) AND t.company.id = :companyId")
    Page<Task> findAvailableTasksBySkill(@Param("companyId") Long companyId, @Param("skill") String skill, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' AND LOWER(t.requiredSkills) LIKE LOWER(CONCAT('%', :skill, '%'))")
    Page<Task> findAvailableTasksBySkillAll(@Param("skill") String skill, Pageable pageable);
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' AND t.priority = :priority AND LOWER(t.requiredSkills) LIKE LOWER(CONCAT('%', :skill, '%')) AND t.company.id = :companyId")
    Page<Task> findAvailableTasksWithPriorityAndSkill(
            @Param("companyId") Long companyId,
            @Param("priority") TaskPriority priority,
            @Param("skill") String skill,
            Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' AND t.priority = :priority AND LOWER(t.requiredSkills) LIKE LOWER(CONCAT('%', :skill, '%'))")
    Page<Task> findAvailableTasksWithPriorityAndSkillAll(
            @Param("priority") TaskPriority priority,
            @Param("skill") String skill,
            Pageable pageable);
    
    // ==================== WORK ORDER RELATED ====================
    
    List<Task> findByWorkOrderId(Long workOrderId);
    Page<Task> findByWorkOrderId(Long workOrderId, Pageable pageable);
    List<Task> findByWorkOrderIdAndCompany_Id(Long workOrderId, Long companyId);
    Page<Task> findByWorkOrderIdAndCompany_Id(Long workOrderId, Long companyId, Pageable pageable);

    Page<Task> findByCompany_Id(Long companyId, Pageable pageable);
    
    List<Task> findByWorkOrderIdAndAssignedToId(Long workOrderId, Long userId);
    List<Task> findByWorkOrderIdAndStatus(Long workOrderId, TaskStatus status);
    
    // ==================== DATE BASED QUERIES ====================
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId AND DATE(t.createdAt) = :date")
    List<Task> findByAssignedToIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId AND t.dueDate BETWEEN :start AND :end")
    List<Task> findByAssignedToIdAndDueDateBetween(@Param("userId") Long userId, 
                                                   @Param("start") LocalDateTime start, 
                                                   @Param("end") LocalDateTime end);
    
    // Overdue tasks
    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId AND t.dueDate < :now AND t.status != 'COMPLETED'")
    List<Task> findOverdueTasksByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
    
    @Query("SELECT t FROM Task t WHERE t.dueDate < :now AND t.status != 'COMPLETED'")
    List<Task> findAllOverdueTasks(@Param("now") LocalDateTime now);
    
    // ==================== STATISTICS ====================
    
    long countByAssignedToId(Long userId);
    long countByAssignedToIdAndStatus(Long userId, TaskStatus status);
    long countByAssignedToIdAndCompany_Id(Long userId, Long companyId);
    long countByAssignedToIdAndStatusAndCompany_Id(Long userId, TaskStatus status, Long companyId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignedTo.id = :userId AND t.dueDate < :now AND t.status != 'COMPLETED'")
    long countOverdueByAssignedToId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignedTo.id = :userId AND t.company.id = :companyId AND t.dueDate < :now AND t.status != 'COMPLETED'")
    long countOverdueByAssignedToIdAndCompany(@Param("userId") Long userId, @Param("companyId") Long companyId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignedTo.id = :userId AND t.completedAt BETWEEN :start AND :end")
    long countCompletedByAssignedToIdBetween(@Param("userId") Long userId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignedTo.id = :userId AND t.priority = 'HIGH' AND t.status <> :completedStatus")
    long countUrgentByAssignedToId(@Param("userId") Long userId, @Param("completedStatus") TaskStatus completedStatus);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignedTo.id = :userId AND t.company.id = :companyId AND t.priority = 'HIGH' AND t.status <> :completedStatus")
    long countUrgentByAssignedToIdAndCompany(@Param("userId") Long userId, @Param("companyId") Long companyId, @Param("completedStatus") TaskStatus completedStatus);

    @Query("SELECT COALESCE(SUM(t.estimatedHours), 0) FROM Task t WHERE t.assignedTo.id = :userId")
    double sumEstimatedHoursByAssignedToId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(t.estimatedHours), 0) FROM Task t WHERE t.assignedTo.id = :userId AND t.status = :status")
    double sumEstimatedHoursByAssignedToIdAndStatus(@Param("userId") Long userId, @Param("status") TaskStatus status);
    
    @Query("SELECT COUNT(t) FROM Task t WHERE t.workOrder.id = :workOrderId")
    long countByWorkOrderId(@Param("workOrderId") Long workOrderId);
    
    @Query("SELECT t.status, COUNT(t) FROM Task t WHERE t.assignedTo.id = :userId GROUP BY t.status")
    List<Object[]> countTasksByStatusForUser(@Param("userId") Long userId);
    
    @Query("SELECT t.priority, COUNT(t) FROM Task t WHERE t.assignedTo.id = :userId GROUP BY t.priority")
    List<Object[]> countTasksByPriorityForUser(@Param("userId") Long userId);
    
    // ==================== SEARCH METHODS ====================
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId AND " +
           "(LOWER(t.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.notes) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Task> searchByUserIdAndText(@Param("userId") Long userId, 
                                     @Param("searchTerm") String searchTerm, 
                                     Pageable pageable);
    
    @Query("SELECT t FROM Task t WHERE " +
           "(LOWER(t.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.notes) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Task> searchAllByText(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    // ==================== COMPLETED TASKS ====================
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId AND t.status = 'COMPLETED' AND t.completedAt BETWEEN :start AND :end")
    List<Task> findCompletedTasksByUserAndDateRange(@Param("userId") Long userId, 
                                                    @Param("start") LocalDateTime start, 
                                                    @Param("end") LocalDateTime end);
    
    // ==================== TIME TRACKING RELATED ====================
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId AND t.timerStartedAt IS NOT NULL")
    List<Task> findActiveTimersByUserId(@Param("userId") Long userId);
    
    // ==================== BULK OPERATIONS ====================
    
    @Query("UPDATE Task t SET t.status = :newStatus WHERE t.id IN :taskIds")
    int updateStatusForTasks(@Param("taskIds") List<Long> taskIds, @Param("newStatus") TaskStatus newStatus);
    
    @Query("UPDATE Task t SET t.assignedTo.id = :userId WHERE t.id IN :taskIds")
    int assignTasksToUser(@Param("taskIds") List<Long> taskIds, @Param("userId") Long userId);
 // U TaskRepository dodajte ove metode:
    boolean existsByIdAndAssignedToId(Long taskId, Long userId);

    @Query("SELECT COUNT(t) > 0 FROM Task t WHERE t.id = :taskId AND t.workOrder.id = :workOrderId AND t.assignedTo.id = :userId")
    boolean existsTaskInWorkOrderAssignedToUser(@Param("taskId") Long taskId, 
                                               @Param("workOrderId") Long workOrderId, 
                                               @Param("userId") Long userId);
 
}
