package com.printflow.service;

import com.printflow.dto.CompanyDTO;
import com.printflow.entity.Company;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyServiceTest {

    @Test
    void createCompanyNormalizesBlankContactFieldsToNull() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        com.printflow.storage.FileStorage fileStorage = mock(com.printflow.storage.FileStorage.class);
        TemplateSeederService templateSeederService = mock(TemplateSeederService.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(companyRepository.existsByNameIgnoreCase("Gamma")).thenReturn(false);
        when(companyRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        CompanyService service = new CompanyService(
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            fileStorage,
            14,
            templateSeederService,
            notificationService
        );

        CompanyDTO dto = new CompanyDTO();
        dto.setName("Gamma");
        dto.setEmail("   ");
        dto.setPhone(" ");
        dto.setAddress("   ");
        dto.setWebsite(" ");
        dto.setPrimaryColor(" ");
        dto.setActive(true);

        CompanyDTO created = service.createCompany(dto);

        assertNull(created.getEmail());
        assertNull(created.getPhone());
        assertNull(created.getAddress());
        assertNull(created.getWebsite());
        assertNull(created.getPrimaryColor());
    }

    @Test
    void createCompanyRejectsNameCollisionIgnoringCase() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        com.printflow.storage.FileStorage fileStorage = mock(com.printflow.storage.FileStorage.class);
        TemplateSeederService templateSeederService = mock(TemplateSeederService.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(companyRepository.existsByNameIgnoreCase("Acme")).thenReturn(true);

        CompanyService service = new CompanyService(
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            fileStorage,
            14,
            templateSeederService,
            notificationService
        );

        CompanyDTO dto = new CompanyDTO();
        dto.setName("Acme");
        dto.setActive(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createCompany(dto));
        assertEquals("Company name already exists", ex.getMessage());
    }

    @Test
    void updateCompanyClearsBillingOverrideWhenDisabledInDto() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        com.printflow.storage.FileStorage fileStorage = mock(com.printflow.storage.FileStorage.class);
        TemplateSeederService templateSeederService = mock(TemplateSeederService.class);
        NotificationService notificationService = mock(NotificationService.class);

        Company existing = new Company();
        existing.setId(12L);
        existing.setName("Acme");
        existing.setSlug("acme");
        existing.setBillingOverrideActive(true);
        existing.setBillingOverrideUntil(LocalDateTime.now().plusDays(7));

        when(companyRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findBySlug(any())).thenReturn(Optional.empty());

        CompanyService service = new CompanyService(
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            fileStorage,
            14,
            templateSeederService,
            notificationService
        );

        CompanyDTO dto = new CompanyDTO();
        dto.setName("Acme");
        dto.setActive(true);
        dto.setBillingOverrideActive(false);

        CompanyDTO updated = service.updateCompany(12L, dto);

        assertFalse(updated.isBillingOverrideActive());
        assertNull(updated.getBillingOverrideUntil());
    }

    @Test
    void updateCompanyKeepsBillingOverrideWhenEnabledInDto() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        com.printflow.storage.FileStorage fileStorage = mock(com.printflow.storage.FileStorage.class);
        TemplateSeederService templateSeederService = mock(TemplateSeederService.class);
        NotificationService notificationService = mock(NotificationService.class);

        Company existing = new Company();
        existing.setId(13L);
        existing.setName("Beta");
        existing.setSlug("beta");
        existing.setBillingOverrideActive(false);
        existing.setBillingOverrideUntil(null);

        when(companyRepository.findById(13L)).thenReturn(Optional.of(existing));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findBySlug(any())).thenReturn(Optional.empty());

        CompanyService service = new CompanyService(
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            fileStorage,
            14,
            templateSeederService,
            notificationService
        );

        LocalDateTime until = LocalDateTime.now().plusDays(5);
        CompanyDTO dto = new CompanyDTO();
        dto.setName("Beta");
        dto.setActive(true);
        dto.setBillingOverrideActive(true);
        dto.setBillingOverrideUntil(until);

        CompanyDTO updated = service.updateCompany(13L, dto);

        assertTrue(updated.isBillingOverrideActive());
        assertEquals(until, updated.getBillingOverrideUntil());
    }

    @Test
    void updateCompanyRejectsNameCollisionIgnoringCase() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        com.printflow.storage.FileStorage fileStorage = mock(com.printflow.storage.FileStorage.class);
        TemplateSeederService templateSeederService = mock(TemplateSeederService.class);
        NotificationService notificationService = mock(NotificationService.class);

        Company existing = new Company();
        existing.setId(16L);
        existing.setName("Alpha");
        existing.setSlug("alpha");
        when(companyRepository.findById(16L)).thenReturn(Optional.of(existing));
        when(companyRepository.existsByNameIgnoreCase("Beta")).thenReturn(true);

        CompanyService service = new CompanyService(
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            fileStorage,
            14,
            templateSeederService,
            notificationService
        );

        CompanyDTO dto = new CompanyDTO();
        dto.setName("Beta");
        dto.setActive(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateCompany(16L, dto));
        assertEquals("Company name already exists", ex.getMessage());
    }

    @Test
    void updateCompanyNormalizesBlankContactFieldsToNull() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        com.printflow.storage.FileStorage fileStorage = mock(com.printflow.storage.FileStorage.class);
        TemplateSeederService templateSeederService = mock(TemplateSeederService.class);
        NotificationService notificationService = mock(NotificationService.class);

        Company existing = new Company();
        existing.setId(14L);
        existing.setName("Delta");
        existing.setSlug("delta");
        existing.setEmail("old@example.com");
        existing.setPhone("123");
        existing.setAddress("old address");
        existing.setWebsite("https://old.example.com");
        existing.setPrimaryColor("#000000");

        when(companyRepository.findById(14L)).thenReturn(Optional.of(existing));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findBySlug(any())).thenReturn(Optional.empty());

        CompanyService service = new CompanyService(
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            fileStorage,
            14,
            templateSeederService,
            notificationService
        );

        CompanyDTO dto = new CompanyDTO();
        dto.setName("Delta");
        dto.setEmail(" ");
        dto.setPhone(" ");
        dto.setAddress(" ");
        dto.setWebsite(" ");
        dto.setPrimaryColor(" ");
        dto.setActive(true);

        CompanyDTO updated = service.updateCompany(14L, dto);

        assertNull(updated.getEmail());
        assertNull(updated.getPhone());
        assertNull(updated.getAddress());
        assertNull(updated.getWebsite());
        assertNull(updated.getPrimaryColor());
    }

    @Test
    void updateCompanyClearsSmtpHostPortUserAndKeepsPasswordWhenBlankProvided() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        com.printflow.storage.FileStorage fileStorage = mock(com.printflow.storage.FileStorage.class);
        TemplateSeederService templateSeederService = mock(TemplateSeederService.class);
        NotificationService notificationService = mock(NotificationService.class);

        Company existing = new Company();
        existing.setId(15L);
        existing.setName("Epsilon");
        existing.setSlug("epsilon");
        existing.setSmtpHost("smtp.old.local");
        existing.setSmtpPort(587);
        existing.setSmtpUser("old-user");
        existing.setSmtpPassword("secret-old");

        when(companyRepository.findById(15L)).thenReturn(Optional.of(existing));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findBySlug(any())).thenReturn(Optional.empty());

        CompanyService service = new CompanyService(
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            fileStorage,
            14,
            templateSeederService,
            notificationService
        );

        CompanyDTO dto = new CompanyDTO();
        dto.setName("Epsilon");
        dto.setActive(true);
        dto.setSmtpHost("  ");
        dto.setSmtpPort(null);
        dto.setSmtpUser(" ");
        dto.setSmtpPassword(" ");

        CompanyDTO updated = service.updateCompany(15L, dto);

        assertNull(updated.getSmtpHost());
        assertNull(updated.getSmtpPort());
        assertNull(updated.getSmtpUser());
        // Password should not be overwritten by blank value.
        assertEquals("secret-old", existing.getSmtpPassword());
    }
}
