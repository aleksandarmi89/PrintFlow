package com.printflow.service;

import com.printflow.dto.DashboardStatsDTO;
import com.printflow.entity.WorkOrder.DeliveryType;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void readyToShipUsesCourierOnlyForTenant() {
        ClientRepository clientRepository = Mockito.mock(ClientRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        TenantContextService tenantContextService = Mockito.mock(TenantContextService.class);

        DashboardService service = new DashboardService(
            clientRepository,
            userRepository,
            workOrderRepository,
            taskRepository,
            tenantContextService
        );

        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(tenantContextService.requireCompanyId()).thenReturn(7L);
        when(clientRepository.countActiveClientsByCompany(7L)).thenReturn(0L);
        when(userRepository.findByCompany_IdAndActiveTrue(eq(7L), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of()));

        when(workOrderRepository.countByStatusAndDeliveryTypeAndCompany_Id(OrderStatus.READY_FOR_DELIVERY, DeliveryType.PICKUP, 7L)).thenReturn(1L);
        when(workOrderRepository.countByStatusAndDeliveryTypeAndCompany_Id(OrderStatus.READY_FOR_DELIVERY, DeliveryType.COURIER, 7L)).thenReturn(2L);
        when(workOrderRepository.countCourierReadyWithMissingShipmentDataByCompany(eq(7L), eq(OrderStatus.READY_FOR_DELIVERY), eq(List.of(DeliveryType.COURIER))))
            .thenReturn(4L);

        DashboardStatsDTO stats = service.getDashboardStats();

        assertEquals(2L, stats.getReadyToShipOrders());
        assertEquals(4L, stats.getBlockedCourierReadyOrders());
    }

    @Test
    void readyToShipUsesCourierOnlyForSuperAdmin() {
        ClientRepository clientRepository = Mockito.mock(ClientRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        TenantContextService tenantContextService = Mockito.mock(TenantContextService.class);

        DashboardService service = new DashboardService(
            clientRepository,
            userRepository,
            workOrderRepository,
            taskRepository,
            tenantContextService
        );

        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        when(clientRepository.countActiveClients()).thenReturn(0L);
        when(userRepository.countByActiveTrue()).thenReturn(0L);

        when(workOrderRepository.countByStatusAndDeliveryType(OrderStatus.READY_FOR_DELIVERY, DeliveryType.PICKUP)).thenReturn(2L);
        when(workOrderRepository.countByStatusAndDeliveryType(OrderStatus.READY_FOR_DELIVERY, DeliveryType.COURIER)).thenReturn(6L);
        when(workOrderRepository.countCourierReadyWithMissingShipmentData(eq(OrderStatus.READY_FOR_DELIVERY), eq(List.of(DeliveryType.COURIER))))
            .thenReturn(3L);

        DashboardStatsDTO stats = service.getDashboardStats();

        assertEquals(6L, stats.getReadyToShipOrders());
        assertEquals(3L, stats.getBlockedCourierReadyOrders());
    }
}
