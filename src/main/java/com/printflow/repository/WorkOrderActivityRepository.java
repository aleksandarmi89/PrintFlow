package com.printflow.repository;

import com.printflow.entity.WorkOrderActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderActivityRepository extends JpaRepository<WorkOrderActivity, Long> {
    List<WorkOrderActivity> findByWorkOrder_IdAndCompany_IdOrderByCreatedAtDesc(Long workOrderId, Long companyId);
}
