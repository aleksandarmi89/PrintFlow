package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderActivity;
import com.printflow.repository.WorkOrderActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActivityLogService {

    private final WorkOrderActivityRepository activityRepository;
    private final TenantGuard tenantGuard;

    public ActivityLogService(WorkOrderActivityRepository activityRepository, TenantGuard tenantGuard) {
        this.activityRepository = activityRepository;
        this.tenantGuard = tenantGuard;
    }

    @Transactional
    public void log(WorkOrder workOrder, String type, String message, Long createdByUserId) {
        if (workOrder == null || workOrder.getCompany() == null) {
            return;
        }
        WorkOrderActivity activity = new WorkOrderActivity();
        activity.setCompany(workOrder.getCompany());
        activity.setWorkOrder(workOrder);
        activity.setType(type != null ? type : "INFO");
        activity.setMessage(message != null ? message : "");
        activity.setCreatedByUserId(createdByUserId);
        activityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderActivity> getForWorkOrder(Long workOrderId, Company company) {
        if (workOrderId == null || company == null) {
            return List.of();
        }
        return activityRepository.findByWorkOrder_IdAndCompany_IdOrderByCreatedAtDesc(workOrderId, company.getId());
    }

    @Transactional(readOnly = true)
    public List<WorkOrderActivity> getForWorkOrder(Long workOrderId) {
        Long companyId = tenantGuard.requireCompanyId();
        return activityRepository.findByWorkOrder_IdAndCompany_IdOrderByCreatedAtDesc(workOrderId, companyId);
    }
}
