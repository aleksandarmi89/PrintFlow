package com.printflow.repository;

import com.printflow.entity.WorkOrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkOrderItemRepository extends JpaRepository<WorkOrderItem, Long> {
    @EntityGraph(attributePaths = {"variant"})
    List<WorkOrderItem> findAllByWorkOrder_IdAndCompany_Id(Long workOrderId, Long companyId);
    @EntityGraph(attributePaths = {"variant"})
    List<WorkOrderItem> findAllByWorkOrder_Id(Long workOrderId);

    Optional<WorkOrderItem> findByIdAndCompany_Id(Long id, Long companyId);

    @Query("""
        SELECT i.workOrder.id, COALESCE(SUM(i.calculatedPrice), 0)
        FROM WorkOrderItem i
        WHERE i.workOrder.id IN :orderIds
        GROUP BY i.workOrder.id
        """)
    List<Object[]> sumPriceByWorkOrderIds(@Param("orderIds") List<Long> orderIds);

    @Query("""
        SELECT i.workOrder.id, COALESCE(SUM(i.calculatedPrice), 0)
        FROM WorkOrderItem i
        WHERE i.workOrder.id IN :orderIds
          AND i.company.id = :companyId
        GROUP BY i.workOrder.id
        """)
    List<Object[]> sumPriceByWorkOrderIdsAndCompanyId(@Param("orderIds") List<Long> orderIds,
                                                       @Param("companyId") Long companyId);

    @Query("""
        SELECT i.workOrder.id, COALESCE(SUM(i.calculatedCost), 0)
        FROM WorkOrderItem i
        WHERE i.workOrder.id IN :orderIds
        GROUP BY i.workOrder.id
        """)
    List<Object[]> sumCostByWorkOrderIds(@Param("orderIds") List<Long> orderIds);

    @Query("""
        SELECT i.workOrder.id, COALESCE(SUM(i.calculatedCost), 0)
        FROM WorkOrderItem i
        WHERE i.workOrder.id IN :orderIds
          AND i.company.id = :companyId
        GROUP BY i.workOrder.id
        """)
    List<Object[]> sumCostByWorkOrderIdsAndCompanyId(@Param("orderIds") List<Long> orderIds,
                                                      @Param("companyId") Long companyId);

    @Query("""
        SELECT i.workOrder.id, COUNT(i.id)
        FROM WorkOrderItem i
        WHERE i.workOrder.id IN :orderIds
        GROUP BY i.workOrder.id
        """)
    List<Object[]> countItemsByWorkOrderIds(@Param("orderIds") List<Long> orderIds);

    @Query("""
        SELECT i.workOrder.id, COUNT(i.id)
        FROM WorkOrderItem i
        WHERE i.workOrder.id IN :orderIds
          AND i.company.id = :companyId
        GROUP BY i.workOrder.id
        """)
    List<Object[]> countItemsByWorkOrderIdsAndCompanyId(@Param("orderIds") List<Long> orderIds,
                                                         @Param("companyId") Long companyId);
}
