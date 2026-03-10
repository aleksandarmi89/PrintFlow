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
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Optional;

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
        FileStorageService fileStorageService = Mockito.mock(FileStorageService.class);
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
            fileStorageService,
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
}
