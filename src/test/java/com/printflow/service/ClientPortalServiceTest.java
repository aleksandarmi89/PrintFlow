package com.printflow.service;

import com.printflow.entity.Attachment;
import com.printflow.entity.Company;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.AuditAction;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientPortalAccessRepository;
import com.printflow.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class ClientPortalServiceTest {

    @Test
    void approveAttachmentDoesNotOverwriteApprovalToken() {
        ClientPortalAccessRepository accessRepository = Mockito.mock(ClientPortalAccessRepository.class);
        WorkOrderRepository workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, attachmentRepository, auditLogService, notificationService);

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
        AttachmentRepository attachmentRepository = Mockito.mock(AttachmentRepository.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        ClientPortalService service = new ClientPortalService(
            accessRepository, workOrderRepository, attachmentRepository, auditLogService, notificationService);

        Attachment attachment = new Attachment();
        attachment.setApproved(true);

        assertThatThrownBy(() -> service.approveAttachment(attachment, "Client A", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already approved");
    }
}
