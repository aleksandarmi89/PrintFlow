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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyServiceTest {

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
}
