package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderTotalsSyncServiceTest {

    @Test
    void syncFromItemsSavesWhenTotalsChanged() {
        WorkOrderItemRepository itemRepository = mock(WorkOrderItemRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        WorkOrderTotalsSyncService service = new WorkOrderTotalsSyncService(itemRepository, workOrderRepository);

        WorkOrder order = new WorkOrder();
        order.setId(10L);
        order.setPrice(0.0d);
        order.setCost(0.0d);
        Company company = new Company();
        company.setId(3L);
        order.setCompany(company);

        WorkOrderItem a = new WorkOrderItem();
        a.setCalculatedPrice(new BigDecimal("100.00"));
        a.setCalculatedCost(new BigDecimal("75.00"));
        WorkOrderItem b = new WorkOrderItem();
        b.setCalculatedPrice(new BigDecimal("50.00"));
        b.setCalculatedCost(new BigDecimal("30.00"));
        when(itemRepository.findAllByWorkOrder_IdAndCompany_Id(10L, 3L)).thenReturn(List.of(a, b));

        service.syncFromItems(order);

        verify(workOrderRepository).save(order);
    }

    @Test
    void syncFromItemsDoesNotSaveWhenTotalsUnchanged() {
        WorkOrderItemRepository itemRepository = mock(WorkOrderItemRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        WorkOrderTotalsSyncService service = new WorkOrderTotalsSyncService(itemRepository, workOrderRepository);

        WorkOrder order = new WorkOrder();
        order.setId(11L);
        order.setPrice(150.0d);
        order.setCost(105.0d);
        Company company = new Company();
        company.setId(4L);
        order.setCompany(company);

        WorkOrderItem a = new WorkOrderItem();
        a.setCalculatedPrice(new BigDecimal("100.00"));
        a.setCalculatedCost(new BigDecimal("75.00"));
        WorkOrderItem b = new WorkOrderItem();
        b.setCalculatedPrice(new BigDecimal("50.00"));
        b.setCalculatedCost(new BigDecimal("30.00"));
        when(itemRepository.findAllByWorkOrder_IdAndCompany_Id(11L, 4L)).thenReturn(List.of(a, b));

        service.syncFromItems(order);

        verify(workOrderRepository, never()).save(order);
    }

    @Test
    void syncFromItemsNormalizesNullTotalsToZeroAndSaves() {
        WorkOrderItemRepository itemRepository = mock(WorkOrderItemRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        WorkOrderTotalsSyncService service = new WorkOrderTotalsSyncService(itemRepository, workOrderRepository);

        WorkOrder order = new WorkOrder();
        order.setId(12L);
        order.setPrice(null);
        order.setCost(null);
        Company company = new Company();
        company.setId(5L);
        order.setCompany(company);

        when(itemRepository.findAllByWorkOrder_IdAndCompany_Id(12L, 5L)).thenReturn(Collections.emptyList());

        service.syncFromItems(order);

        verify(workOrderRepository).save(order);
    }

    @Test
    void syncFromItemsHandlesNullRepositoryResult() {
        WorkOrderItemRepository itemRepository = mock(WorkOrderItemRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        WorkOrderTotalsSyncService service = new WorkOrderTotalsSyncService(itemRepository, workOrderRepository);

        WorkOrder order = new WorkOrder();
        order.setId(13L);
        order.setPrice(10.0d);
        order.setCost(5.0d);
        Company company = new Company();
        company.setId(6L);
        order.setCompany(company);

        when(itemRepository.findAllByWorkOrder_IdAndCompany_Id(13L, 6L)).thenReturn(null);

        service.syncFromItems(order);

        verify(workOrderRepository).save(order);
    }
}
