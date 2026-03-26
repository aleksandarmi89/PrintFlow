package com.printflow.service;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrderItem;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.PrintType;
import com.printflow.entity.enums.QuoteStatus;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.util.OrderNumberGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
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
    void updateQuoteStatus_setsDefaultValidityWhenSentWithoutDate() {
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
        company.setId(77L);
        WorkOrder order = new WorkOrder();
        order.setId(8L);
        order.setOrderNumber("WO-8");
        order.setTitle("Quote Order");
        order.setCompany(company);
        order.setQuoteStatus(QuoteStatus.READY);
        order.setQuoteValidUntil(null);
        order.setQuoteSentAt(null);

        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findWithRelationsById(8L)).thenReturn(Optional.of(order));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrderDTO dto = service.updateQuoteStatus(8L, QuoteStatus.SENT, null);

        assertNotNull(dto.getQuoteValidUntil());
        assertEquals(QuoteStatus.SENT, dto.getQuoteStatus());
        verify(auditLogService, atLeastOnce()).log(any(), eq("WorkOrder"), eq(8L), any(), any(), any(), eq(company));
    }

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
    void reorderWorkOrder_superAdminUsesSourceCompanyForItemCopy() {
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
        company.setId(42L);
        WorkOrder source = new WorkOrder();
        source.setId(10L);
        source.setCompany(company);
        source.setOrderNumber("WO-10");
        source.setTitle("Source");
        source.setStatus(OrderStatus.NEW);

        ProductVariant variant = new ProductVariant();
        variant.setId(77L);
        WorkOrderItem sourceItem = new WorkOrderItem();
        sourceItem.setVariant(variant);
        sourceItem.setQuantity(BigDecimal.valueOf(2));
        sourceItem.setCalculatedPrice(BigDecimal.valueOf(1500));
        sourceItem.setCalculatedCost(BigDecimal.valueOf(900));

        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findWithRelationsById(10L)).thenReturn(Optional.of(source));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-NEW");
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
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(10L, 42L))
            .thenReturn(List.of(sourceItem));

        WorkOrderDTO reordered = service.reorderWorkOrder(10L, null, "portal");

        assertNotNull(reordered);
        assertEquals(1500.0, reordered.getPrice());
        assertEquals(900.0, reordered.getCost());
        verify(workOrderItemRepository).findAllByWorkOrder_IdAndCompany_Id(10L, 42L);
        verify(tenantGuard, never()).requireCompanyId();
    }

    @Test
    void ensureTotalsSyncedForEntity_updatesFromItemsWithoutTenantContextLookup() {
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
        WorkOrder order = new WorkOrder();
        order.setId(5L);
        order.setCompany(company);
        order.setPrice(1.0);
        order.setCost(1.0);

        WorkOrderItem i1 = new WorkOrderItem();
        i1.setCalculatedPrice(new BigDecimal("100.00"));
        i1.setCalculatedCost(new BigDecimal("80.00"));
        WorkOrderItem i2 = new WorkOrderItem();
        i2.setCalculatedPrice(new BigDecimal("50.00"));
        i2.setCalculatedCost(new BigDecimal("20.00"));

        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(5L, 2L))
            .thenReturn(List.of(i1, i2));

        service.ensureTotalsSyncedForEntity(order);

        assertEquals(150.0, order.getPrice(), 0.0001);
        assertEquals(100.0, order.getCost(), 0.0001);
        verify(workOrderRepository).save(order);
        verify(tenantGuard, never()).requireCompanyId();
    }

    @Test
    void ensureTotalsSyncedForEntity_resetsTotalsToZeroWhenNoItems() {
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
        WorkOrder order = new WorkOrder();
        order.setId(5L);
        order.setCompany(company);
        order.setPrice(22.0);
        order.setCost(11.0);

        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(5L, 2L)).thenReturn(List.of());

        service.ensureTotalsSyncedForEntity(order);

        assertEquals(0.0, order.getPrice(), 0.0001);
        assertEquals(0.0, order.getCost(), 0.0001);
        verify(workOrderRepository).save(order);
        verify(tenantGuard, never()).requireCompanyId();
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
    void updateWorkOrder_prefersItemTotalsOverManualPriceCostWhenItemsExist() {
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
        order.setPrintType(PrintType.OTHER);
        order.setPrice(9999.0);
        order.setCost(8888.0);

        WorkOrderItem item1 = new WorkOrderItem();
        item1.setCalculatedPrice(new BigDecimal("100.00"));
        item1.setCalculatedCost(new BigDecimal("70.00"));
        WorkOrderItem item2 = new WorkOrderItem();
        item2.setCalculatedPrice(new BigDecimal("50.00"));
        item2.setCalculatedCost(new BigDecimal("30.00"));

        when(tenantGuard.requireCompanyId()).thenReturn(12L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(92L, 12L)).thenReturn(Optional.of(order));
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(92L, 12L))
            .thenReturn(List.of(item1, item2));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setTitle("Updated totals");
        dto.setPrintType(PrintType.OTHER);
        dto.setPrice(1.0);
        dto.setCost(2.0);

        service.updateWorkOrder(92L, dto);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        assertEquals(150.0, captor.getValue().getPrice());
        assertEquals(100.0, captor.getValue().getCost());
    }

    @Test
    void updateWorkOrder_setsDefaultQuoteValidityWhenStatusSentWithoutDate() {
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
        company.setId(18L);
        WorkOrder order = new WorkOrder();
        order.setId(93L);
        order.setCompany(company);
        order.setTitle("Quote Update");
        order.setPrintType(PrintType.OTHER);
        order.setQuoteStatus(QuoteStatus.READY);
        order.setQuoteValidUntil(null);
        order.setQuoteSentAt(null);

        when(tenantGuard.requireCompanyId()).thenReturn(18L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(93L, 18L)).thenReturn(Optional.of(order));
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(93L, 18L)).thenReturn(List.of());
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setTitle("Quote Update");
        dto.setPrintType(PrintType.OTHER);
        dto.setQuoteStatus(QuoteStatus.SENT);
        dto.setQuoteValidUntil(null);

        WorkOrderDTO result = service.updateWorkOrder(93L, dto);

        assertEquals(QuoteStatus.SENT, result.getQuoteStatus());
        assertNotNull(result.getQuoteValidUntil());
        assertNotNull(result.getQuoteSentAt());
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
    void updateWorkOrderStatus_setsShipmentStatusForCourier() {
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
        company.setId(21L);
        WorkOrder order = new WorkOrder();
        order.setId(121L);
        order.setCompany(company);
        order.setStatus(OrderStatus.READY_FOR_DELIVERY);
        order.setDeliveryType(WorkOrder.DeliveryType.COURIER);
        order.setCourierName("DHL");
        order.setTrackingNumber("TRK-121");
        order.setDeliveryAddress("Main street 1");
        order.setDeliveryRecipientName("Pera Peric");
        order.setDeliveryRecipientPhone("+38161111222");
        order.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(21L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(121L, 21L)).thenReturn(Optional.of(order));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantGuard.getCurrentUser()).thenReturn(null);

        WorkOrderDTO result = service.updateWorkOrderStatus(121L, OrderStatus.SENT, "sent");

        assertEquals(OrderStatus.SENT, result.getStatus());
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        assertEquals(WorkOrder.ShipmentStatus.SHIPPED, captor.getValue().getShipmentStatus());
        assertNotNull(captor.getValue().getShippedAt());
        verify(activityLogService).log(any(WorkOrder.class), eq("DELIVERY_SHIPPED"), eq("Order shipped"), isNull());
    }

    @Test
    void updateWorkOrderStatus_requiresTrackingBeforeCourierShip() {
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
        company.setId(22L);
        WorkOrder order = new WorkOrder();
        order.setId(1221L);
        order.setCompany(company);
        order.setStatus(OrderStatus.READY_FOR_DELIVERY);
        order.setDeliveryType(WorkOrder.DeliveryType.COURIER);
        order.setCourierName("DHL");
        order.setDeliveryAddress("Street 2");
        order.setDeliveryRecipientName("Mika Mikic");
        order.setDeliveryRecipientPhone("+38161122334");
        // intentionally no tracking number

        when(tenantGuard.requireCompanyId()).thenReturn(22L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(1221L, 22L)).thenReturn(Optional.of(order));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.updateWorkOrderStatus(1221L, OrderStatus.SENT, "ship"));
        assertEquals("Tracking number is required before marking courier order as shipped.", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void updateWorkOrderStatus_doesNotSetShipmentStatusForPickup() {
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
        company.setId(22L);
        WorkOrder order = new WorkOrder();
        order.setId(122L);
        order.setCompany(company);
        order.setStatus(OrderStatus.READY_FOR_DELIVERY);
        order.setDeliveryType(WorkOrder.DeliveryType.PICKUP);
        order.setPrintType(PrintType.OTHER);

        when(tenantGuard.requireCompanyId()).thenReturn(22L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(122L, 22L)).thenReturn(Optional.of(order));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantGuard.getCurrentUser()).thenReturn(null);

        WorkOrderDTO result = service.updateWorkOrderStatus(122L, OrderStatus.COMPLETED, "picked");

        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        assertEquals(WorkOrder.ShipmentStatus.PREPARING, captor.getValue().getShipmentStatus());
        assertNull(captor.getValue().getShippedAt());
        assertNull(captor.getValue().getDeliveredAt());
        verify(activityLogService).log(any(WorkOrder.class), eq("DELIVERY_PICKED_UP"), eq("Order picked up"), isNull());
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
    void createWorkOrder_rejectsNonAssignableAssignee() {
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
        User assignee = new User();
        assignee.setId(404L);
        assignee.setRole(User.Role.SUPER_ADMIN);

        when(tenantGuard.requireCompanyId()).thenReturn(6L);
        when(clientRepository.findByIdAndCompany_Id(77L, 6L)).thenReturn(Optional.of(client));
        when(userRepository.findByIdAndCompany_Id(404L, 6L)).thenReturn(Optional.of(assignee));
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
        assertEquals("Selected user cannot be assigned", ex.getMessage());
        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }

    @Test
    void assignWorker_rejectsWorkerAsAssigner() {
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

        User currentUser = new User();
        currentUser.setRole(User.Role.WORKER_GENERAL);
        when(tenantGuard.getCurrentUser()).thenReturn(currentUser);

        assertThrows(AccessDeniedException.class, () -> service.assignWorker(1L, 2L));
        verifyNoInteractions(workOrderRepository);
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
    void reorderWorkOrder_usesSourceCompanyForCreatorLookup() {
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

        Company sourceCompany = new Company();
        sourceCompany.setId(3L);
        WorkOrder source = new WorkOrder();
        source.setId(15L);
        source.setCompany(sourceCompany);
        source.setOrderNumber("WO-15");
        source.setTitle("Source");

        User creator = new User();
        creator.setId(500L);

        when(tenantGuard.requireCompanyId()).thenReturn(99L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(15L, 99L)).thenReturn(Optional.of(source));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-199");
        PublicTokenService.TokenInfo tokenInfo =
            new PublicTokenService.TokenInfo("tok", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        when(publicTokenService.newToken()).thenReturn(tokenInfo);
        when(userRepository.findByIdAndCompany_Id(500L, 3L)).thenReturn(Optional.of(creator));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> {
            WorkOrder wo = inv.getArgument(0);
            if (wo.getId() == null) {
                wo.setId(199L);
            }
            return wo;
        });
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(15L, 99L)).thenReturn(List.of());

        service.reorderWorkOrder(15L, 500L, "portal");

        verify(userRepository).findByIdAndCompany_Id(500L, 3L);
    }

    @Test
    void duplicateWorkOrder_usesSourceCompanyForCreatorLookup() {
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

        Company sourceCompany = new Company();
        sourceCompany.setId(4L);
        WorkOrder source = new WorkOrder();
        source.setId(16L);
        source.setCompany(sourceCompany);
        source.setOrderNumber("WO-16");
        source.setTitle("Source");
        source.setPrintType(PrintType.OTHER);

        User creator = new User();
        creator.setId(501L);

        when(tenantGuard.requireCompanyId()).thenReturn(88L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(16L, 88L)).thenReturn(Optional.of(source));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("WO-200");
        PublicTokenService.TokenInfo tokenInfo =
            new PublicTokenService.TokenInfo("tok", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        when(publicTokenService.newToken()).thenReturn(tokenInfo);
        when(userRepository.findByIdAndCompany_Id(501L, 4L)).thenReturn(Optional.of(creator));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> {
            WorkOrder wo = inv.getArgument(0);
            if (wo.getId() == null) {
                wo.setId(200L);
            }
            return wo;
        });
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(16L, 88L)).thenReturn(List.of());

        service.duplicateWorkOrder(16L, 501L, false);

        verify(userRepository).findByIdAndCompany_Id(501L, 4L);
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
        order.setPrice(0.0);
        order.setCost(0.0);
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

    @Test
    void getWorkOrderById_usesCalculatedItemTotalsWhenPresent() {
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
        order.setId(1L);
        order.setCompany(company);
        order.setPrice(1.0);
        order.setCost(1.0);

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.isAuthenticated()).thenReturn(true);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(1L, 10L)).thenReturn(Optional.of(order));
        java.util.List<Object[]> priceRows = new java.util.ArrayList<>();
        priceRows.add(new Object[]{1L, new BigDecimal("300.00")});
        java.util.List<Object[]> costRows = new java.util.ArrayList<>();
        costRows.add(new Object[]{1L, new BigDecimal("250.00")});
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(1L), 10L)).thenReturn(priceRows);
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(1L), 10L)).thenReturn(costRows);

        WorkOrderDTO dto = service.getWorkOrderById(1L);

        assertEquals(300.0, dto.getPrice());
        assertEquals(250.0, dto.getCost());
    }

    @Test
    void getWorkOrderById_syncsOrderEntityTotalsFromItemsWhenItemsExist() {
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
        order.setId(1L);
        order.setCompany(company);
        order.setPrice(999.0);
        order.setCost(888.0);

        WorkOrderItem item1 = new WorkOrderItem();
        item1.setCalculatedPrice(new BigDecimal("100.00"));
        item1.setCalculatedCost(new BigDecimal("40.00"));
        WorkOrderItem item2 = new WorkOrderItem();
        item2.setCalculatedPrice(new BigDecimal("50.00"));
        item2.setCalculatedCost(new BigDecimal("20.00"));

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.isAuthenticated()).thenReturn(true);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(1L, 10L)).thenReturn(Optional.of(order));
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(1L, 10L)).thenReturn(List.of(item1, item2));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(1L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(1L), 10L)).thenReturn(List.of());
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrderDTO dto = service.getWorkOrderById(1L);

        assertEquals(150.0, dto.getPrice());
        assertEquals(60.0, dto.getCost());
        verify(workOrderRepository, times(1)).save(order);
    }

    @Test
    void getWorkOrderById_doesNotSaveWhenSyncedTotalsAreUnchanged() {
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
        order.setId(1L);
        order.setCompany(company);
        order.setPrice(150.0);
        order.setCost(60.0);

        WorkOrderItem item1 = new WorkOrderItem();
        item1.setCalculatedPrice(new BigDecimal("100.00"));
        item1.setCalculatedCost(new BigDecimal("40.00"));
        WorkOrderItem item2 = new WorkOrderItem();
        item2.setCalculatedPrice(new BigDecimal("50.00"));
        item2.setCalculatedCost(new BigDecimal("20.00"));

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.isAuthenticated()).thenReturn(true);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findWithRelationsByIdAndCompany_Id(1L, 10L)).thenReturn(Optional.of(order));
        when(workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(1L, 10L)).thenReturn(List.of(item1, item2));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(1L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(1L), 10L)).thenReturn(List.of());

        WorkOrderDTO dto = service.getWorkOrderById(1L);

        assertEquals(150.0, dto.getPrice());
        assertEquals(60.0, dto.getCost());
        verify(workOrderRepository, never()).save(order);
    }

    @Test
    void getWorkOrdersByStatusPage_usesCalculatedItemTotalsWhenPresent() {
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
        order.setId(11L);
        order.setCompany(company);
        order.setStatus(OrderStatus.NEW);
        order.setPrice(1.0);
        order.setCost(1.0);

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.isAuthenticated()).thenReturn(true);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findByStatusAndCompany_Id(OrderStatus.NEW, 10L, pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));

        java.util.List<Object[]> priceRows = new java.util.ArrayList<>();
        priceRows.add(new Object[]{11L, new BigDecimal("315.00")});
        java.util.List<Object[]> costRows = new java.util.ArrayList<>();
        costRows.add(new Object[]{11L, new BigDecimal("197.95")});
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(11L), 10L)).thenReturn(priceRows);
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(11L), 10L)).thenReturn(costRows);

        org.springframework.data.domain.Page<WorkOrderDTO> page = service.getWorkOrdersByStatus(OrderStatus.NEW, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(315.0, page.getContent().get(0).getPrice());
        assertEquals(197.95, page.getContent().get(0).getCost());
    }

    @Test
    void getWorkOrdersByStatusPage_resetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(11L);
        order.setCompany(company);
        order.setStatus(OrderStatus.NEW);
        order.setPrice(315.0);
        order.setCost(197.95);

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.isAuthenticated()).thenReturn(true);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findByStatusAndCompany_Id(OrderStatus.NEW, 10L, pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(11L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(11L), 10L)).thenReturn(List.of());

        org.springframework.data.domain.Page<WorkOrderDTO> page = service.getWorkOrdersByStatus(OrderStatus.NEW, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(0.0, page.getContent().get(0).getPrice());
        assertEquals(0.0, page.getContent().get(0).getCost());
    }

    @Test
    void getWorkOrdersPage_resetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(31L);
        order.setCompany(company);
        order.setPrice(999.0);
        order.setCost(555.0);

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(tenantGuard.isAuthenticated()).thenReturn(true);
        when(workOrderRepository.findByCompany_IdOrderByCreatedAtDesc(10L, pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(31L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(31L), 10L)).thenReturn(List.of());

        org.springframework.data.domain.Page<WorkOrderDTO> page = service.getWorkOrders(pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(0.0, page.getContent().get(0).getPrice());
        assertEquals(0.0, page.getContent().get(0).getCost());
    }

    @Test
    void getRecentWorkOrders_resetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(41L);
        order.setCompany(company);
        order.setPrice(700.0);
        order.setCost(500.0);

        org.springframework.data.domain.PageRequest expectedPage =
            org.springframework.data.domain.PageRequest.of(0, 5, org.springframework.data.domain.Sort.by("createdAt").descending());

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findByCompany_IdOrderByCreatedAtDesc(eq(10L), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), expectedPage, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(41L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(41L), 10L)).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getRecentWorkOrders(5);

        verify(workOrderRepository).findByCompany_IdOrderByCreatedAtDesc(10L, expectedPage);
        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void getWorkOrdersByStatusList_resetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(51L);
        order.setCompany(company);
        order.setStatus(OrderStatus.NEW);
        order.setPrice(111.0);
        order.setCost(99.0);

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findByStatusAndCompany_Id(OrderStatus.NEW, 10L))
            .thenReturn(List.of(order));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(51L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(51L), 10L)).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getWorkOrdersByStatus(OrderStatus.NEW);

        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void getWorkOrdersPage_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(61L);
        order.setPrice(700.0);
        order.setCost(400.0);

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findByOrderByCreatedAtDesc(pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(61L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(61L))).thenReturn(List.of());

        org.springframework.data.domain.Page<WorkOrderDTO> page = service.getWorkOrders(pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(0.0, page.getContent().get(0).getPrice());
        assertEquals(0.0, page.getContent().get(0).getCost());
    }

    @Test
    void getRecentWorkOrders_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(71L);
        order.setPrice(910.0);
        order.setCost(610.0);

        org.springframework.data.domain.PageRequest expectedPage =
            org.springframework.data.domain.PageRequest.of(0, 5, org.springframework.data.domain.Sort.by("createdAt").descending());

        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findByOrderByCreatedAtDesc(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), expectedPage, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(71L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(71L))).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getRecentWorkOrders(5);

        verify(workOrderRepository).findByOrderByCreatedAtDesc(expectedPage);
        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void getWorkOrdersByStatusList_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(81L);
        order.setStatus(OrderStatus.NEW);
        order.setPrice(220.0);
        order.setCost(150.0);

        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findByStatus(OrderStatus.NEW)).thenReturn(List.of(order));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(81L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(81L))).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getWorkOrdersByStatus(OrderStatus.NEW);

        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void getWorkOrdersByStatusPage_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(91L);
        order.setStatus(OrderStatus.NEW);
        order.setPrice(333.0);
        order.setCost(222.0);

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findByStatus(OrderStatus.NEW, pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(91L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(91L))).thenReturn(List.of());

        org.springframework.data.domain.Page<WorkOrderDTO> page = service.getWorkOrdersByStatus(OrderStatus.NEW, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(0.0, page.getContent().get(0).getPrice());
        assertEquals(0.0, page.getContent().get(0).getCost());
    }

    @Test
    void searchWorkOrdersList_resetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(101L);
        order.setCompany(company);
        order.setPrice(515.0);
        order.setCost(315.0);

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.searchByCompany(10L, "flyer")).thenReturn(List.of(order));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(101L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(101L), 10L)).thenReturn(List.of());

        List<WorkOrderDTO> result = service.searchWorkOrders("flyer");

        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void searchWorkOrdersPage_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(111L);
        order.setPrice(820.0);
        order.setCost(540.0);

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.searchAll("banner", pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(111L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(111L))).thenReturn(List.of());

        org.springframework.data.domain.Page<WorkOrderDTO> result = service.searchWorkOrders("banner", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(0.0, result.getContent().get(0).getPrice());
        assertEquals(0.0, result.getContent().get(0).getCost());
    }

    @Test
    void getWorkOrdersByAssignedUserList_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setPrice(470.0);
        order.setCost(300.0);

        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findByAssignedToId(55L)).thenReturn(List.of(order));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(121L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(121L))).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getWorkOrdersByAssignedUser(55L);

        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void getWorkOrdersByAssignedUserPage_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(131L);
        order.setPrice(430.0);
        order.setCost(270.0);

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findByAssignedToId(55L, pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(131L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(131L))).thenReturn(List.of());

        org.springframework.data.domain.Page<WorkOrderDTO> result = service.getWorkOrdersByAssignedUser(55L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(0.0, result.getContent().get(0).getPrice());
        assertEquals(0.0, result.getContent().get(0).getCost());
    }

    @Test
    void getWorkOrdersByClient_resetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        Client client = new Client();
        client.setId(201L);
        client.setCompany(company);

        WorkOrder order = new WorkOrder();
        order.setId(141L);
        order.setCompany(company);
        order.setClient(client);
        order.setPrice(390.0);
        order.setCost(240.0);

        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(clientRepository.findByIdAndCompany_Id(201L, 10L)).thenReturn(Optional.of(client));
        when(workOrderRepository.findByClient(client)).thenReturn(List.of(order));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(141L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(141L), 10L)).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getWorkOrdersByClient(201L);

        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void getOverdueOrders_resetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(151L);
        order.setCompany(company);
        order.setPrice(640.0);
        order.setCost(390.0);

        when(tenantGuard.isSuperAdmin()).thenReturn(false);
        when(tenantGuard.requireCompanyId()).thenReturn(10L);
        when(workOrderRepository.findOverdueOrdersByCompany(eq(10L), any(LocalDateTime.class), anyList()))
            .thenReturn(List.of(order));
        when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(List.of(151L), 10L)).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(List.of(151L), 10L)).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getOverdueOrders();

        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void getOverdueOrdersWithLimit_superAdminResetsTotalsToZeroWhenNoItemAggregatesExist() {
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
        order.setId(161L);
        order.setPrice(910.0);
        order.setCost(650.0);

        when(tenantGuard.isSuperAdmin()).thenReturn(true);
        when(workOrderRepository.findOverdueOrders(any(LocalDateTime.class), anyList(), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(List.of(order));
        when(workOrderItemRepository.sumPriceByWorkOrderIds(List.of(161L))).thenReturn(List.of());
        when(workOrderItemRepository.sumCostByWorkOrderIds(List.of(161L))).thenReturn(List.of());

        List<WorkOrderDTO> result = service.getOverdueOrders(5);

        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getPrice());
        assertEquals(0.0, result.get(0).getCost());
    }

    @Test
    void statusFiltersReturnEmptyWhenStatusIsNull() {
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

        assertEquals(List.of(), service.getWorkOrdersByStatus(null));
        assertEquals(0L, service.countByStatus(null));
        assertEquals(0, service.getWorkOrdersByStatus(null, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
        verifyNoInteractions(workOrderRepository);
    }
}
