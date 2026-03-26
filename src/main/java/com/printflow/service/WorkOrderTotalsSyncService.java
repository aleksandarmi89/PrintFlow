package com.printflow.service;

import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WorkOrderTotalsSyncService {

    private final WorkOrderItemRepository workOrderItemRepository;
    private final WorkOrderRepository workOrderRepository;

    public WorkOrderTotalsSyncService(WorkOrderItemRepository workOrderItemRepository,
                                      WorkOrderRepository workOrderRepository) {
        this.workOrderItemRepository = workOrderItemRepository;
        this.workOrderRepository = workOrderRepository;
    }

    @Transactional
    public void syncFromItems(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getId() == null) {
            return;
        }
        Long companyId = workOrder.getCompany() != null ? workOrder.getCompany().getId() : null;
        List<WorkOrderItem> items = companyId != null
            ? workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(workOrder.getId(), companyId)
            : workOrderItemRepository.findAllByWorkOrder_Id(workOrder.getId());
        if (items == null) {
            items = java.util.Collections.emptyList();
        }

        BigDecimal totalPrice = items.stream()
            .map(WorkOrderItem::getCalculatedPrice)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = items.stream()
            .map(WorkOrderItem::getCalculatedCost)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        double nextPrice = totalPrice.doubleValue();
        double nextCost = totalCost.doubleValue();
        double currentPrice = workOrder.getPrice() != null ? workOrder.getPrice() : 0.0d;
        double currentCost = workOrder.getCost() != null ? workOrder.getCost() : 0.0d;
        boolean priceChanged = workOrder.getPrice() == null || Math.abs(currentPrice - nextPrice) > 0.0001d;
        boolean costChanged = workOrder.getCost() == null || Math.abs(currentCost - nextCost) > 0.0001d;
        if (!priceChanged && !costChanged) {
            return;
        }
        workOrder.setPrice(nextPrice);
        workOrder.setCost(nextCost);
        workOrderRepository.save(workOrder);
    }
}
