package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.util.OrderNumberGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkOrderServiceTest {

    @Test
    void approveDesign_blocksWhenBillingExpired() {
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        ClientRepository clientRepository = Mockito.mock(ClientRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        OrderNumberGenerator orderNumberGenerator = Mockito.mock(OrderNumberGenerator.class);
        TenantGuard tenantGuard = Mockito.mock(TenantGuard.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        PlanLimitService planLimitService = Mockito.mock(PlanLimitService.class);
        BillingAccessService billingAccessService = Mockito.mock(BillingAccessService.class);
        PublicTokenService publicTokenService = Mockito.mock(PublicTokenService.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        ClientPricingProfileService pricingProfileService = Mockito.mock(ClientPricingProfileService.class);
        ActivityLogService activityLogService = Mockito.mock(ActivityLogService.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);

        WorkOrderService service = new WorkOrderService(
            workOrderRepository,
            clientRepository,
            userRepository,
            attachmentRepository,
            orderNumberGenerator,
            tenantGuard,
            notificationService,
            auditLogService,
            planLimitService,
            billingAccessService,
            publicTokenService,
            workOrderItemRepository,
            pricingProfileService,
            activityLogService,
            eventPublisher
        );

        Company company = new Company();
        company.setId(10L);
        WorkOrder order = new WorkOrder();
        order.setId(99L);
        order.setCompany(company);

        LocalDateTime now = LocalDateTime.now();
        when(publicTokenService.now()).thenReturn(now);
        when(workOrderRepository.findByIdAndPublicTokenAndPublicTokenExpiresAtAfter(99L, "token", now))
            .thenReturn(Optional.of(order));
        doThrow(new BillingRequiredException("billing.notice.expired"))
            .when(billingAccessService).assertBillingActiveForPremiumAction(10L);

        assertThrows(BillingRequiredException.class, () -> service.approveDesign(99L, "token", true, "ok"));

        verify(billingAccessService).assertBillingActiveForPremiumAction(10L);
        verifyNoInteractions(auditLogService, activityLogService, notificationService);
    }

    @Test
    void reorderWorkOrder_includesSourceLabelInNotesAndAudit() {
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        ClientRepository clientRepository = Mockito.mock(ClientRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        OrderNumberGenerator orderNumberGenerator = Mockito.mock(OrderNumberGenerator.class);
        TenantGuard tenantGuard = Mockito.mock(TenantGuard.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        PlanLimitService planLimitService = Mockito.mock(PlanLimitService.class);
        BillingAccessService billingAccessService = Mockito.mock(BillingAccessService.class);
        PublicTokenService publicTokenService = Mockito.mock(PublicTokenService.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        ClientPricingProfileService pricingProfileService = Mockito.mock(ClientPricingProfileService.class);
        ActivityLogService activityLogService = Mockito.mock(ActivityLogService.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);

        WorkOrderService service = new WorkOrderService(
            workOrderRepository,
            clientRepository,
            userRepository,
            attachmentRepository,
            orderNumberGenerator,
            tenantGuard,
            notificationService,
            auditLogService,
            planLimitService,
            billingAccessService,
            publicTokenService,
            workOrderItemRepository,
            pricingProfileService,
            activityLogService,
            eventPublisher
        );

        Company company = new Company();
        company.setId(2L);
        WorkOrder source = new WorkOrder();
        source.setId(5L);
        source.setCompany(company);
        source.setOrderNumber("WO-55");
        source.setTitle("Source");

        when(tenantGuard.requireCompanyId()).thenReturn(2L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(5L, 2L)).thenReturn(Optional.of(source));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-99");
        PublicTokenService.TokenInfo tokenInfo =
            new PublicTokenService.TokenInfo("tok", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        when(publicTokenService.newToken()).thenReturn(tokenInfo);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> {
            WorkOrder wo = inv.getArgument(0);
            if (wo.getId() == null) {
                wo.setId(99L);
            }
            return wo;
        });
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(5L, 2L)).thenReturn(List.of());

        service.reorderWorkOrder(5L, null, "portal");

        ArgumentCaptor<WorkOrder> orderCaptor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        WorkOrder savedOrder = orderCaptor.getAllValues().get(0);
        assertEquals("Reordered from WorkOrder #WO-55 (portal)", savedOrder.getInternalNotes());
        verify(auditLogService).log(any(), eq("WorkOrder"), eq(99L), eq(null), eq(null),
            eq("Reordered from WorkOrder #WO-55 (portal)"), eq(company));
    }
}
