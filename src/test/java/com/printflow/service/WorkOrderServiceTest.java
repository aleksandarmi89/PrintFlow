package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.PrintType;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void updateWorkOrder_allowsUnassigningWorker() {
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
        company.setId(3L);
        User assigned = new User();
        assigned.setId(17L);
        assigned.setFullName("Worker One");

        WorkOrder order = new WorkOrder();
        order.setId(77L);
        order.setCompany(company);
        order.setAssignedTo(assigned);
        order.setStatus(null);
        order.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(3L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(77L, 3L)).thenReturn(Optional.of(order));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("Updated");
        dto.setAssignedToId(null);
        dto.setPrintType(PrintType.OTHER);

        com.printflow.dto.WorkOrderDTO result = service.updateWorkOrder(77L, dto);

        assertNull(result.getAssignedToId());
        verify(notificationService, never()).sendOrderAssignedNotification(any(), any());
    }

    @Test
    void updateWorkOrder_rejectsUnknownAssignee() {
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
        company.setId(4L);
        WorkOrder order = new WorkOrder();
        order.setId(88L);
        order.setCompany(company);
        order.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(4L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(88L, 4L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndCompany_Id(999L, 4L)).thenReturn(Optional.empty());

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("Updated");
        dto.setAssignedToId(999L);
        dto.setPrintType(PrintType.OTHER);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateWorkOrder(88L, dto));
        assertEquals("User not found", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void updateWorkOrder_rejectsBlankTitle() {
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
        order.setId(90L);
        order.setCompany(company);
        order.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(90L, 10L)).thenReturn(Optional.of(order));

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("   ");
        dto.setPrintType(PrintType.OTHER);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateWorkOrder(90L, dto));
        assertEquals("Work order title is required", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void updateWorkOrder_trimsTitleBeforePersist() {
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
        company.setId(11L);
        WorkOrder order = new WorkOrder();
        order.setId(91L);
        order.setCompany(company);
        order.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(11L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(91L, 11L)).thenReturn(Optional.of(order));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("  Updated title  ");
        dto.setPrintType(PrintType.OTHER);

        service.updateWorkOrder(91L, dto);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        assertEquals("Updated title", captor.getValue().getTitle());
    }

    @Test
    void updateWorkOrderStatus_rejectsNullStatus() {
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

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateWorkOrderStatus(10L, null, null));
        assertEquals("Status is required", ex.getMessage());
        verifyNoInteractions(workOrderRepository);
    }

    @Test
    void updateWorkOrderStatus_setsCompletedAtWhenCompleted() {
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
        company.setId(12L);
        WorkOrder order = new WorkOrder();
        order.setId(92L);
        order.setCompany(company);
        order.setStatus(OrderStatus.SENT);
        order.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(12L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(92L, 12L)).thenReturn(Optional.of(order));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantGuard.getCurrentUser()).thenReturn(null);

        com.printflow.dto.WorkOrderDTO result = service.updateWorkOrderStatus(92L, OrderStatus.COMPLETED, "done");

        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCompletedAt());
    }

    @Test
    void createWorkOrder_rejectsBlankTitle() {
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

        when(tenantGuard.requireCompanyId()).thenReturn(9L);

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("   ");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createWorkOrder(dto));
        assertEquals("Work order title is required", ex.getMessage());
        verifyNoInteractions(clientRepository, workOrderRepository);
    }

    @Test
    void createWorkOrder_rejectsMissingClient() {
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

        when(tenantGuard.requireCompanyId()).thenReturn(9L);

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("Order");
        dto.setClientId(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createWorkOrder(dto));
        assertEquals("Client is required", ex.getMessage());
        verifyNoInteractions(clientRepository, workOrderRepository);
    }

    @Test
    void createWorkOrder_rejectsUnknownAssignee() {
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
        company.setId(6L);
        com.printflow.entity.Client client = new com.printflow.entity.Client();
        client.setId(77L);
        client.setCompany(company);

        when(tenantGuard.requireCompanyId()).thenReturn(6L);
        when(clientRepository.findByIdAndCompany_Id(77L, 6L)).thenReturn(Optional.of(client));
        when(userRepository.findByIdAndCompany_Id(404L, 6L)).thenReturn(Optional.empty());
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-101");
        PublicTokenService.TokenInfo tokenInfo =
            new PublicTokenService.TokenInfo("tok", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        when(publicTokenService.newToken()).thenReturn(tokenInfo);

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("Order");
        dto.setClientId(77L);
        dto.setAssignedToId(404L);
        dto.setPrintType(PrintType.OTHER);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createWorkOrder(dto));
        assertEquals("User not found", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrder_rejectsUnknownCreator() {
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
        company.setId(6L);
        com.printflow.entity.Client client = new com.printflow.entity.Client();
        client.setId(77L);
        client.setCompany(company);

        when(tenantGuard.requireCompanyId()).thenReturn(6L);
        when(clientRepository.findByIdAndCompany_Id(77L, 6L)).thenReturn(Optional.of(client));
        when(userRepository.findByIdAndCompany_Id(405L, 6L)).thenReturn(Optional.empty());
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-102");
        PublicTokenService.TokenInfo tokenInfo =
            new PublicTokenService.TokenInfo("tok", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        when(publicTokenService.newToken()).thenReturn(tokenInfo);

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("Order");
        dto.setClientId(77L);
        dto.setCreatedById(405L);
        dto.setPrintType(PrintType.OTHER);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createWorkOrder(dto));
        assertEquals("User not found", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrder_trimsTitleBeforePersist() {
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
        company.setId(7L);
        com.printflow.entity.Client client = new com.printflow.entity.Client();
        client.setId(88L);
        client.setCompany(company);

        when(tenantGuard.requireCompanyId()).thenReturn(7L);
        when(clientRepository.findByIdAndCompany_Id(88L, 7L)).thenReturn(Optional.of(client));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-103");
        PublicTokenService.TokenInfo tokenInfo =
            new PublicTokenService.TokenInfo("tok", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        when(publicTokenService.newToken()).thenReturn(tokenInfo);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> {
            WorkOrder wo = inv.getArgument(0);
            wo.setId(103L);
            return wo;
        });

        com.printflow.dto.WorkOrderDTO dto = new com.printflow.dto.WorkOrderDTO();
        dto.setTitle("   New title   ");
        dto.setClientId(88L);
        dto.setPrintType(PrintType.OTHER);

        service.createWorkOrder(dto);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        assertEquals("New title", captor.getValue().getTitle());
    }

    @Test
    void reorderWorkOrder_rejectsUnknownCreator() {
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
        when(userRepository.findByIdAndCompany_Id(404L, 2L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.reorderWorkOrder(5L, 404L, "portal"));
        assertEquals("User not found", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void duplicateWorkOrder_rejectsUnknownCreator() {
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
        company.setId(3L);
        WorkOrder source = new WorkOrder();
        source.setId(6L);
        source.setCompany(company);
        source.setOrderNumber("WO-56");
        source.setTitle("Source");
        source.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(3L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(6L, 3L)).thenReturn(Optional.of(source));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-100");
        PublicTokenService.TokenInfo tokenInfo =
            new PublicTokenService.TokenInfo("tok", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        when(publicTokenService.newToken()).thenReturn(tokenInfo);
        when(userRepository.findByIdAndCompany_Id(405L, 3L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.duplicateWorkOrder(6L, 405L, false));
        assertEquals("User not found", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void getWorkOrderByPublicToken_trimsTokenBeforeLookup() {
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

        WorkOrder order = new WorkOrder();
        order.setId(101L);
        order.setTitle("Order");
        order.setPublicToken("abc");
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusHours(2));

        when(workOrderRepository.findWithClientAndCompanyByPublicToken("abc")).thenReturn(Optional.of(order));
        when(publicTokenService.isExpired(order.getPublicTokenExpiresAt())).thenReturn(false);

        com.printflow.dto.WorkOrderDTO dto = service.getWorkOrderByPublicToken("  abc  ");

        assertEquals(101L, dto.getId());
        verify(workOrderRepository).findWithClientAndCompanyByPublicToken("abc");
    }

    @Test
    void resolvePublicTokenFromOrderNumber_rejectsExpiredToken() {
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

        WorkOrder order = new WorkOrder();
        order.setOrderNumber("WO-900");
        order.setPublicToken("pub-900");
        order.setPublicTokenExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(workOrderRepository.findByOrderNumberIgnoreCase("wo-900")).thenReturn(Optional.of(order));
        when(publicTokenService.isExpired(order.getPublicTokenExpiresAt())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.resolvePublicTokenFromOrderNumber(" wo-900 "));
        assertEquals("Work order not found", ex.getMessage());
    }

    @Test
    void getWorkOrderEntityByPublicToken_rejectsBlankToken() {
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

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.getWorkOrderEntityByPublicToken("   "));
        assertEquals("Work order not found", ex.getMessage());
        verify(workOrderRepository, never()).findWithClientAndCompanyByPublicToken(anyString());
    }

    @Test
    void getWorkOrderByPublicToken_setsExpiryWhenMissing() {
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

        WorkOrder order = new WorkOrder();
        order.setId(120L);
        order.setTitle("Order");
        order.setPublicToken("token-120");
        order.setPublicTokenExpiresAt(null);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(2);

        when(workOrderRepository.findWithClientAndCompanyByPublicToken("token-120")).thenReturn(Optional.of(order));
        when(publicTokenService.expiresAtFromNow()).thenReturn(expiresAt);
        when(publicTokenService.isExpired(expiresAt)).thenReturn(false);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.getWorkOrderByPublicToken("token-120");

        assertEquals(expiresAt, order.getPublicTokenExpiresAt());
        verify(workOrderRepository).save(order);
    }

    @Test
    void getWorkOrderEntityByPublicToken_setsExpiryWhenMissing() {
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

        WorkOrder order = new WorkOrder();
        order.setId(121L);
        order.setPublicToken("token-121");
        order.setPublicTokenExpiresAt(null);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(2);

        when(workOrderRepository.findWithClientAndCompanyByPublicToken("token-121")).thenReturn(Optional.of(order));
        when(publicTokenService.expiresAtFromNow()).thenReturn(expiresAt);
        when(publicTokenService.isExpired(expiresAt)).thenReturn(false);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrder resolved = service.getWorkOrderEntityByPublicToken("token-121");

        assertEquals(121L, resolved.getId());
        assertEquals(expiresAt, resolved.getPublicTokenExpiresAt());
        verify(workOrderRepository).save(order);
    }

    @Test
    void getWorkOrderEntityByPublicToken_rejectsExpiredToken() {
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

        WorkOrder order = new WorkOrder();
        order.setId(122L);
        order.setPublicToken("token-122");
        order.setPublicTokenExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(workOrderRepository.findWithClientAndCompanyByPublicToken("token-122")).thenReturn(Optional.of(order));
        when(publicTokenService.isExpired(order.getPublicTokenExpiresAt())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.getWorkOrderEntityByPublicToken("token-122"));
        assertEquals("Work order not found", ex.getMessage());
    }

    @Test
    void resolvePublicTokenFromOrderNumber_setsExpiryWhenMissing() {
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

        WorkOrder order = new WorkOrder();
        order.setOrderNumber("WO-777");
        order.setPublicToken("pub-777");
        order.setPublicTokenExpiresAt(null);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);

        when(workOrderRepository.findByOrderNumberIgnoreCase("wo-777")).thenReturn(Optional.of(order));
        when(publicTokenService.expiresAtFromNow()).thenReturn(expiresAt);
        when(publicTokenService.isExpired(expiresAt)).thenReturn(false);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = service.resolvePublicTokenFromOrderNumber(" wo-777 ");

        assertEquals("pub-777", token);
        assertEquals(expiresAt, order.getPublicTokenExpiresAt());
        verify(workOrderRepository).save(order);
    }

    @Test
    void resolvePublicTokenFromOrderNumber_rejectsBlankInput() {
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

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.resolvePublicTokenFromOrderNumber("   "));
        assertEquals("Work order not found", ex.getMessage());
        verify(workOrderRepository, never()).findByOrderNumberIgnoreCase(anyString());
    }

    @Test
    void resolvePublicTokenFromOrderNumber_rejectsMissingPublicToken() {
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

        WorkOrder order = new WorkOrder();
        order.setOrderNumber("WO-888");
        order.setPublicToken(" ");
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        when(workOrderRepository.findByOrderNumberIgnoreCase("WO-888")).thenReturn(Optional.of(order));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.resolvePublicTokenFromOrderNumber("WO-888"));
        assertEquals("Work order not found", ex.getMessage());
    }
}
