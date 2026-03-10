package com.printflow.service;

import com.printflow.config.PlanLimitsProperties;
import com.printflow.entity.Company;
import com.printflow.entity.enums.PlanTier;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanLimitServiceTest {

    @Test
    void userLimitIsEnforced() {
        PlanLimitsProperties properties = new PlanLimitsProperties();
        PlanLimitsProperties.PlanLimits free = new PlanLimitsProperties.PlanLimits();
        free.setMaxUsers(1);
        properties.setFree(free);

        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);

        Company company = new Company();
        company.setId(10L);
        company.setPlan(PlanTier.FREE);

        when(userRepository.countByCompany_IdAndActiveTrue(10L)).thenReturn(1L);

        PlanLimitService service = new PlanLimitService(properties, userRepository, workOrderRepository, attachmentRepository);

        assertThrows(PlanLimitExceededException.class, () -> service.assertUserLimit(company));

        when(userRepository.countByCompany_IdAndActiveTrue(10L)).thenReturn(0L);
        assertDoesNotThrow(() -> service.assertUserLimit(company));
    }

    @Test
    void monthlyOrderLimitIsEnforced() {
        PlanLimitsProperties properties = new PlanLimitsProperties();
        PlanLimitsProperties.PlanLimits free = new PlanLimitsProperties.PlanLimits();
        free.setMaxMonthlyOrders(2);
        properties.setFree(free);

        UserRepository userRepository = mock(UserRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);

        Company company = new Company();
        company.setId(20L);
        company.setPlan(PlanTier.FREE);

        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(
            org.mockito.ArgumentMatchers.eq(20L),
            org.mockito.ArgumentMatchers.any()
        ))
            .thenReturn(2L);

        PlanLimitService service = new PlanLimitService(properties, userRepository, workOrderRepository, attachmentRepository);

        assertThrows(PlanLimitExceededException.class, () -> service.assertMonthlyOrdersLimit(company));

        when(workOrderRepository.countByCompany_IdAndCreatedAtAfter(
            org.mockito.ArgumentMatchers.eq(20L),
            org.mockito.ArgumentMatchers.any()
        ))
            .thenReturn(1L);
        assertDoesNotThrow(() -> service.assertMonthlyOrdersLimit(company));
    }
}
