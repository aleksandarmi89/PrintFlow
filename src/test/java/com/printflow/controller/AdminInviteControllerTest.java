package com.printflow.controller;

import com.printflow.entity.User.Role;
import com.printflow.service.InviteService;
import com.printflow.service.TenantContextService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminInviteControllerTest {

    @Test
    void createInviteAcceptsLowercaseRole() {
        InviteService inviteService = mock(InviteService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(inviteService.createInvite("user@example.com", Role.WORKER_PRINT))
            .thenReturn("https://example.test/invite/token");

        AdminInviteController controller = new AdminInviteController(inviteService, tenantContextService);
        Model model = new ExtendedModelMap();

        String view = controller.createInvite("user@example.com", "worker_print", model);

        assertEquals("redirect:/admin/users/invite?inviteLink=https%3A%2F%2Fexample.test%2Finvite%2Ftoken", view);
        verify(inviteService).createInvite("user@example.com", Role.WORKER_PRINT);
    }

    @Test
    void createInviteRejectsInvalidRoleWithoutCallingService() {
        InviteService inviteService = mock(InviteService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.isSuperAdmin()).thenReturn(false);

        AdminInviteController controller = new AdminInviteController(inviteService, tenantContextService);
        Model model = new ExtendedModelMap();

        String view = controller.createInvite("user@example.com", "not-a-role", model);

        assertEquals("admin/users/invite", view);
        assertEquals("admin.users.invite.invalid_role", model.getAttribute("errorMessage"));
        verifyNoInteractions(inviteService);
    }
}
