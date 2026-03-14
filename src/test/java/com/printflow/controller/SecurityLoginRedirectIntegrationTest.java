package com.printflow.controller;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class SecurityLoginRedirectIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.findByName("Redirect Tenant")
            .orElseGet(() -> {
                Company c = new Company();
                c.setName("Redirect Tenant");
                c.setActive(true);
                return companyRepository.save(c);
            });
        company.setActive(true);
        companyRepository.save(company);

        ensureUser("redirect_admin", User.Role.ADMIN, company);
        ensureUser("redirect_manager", User.Role.MANAGER, company);
        ensureUser("redirect_super_admin", User.Role.SUPER_ADMIN, company);
        ensureUser("redirect_worker", User.Role.WORKER_GENERAL, company);
    }

    @Test
    void adminRedirectsToAdminDashboard() throws Exception {
        mockMvc.perform(formLogin("/login").user("redirect_admin").password("password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void managerRedirectsToAdminDashboard() throws Exception {
        mockMvc.perform(formLogin("/login").user("redirect_manager").password("password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void superAdminRedirectsToCompanies() throws Exception {
        mockMvc.perform(formLogin("/login").user("redirect_super_admin").password("password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/companies"));
    }

    @Test
    void workerRedirectsToWorkerDashboard() throws Exception {
        mockMvc.perform(formLogin("/login").user("redirect_worker").password("password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/worker/dashboard"));
    }

    private void ensureUser(String username, User.Role role, Company company) {
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(role);
        user.setCompany(company);
        user.setActive(true);
        user.setFirstName("Redirect");
        user.setLastName("User");
        user.setFullName("Redirect User");
        userRepository.save(user);
    }
}
