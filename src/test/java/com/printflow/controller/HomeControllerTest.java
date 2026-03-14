package com.printflow.controller;

import com.printflow.entity.User;
import com.printflow.service.CurrentContextService;
import com.printflow.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeControllerTest {

    @Test
    void updateProfileTrimsOptionalFieldsBeforeServiceCall() {
        CurrentContextService currentContextService = mock(CurrentContextService.class);
        UserService userService = mock(UserService.class);
        HomeController controller = new HomeController(currentContextService, userService);

        User user = new User();
        user.setId(41L);
        when(currentContextService.currentUser()).thenReturn(user);
        Model model = new ExtendedModelMap();

        String view = controller.updateProfile(
            "  Ana  ", "  Admin  ", "  ana@example.com  ", "  +381  ",
            "  Ops  ", "  Lead  ", "  Note  ", model
        );

        assertEquals("redirect:/profile", view);
        verify(userService).updateProfile(
            41L, "Ana", "Admin", "ana@example.com", "+381", "Ops", "Lead", "Note"
        );
    }

    @Test
    void changePasswordHandlesNullInputsWithoutNpe() {
        CurrentContextService currentContextService = mock(CurrentContextService.class);
        UserService userService = mock(UserService.class);
        HomeController controller = new HomeController(currentContextService, userService);
        Model model = new ExtendedModelMap();

        String view = controller.changePassword("old", null, null, model);

        assertEquals("redirect:/settings", view);
        verify(userService, never()).changePassword(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changePasswordTrimsValuesBeforeServiceCall() {
        CurrentContextService currentContextService = mock(CurrentContextService.class);
        UserService userService = mock(UserService.class);
        HomeController controller = new HomeController(currentContextService, userService);

        User user = new User();
        user.setId(42L);
        when(currentContextService.currentUser()).thenReturn(user);
        when(userService.changePassword(42L, "old-pass", "new-pass")).thenReturn(true);
        Model model = new ExtendedModelMap();

        String view = controller.changePassword("  old-pass  ", "  new-pass  ", "  new-pass  ", model);

        assertEquals("redirect:/settings", view);
        verify(userService).changePassword(42L, "old-pass", "new-pass");
    }
}
