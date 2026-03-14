package com.printflow.controller;

import com.printflow.service.OnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class OnboardingControllerTest {

    @Test
    void registerCompanyTrimsInputsBeforeServiceCall() {
        OnboardingService onboardingService = mock(OnboardingService.class);
        OnboardingController controller = new OnboardingController(onboardingService);
        Model model = new ExtendedModelMap();

        String view = controller.registerCompany(
            "  Acme  ", "  admin  ", "  Admin User  ", "  admin@acme.test  ", "  +381  ",
            "  secret123  ", "  secret123  ", model
        );

        assertEquals("auth/register", view);
        assertEquals("auth.register.success", model.getAttribute("successMessage"));
        verify(onboardingService).registerCompanyAndAdmin(
            "Acme", "admin", "Admin User", "admin@acme.test", "+381", "secret123"
        );
    }

    @Test
    void registerCompanyHandlesNullPasswordsWithoutNpe() {
        OnboardingService onboardingService = mock(OnboardingService.class);
        OnboardingController controller = new OnboardingController(onboardingService);
        Model model = new ExtendedModelMap();

        String view = controller.registerCompany(
            "Acme", "admin", "Admin User", "admin@acme.test", "+381",
            null, "secret123", model
        );

        assertEquals("auth/register", view);
        assertEquals("auth.password_mismatch", model.getAttribute("errorMessage"));
        verify(onboardingService, never()).registerCompanyAndAdmin(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void registerCompanyMapsKnownServiceErrorToI18nKey() {
        OnboardingService onboardingService = mock(OnboardingService.class);
        OnboardingController controller = new OnboardingController(onboardingService);
        Model model = new ExtendedModelMap();

        doThrow(new RuntimeException("Username already exists"))
            .when(onboardingService)
            .registerCompanyAndAdmin("Acme", "admin", "Admin User", "admin@acme.test", "+381", "secret123");

        String view = controller.registerCompany(
            "Acme", "admin", "Admin User", "admin@acme.test", "+381",
            "secret123", "secret123", model
        );

        assertEquals("auth/register", view);
        assertEquals("auth.register.error.username_exists", model.getAttribute("errorMessage"));
    }
}
