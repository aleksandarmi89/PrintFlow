package com.printflow.service;

import com.printflow.entity.Attachment;
import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.ClientPortalAccess;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.AuditAction;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientPortalAccessRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientPortalServiceTest {

    @Test
    void getAccessOrThrowNormalizesWhitespaceInToken() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, workOrderItemRepository, attachmentRepository, auditLogService, notificationService);

        ClientPortalAccess access = new ClientPortalAccess();
        access.setAccessToken("abcd-1234");
        access.setClient(new Client());
        access.setCompany(new Company());

        when(accessRepository.findByAccessTokenWithClientAndCompany("abcd-1234"))
            .thenReturn(java.util.Optional.of(access));
        when(accessRepository.save(Mockito.any(ClientPortalAccess.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ClientPortalAccess resolved = service.getAccessOrThrow("  abcd-\n1234  ");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getLastAccessedAt()).isNotNull();
        verify(accessRepository).findByAccessTokenWithClientAndCompany("abcd-1234");
    }

    @Test
    void approveAttachmentDoesNotOverwriteApprovalToken() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, workOrderItemRepository, attachmentRepository, auditLogService, notificationService);

        Company company = new Company();
        company.setId(10L);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(20L);
        workOrder.setCompany(company);

        Attachment attachment = new Attachment();
        attachment.setId(30L);
        attachment.setWorkOrder(workOrder);
        attachment.setApprovalToken("ATTACHMENT_TOKEN_123");

        service.approveAttachment(attachment, "Client A", "127.0.0.1");

        assertThat(attachment.isApproved()).isTrue();
        assertThat(attachment.getApprovedBy()).isEqualTo("Client A");
        assertThat(attachment.getApprovalIp()).isEqualTo("127.0.0.1");
        assertThat(attachment.getApprovalToken()).isEqualTo("ATTACHMENT_TOKEN_123");
        verify(attachmentRepository).save(attachment);
        verify(auditLogService).log(
            Mockito.eq(AuditAction.APPROVE),
            Mockito.eq("Attachment"),
            Mockito.eq(attachment.getId()),
            Mockito.isNull(),
            Mockito.eq("approved"),
            Mockito.eq("Design approved via portal"),
            Mockito.eq(company)
        );
        verify(notificationService).notifyClientDesignApproved(workOrder);
    }

    @Test
    void approveAttachmentRejectsAlreadyApproved() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, workOrderItemRepository, attachmentRepository, auditLogService, notificationService);

        Attachment attachment = new Attachment();
        attachment.setApproved(true);

        assertThatThrownBy(() -> service.approveAttachment(attachment, "Client A", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already approved");
    }

    @Test
    void getRecentOrdersForClientAppliesItemTotalsWhenAvailable() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, workOrderItemRepository, attachmentRepository, auditLogService, notificationService);

        com.printflow.entity.Client client = new com.printflow.entity.Client();
        client.setId(1L);
        Company company = new Company();
        company.setId(2L);

        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setCompany(company);
        order.setPrice(1.0);
        order.setCost(1.0);

        Mockito.when(workOrderRepository.findByClient_IdAndCompany_Id(1L, 2L)).thenReturn(java.util.List.of(order));
        java.util.List<Object[]> priceRows = new java.util.ArrayList<>();
        priceRows.add(new Object[]{100L, 315.0});
        java.util.List<Object[]> costRows = new java.util.ArrayList<>();
        costRows.add(new Object[]{100L, 197.95});
        Mockito.when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(priceRows);
        Mockito.when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(costRows);
        java.util.List<Object[]> countRows = new java.util.ArrayList<>();
        countRows.add(new Object[]{100L, 1L});
        Mockito.when(workOrderItemRepository.countItemsByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(countRows);

        java.util.List<WorkOrder> recent = service.getRecentOrdersForClient(client, company, 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getPrice()).isEqualTo(315.0);
        assertThat(recent.get(0).getCost()).isEqualTo(197.95);
        verify(workOrderRepository).saveAll(Mockito.argThat(iterable -> {
            if (iterable == null) {
                return false;
            }
            java.util.Iterator<WorkOrder> it = iterable.iterator();
            if (!it.hasNext()) {
                return false;
            }
            it.next();
            return !it.hasNext();
        }));
    }

    @Test
    void getRecentOrdersForClientDoesNotPersistWhenTotalsUnchanged() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, workOrderItemRepository, attachmentRepository, auditLogService, notificationService);

        com.printflow.entity.Client client = new com.printflow.entity.Client();
        client.setId(1L);
        Company company = new Company();
        company.setId(2L);

        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setCompany(company);
        order.setPrice(315.0);
        order.setCost(197.95);

        Mockito.when(workOrderRepository.findByClient_IdAndCompany_Id(1L, 2L)).thenReturn(java.util.List.of(order));
        java.util.List<Object[]> priceRows = new java.util.ArrayList<>();
        priceRows.add(new Object[]{100L, 315.0});
        java.util.List<Object[]> costRows = new java.util.ArrayList<>();
        costRows.add(new Object[]{100L, 197.95});
        Mockito.when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(priceRows);
        Mockito.when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(costRows);
        java.util.List<Object[]> countRows = new java.util.ArrayList<>();
        countRows.add(new Object[]{100L, 1L});
        Mockito.when(workOrderItemRepository.countItemsByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(countRows);

        java.util.List<WorkOrder> recent = service.getRecentOrdersForClient(client, company, 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getPrice()).isEqualTo(315.0);
        assertThat(recent.get(0).getCost()).isEqualTo(197.95);
        verify(workOrderRepository, Mockito.never()).saveAll(Mockito.anyList());
    }

    @Test
    void getRecentOrdersForClientResetsTotalsWhenAggregatesAreMalformed() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, workOrderItemRepository, attachmentRepository, auditLogService, notificationService);

        com.printflow.entity.Client client = new com.printflow.entity.Client();
        client.setId(1L);
        Company company = new Company();
        company.setId(2L);

        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setCompany(company);
        order.setPrice(100.0);
        order.setCost(50.0);

        Mockito.when(workOrderRepository.findByClient_IdAndCompany_Id(1L, 2L)).thenReturn(java.util.List.of(order));
        java.util.List<Object[]> badPriceRows = new java.util.ArrayList<>();
        badPriceRows.add(new Object[]{"oops", "bad"});
        java.util.List<Object[]> badCostRows = new java.util.ArrayList<>();
        badCostRows.add(new Object[]{null, 10.0});
        Mockito.when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(badPriceRows);
        Mockito.when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(badCostRows);
        java.util.List<Object[]> countRows = new java.util.ArrayList<>();
        countRows.add(new Object[]{100L, 1L});
        Mockito.when(workOrderItemRepository.countItemsByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(countRows);

        assertDoesNotThrow(() -> service.getRecentOrdersForClient(client, company, 10));
        assertThat(order.getPrice()).isEqualTo(0.0);
        assertThat(order.getCost()).isEqualTo(0.0);
        verify(workOrderRepository).saveAll(Mockito.argThat(iterable -> {
            if (iterable == null) {
                return false;
            }
            java.util.Iterator<WorkOrder> it = iterable.iterator();
            if (!it.hasNext()) {
                return false;
            }
            it.next();
            return !it.hasNext();
        }));
    }

    @Test
    void getRecentOrdersForClientKeepsManualTotalsWhenNoItemAggregates() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        WorkOrderItemRepository workOrderItemRepository = Mockito.mock(WorkOrderItemRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, workOrderItemRepository, attachmentRepository, auditLogService, notificationService);

        com.printflow.entity.Client client = new com.printflow.entity.Client();
        client.setId(1L);
        Company company = new Company();
        company.setId(2L);

        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setCompany(company);
        order.setPrice(315.0);
        order.setCost(197.95);

        Mockito.when(workOrderRepository.findByClient_IdAndCompany_Id(1L, 2L)).thenReturn(java.util.List.of(order));
        Mockito.when(workOrderItemRepository.sumPriceByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(java.util.List.of());
        Mockito.when(workOrderItemRepository.sumCostByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(java.util.List.of());
        Mockito.when(workOrderItemRepository.countItemsByWorkOrderIdsAndCompanyId(java.util.List.of(100L), 2L))
            .thenReturn(java.util.List.of());

        java.util.List<WorkOrder> recent = service.getRecentOrdersForClient(client, company, 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getPrice()).isEqualTo(315.0);
        assertThat(recent.get(0).getCost()).isEqualTo(197.95);
        verify(workOrderRepository, Mockito.never()).saveAll(Mockito.anyList());
    }
}
