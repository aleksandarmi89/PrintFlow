package com.printflow.repository;

import com.printflow.entity.WorkOrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
