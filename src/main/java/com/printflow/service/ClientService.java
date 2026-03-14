package com.printflow.service;

import com.printflow.dto.ClientDTO;
import com.printflow.entity.Client;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TenantGuard tenantGuard;
    
    public ClientService(ClientRepository clientRepository, WorkOrderRepository workOrderRepository, TenantGuard tenantGuard) {
        this.clientRepository = clientRepository;
        this.workOrderRepository = workOrderRepository;
        this.tenantGuard = tenantGuard;
    }

    @Transactional // Write operation
    public ClientDTO createClient(ClientDTO clientDTO) {
        // Provera za unique email (u okviru kompanije)
        if (clientDTO.getEmail() != null && !clientDTO.getEmail().isEmpty() &&
            clientRepository.existsByEmailAndCompany_Id(clientDTO.getEmail(), tenantGuard.requireCompanyId())) {
            throw new RuntimeException("Email already exists");
        }
        
        // Provera za unique company ID
        if (clientDTO.getCompanyId() != null && !clientDTO.getCompanyId().isEmpty() && 
            clientRepository.existsByCompanyId(clientDTO.getCompanyId())) {
            throw new RuntimeException("Company ID already exists");
        }
        
        Client client = new Client();
        client.setCompanyName(clientDTO.getCompanyName());
        client.setContactPerson(clientDTO.getContactPerson());
        client.setPhone(clientDTO.getPhone());
        client.setEmail(clientDTO.getEmail());
        client.setAddress(clientDTO.getAddress());
        client.setCity(clientDTO.getCity());
        client.setCountry(clientDTO.getCountry() != null ? clientDTO.getCountry() : "Serbia");
        client.setTaxId(clientDTO.getTaxId());
        client.setCompanyId(clientDTO.getCompanyId());
        client.setCompany(tenantGuard.requireCompany());
        client.setNotes(clientDTO.getNotes());
        client.setActive(true);
        
        Client savedClient = clientRepository.save(client);
        return convertToDTO(savedClient);
    }
    
    @Transactional // Write operation
    public ClientDTO updateClient(Long id, ClientDTO clientDTO) {
        Client client = getClientOrThrow(id);
        Long clientCompanyId = client.getCompany() != null ? client.getCompany().getId() : tenantGuard.requireCompanyId();
        
        // Provera za unique email (u okviru kompanije)
        if (clientDTO.getEmail() != null && !clientDTO.getEmail().isEmpty() &&
            !clientDTO.getEmail().equals(client.getEmail()) &&
            clientRepository.existsByEmailAndCompany_Id(clientDTO.getEmail(), clientCompanyId)) {
            throw new RuntimeException("Email already exists");
        }
        
        // Provera za unique company ID
        if (clientDTO.getCompanyId() != null && !clientDTO.getCompanyId().isEmpty() && 
            !clientDTO.getCompanyId().equals(client.getCompanyId()) && 
            clientRepository.existsByCompanyId(clientDTO.getCompanyId())) {
            throw new RuntimeException("Company ID already exists");
        }
        
        client.setCompanyName(clientDTO.getCompanyName());
        client.setContactPerson(clientDTO.getContactPerson());
        client.setPhone(clientDTO.getPhone());
        client.setEmail(clientDTO.getEmail());
        client.setAddress(clientDTO.getAddress());
        client.setCity(clientDTO.getCity());
        client.setCountry(clientDTO.getCountry());
        client.setTaxId(clientDTO.getTaxId());
        client.setCompanyId(clientDTO.getCompanyId());
        client.setNotes(clientDTO.getNotes());
        client.setActive(clientDTO.isActive());
        
        Client updatedClient = clientRepository.save(client);
        return convertToDTO(updatedClient);
    }
    
    @Transactional // Write operation
    public void deleteClient(Long id) {
        Client client = getClientOrThrow(id);
        
        // Soft delete
        client.setActive(false);
        clientRepository.save(client);
    }
    
    @Transactional(readOnly = true) // READ-ONLY - OVO JE KLJUČNO
    public ClientDTO getClientById(Long id) {
        Client client = getClientOrThrow(id);
        return convertToDTOWithStats(client);
    }

    @Transactional(readOnly = true)
    public Client getClientEntity(Long id) {
        return getClientOrThrow(id);
    }
    
    @Transactional(readOnly = true) // READ-ONLY - OVO JE KLJUČNO
    public List<ClientDTO> getAllClients() {
        if (tenantGuard.isSuperAdmin()) {
            return clientRepository.findAll().stream()
                .map(this::convertToDTOWithStats)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return clientRepository.findByCompany_Id(companyId).stream()
            .map(this::convertToDTOWithStats)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true) // READ-ONLY - OVO JE KLJUČNO ZA CONNECTION LEAK
    public List<ClientDTO> getActiveClients() {
        if (tenantGuard.isSuperAdmin()) {
            return clientRepository.findByActiveTrue().stream()
                .map(this::convertToDTOWithStats)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return clientRepository.findByCompany_IdAndActiveTrue(companyId).stream()
            .map(this::convertToDTOWithStats)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ClientDTO> getActiveClients(Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            return clientRepository.findByActiveTrue(pageable)
                .map(this::convertToDTOWithStats);
        }
        Long companyId = tenantGuard.requireCompanyId();
        return clientRepository.findByCompany_IdAndActiveTrue(companyId, pageable)
            .map(this::convertToDTOWithStats);
    }
    
    @Transactional(readOnly = true) // READ-ONLY - OVO JE KLJUČNO
    public List<ClientDTO> searchClients(String query) {
        if (tenantGuard.isSuperAdmin()) {
            return clientRepository.searchActive(query).stream()
                .map(this::convertToDTOWithStats)
                .collect(Collectors.toList());
        }
        Long companyId = tenantGuard.requireCompanyId();
        return clientRepository.searchActiveByCompany(companyId, query).stream()
            .map(this::convertToDTOWithStats)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ClientDTO> searchClients(String query, Pageable pageable) {
        if (tenantGuard.isSuperAdmin()) {
            return clientRepository.searchActive(query, pageable)
                .map(this::convertToDTOWithStats);
        }
        Long companyId = tenantGuard.requireCompanyId();
        return clientRepository.searchActiveByCompany(companyId, query, pageable)
            .map(this::convertToDTOWithStats);
    }
    
    @Transactional(readOnly = true) // READ-ONLY - OVO JE KLJUČNO
    public long getTotalActiveClients() {
        if (tenantGuard.isSuperAdmin()) {
            return clientRepository.countActiveClients();
        }
        return clientRepository.countActiveClientsByCompany(tenantGuard.requireCompanyId());
    }

    private Client getClientOrThrow(Long id) {
        if (tenantGuard.isSuperAdmin()) {
            return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        }
        return clientRepository.findByIdAndCompany_Id(id, tenantGuard.requireCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }
    
    private ClientDTO convertToDTO(Client client) {
        return new ClientDTO(
            client.getId(),
            client.getCompanyName(),
            client.getContactPerson(),
            client.getPhone(),
            client.getEmail(),
            client.getAddress(),
            client.getCity(),
            client.getCountry(),
            client.getTaxId(),
            client.getCompanyId(),
            client.getNotes(),
            client.isActive(), 0, 0, 0
        );
    }
    
    private ClientDTO convertToDTOWithStats(Client client) {
        ClientDTO dto = convertToDTO(client);
        
        // Dodavanje statistike - OVO JE U TRANSACTION SADA
        List<com.printflow.entity.WorkOrder> orders = workOrderRepository.findByClient(client);
        
        dto.setTotalOrders(orders.size());
        dto.setCompletedOrders(orders.stream()
            .filter(o -> o.getStatus() == com.printflow.entity.enums.OrderStatus.COMPLETED)
            .count());
        dto.setPendingOrders(orders.stream()
            .filter(o -> o.getStatus() != com.printflow.entity.enums.OrderStatus.COMPLETED && 
                        o.getStatus() != com.printflow.entity.enums.OrderStatus.CANCELLED)
            .count());
        
        return dto;
    }
}
