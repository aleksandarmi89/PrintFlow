package com.printflow.controller;

import com.printflow.service.InviteService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteControllerTest {

    @Test
    void acceptInviteFormMapsKnownServiceErrorToI18nKey() {
        InviteService inviteService = mock(InviteService.class);
        InviteController controller = new InviteController(inviteService);
        Model model = new ExtendedModelMap();

        when(inviteService.getValidInvite("bad-token"))
            .thenThrow(new RuntimeException("Invitation expired"));

        String view = controller.acceptInviteForm("bad-token", model);

        assertEquals("auth/accept-invite", view);
        assertEquals("auth.invite.error.expired", model.getAttribute("errorMessage"));
    }

    @Test
    void acceptInviteTrimsInputsBeforeServiceCall() {
        InviteService inviteService = mock(InviteService.class);
        InviteController controller = new InviteController(inviteService);
        Model model = new ExtendedModelMap();

        String view = controller.acceptInvite(
            "token-1",
            "  user1  ",
            "  User One  ",
            "  secret123  ",
            "  secret123  ",
            model
        );

        assertEquals("auth/accept-invite", view);
        assertEquals("auth.invite.success", model.getAttribute("successMessage"));
        verify(inviteService).acceptInvite("token-1", "user1", "User One", "secret123");
    }

    @Test
    void acceptInviteHandlesNullPasswordsWithoutNpe() {
        InviteService inviteService = mock(InviteService.class);
        InviteController controller = new InviteController(inviteService);
        Model model = new ExtendedModelMap();

        String view = controller.acceptInvite(
            "token-1",
            "user1",
            "User One",
            null,
            "secret123",
            model
        );

        assertEquals("auth/accept-invite", view);
        assertEquals("auth.password_mismatch", model.getAttribute("errorMessage"));
        verify(inviteService, never())
            .acceptInvite(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptInviteMapsKnownServiceErrorToI18nKey() {
        InviteService inviteService = mock(InviteService.class);
        InviteController controller = new InviteController(inviteService);
        Model model = new ExtendedModelMap();

        doThrow(new RuntimeException("Invitation expired"))
            .when(inviteService)
            .acceptInvite("token-1", "user1", "User One", "secret123");

        String view = controller.acceptInvite(
            "token-1",
            "user1",
            "User One",
            "secret123",
            "secret123",
            model
        );

        assertEquals("auth/accept-invite", view);
        assertEquals("auth.invite.error.expired", model.getAttribute("errorMessage"));
    }
}
