package com.printflow.service;

import com.printflow.dto.UserDTO;
import com.printflow.entity.User;
import com.printflow.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceAssignableUsersTest {

    @Test
    void getAssignableUsers_returnsAdminAndManagerForTenant() {
        UserRepository userRepository = mock(UserRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        com.printflow.repository.CompanyRepository companyRepository = mock(com.printflow.repository.CompanyRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);

        UserService service = new UserService(
            userRepository,
            passwordEncoder,
            tenantContextService,
            companyRepository,
            planLimitService,
            billingAccessService
        );

        User admin = new User();
        admin.setId(1L);
        admin.setFirstName("A");
        admin.setLastName("Admin");
        admin.setRole(User.Role.ADMIN);
        admin.setActive(true);

        User manager = new User();
        manager.setId(2L);
        manager.setFirstName("M");
        manager.setLastName("Manager");
        manager.setRole(User.Role.MANAGER);
        manager.setActive(true);

        when(tenantContextService.isSuperAdmin()).thenReturn(false);
        when(tenantContextService.requireCompanyId()).thenReturn(10L);
        when(userRepository.findByCompany_IdAndRoleInAndActiveTrue(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(List.of(admin, manager));

        List<UserDTO> result = service.getAssignableUsers();

        assertEquals(2, result.size());
        assertEquals("ADMIN", result.get(0).getRole());
        assertEquals("MANAGER", result.get(1).getRole());
    }

    @Test
    void getAssignableUsers_excludesSuperAdminFromPool() {
        UserRepository userRepository = mock(UserRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        com.printflow.repository.CompanyRepository companyRepository = mock(com.printflow.repository.CompanyRepository.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        BillingAccessService billingAccessService = mock(BillingAccessService.class);

        UserService service = new UserService(
            userRepository,
            passwordEncoder,
            tenantContextService,
            companyRepository,
            planLimitService,
            billingAccessService
        );

        User worker = new User();
        worker.setId(3L);
        worker.setFirstName("W");
        worker.setLastName("One");
        worker.setRole(User.Role.WORKER_GENERAL);
        worker.setActive(true);

        when(tenantContextService.isSuperAdmin()).thenReturn(true);
        when(userRepository.findByRoleInAndActiveTrue(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(List.of(worker));

        List<UserDTO> result = service.getAssignableUsers();

        assertEquals(1, result.size());
        assertEquals("WORKER_GENERAL", result.get(0).getRole());
    }
}
