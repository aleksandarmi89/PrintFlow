package com.printflow.service;

import com.printflow.dto.ClientDTO;
import com.printflow.entity.Client;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final WorkOrderRepository workOrderRepository;
    
    
    
    public ClientService(ClientRepository clientRepository, WorkOrderRepository workOrderRepository) {
		
		this.clientRepository = clientRepository;
		this.workOrderRepository = workOrderRepository;
	}

	public ClientDTO createClient(ClientDTO clientDTO) {
        // Provera za unique email
        if (clientDTO.getEmail() != null && !clientDTO.getEmail().isEmpty() && 
            clientRepository.existsByEmail(clientDTO.getEmail())) {
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
        client.setNotes(clientDTO.getNotes());
        client.setActive(true);
        
        Client savedClient = clientRepository.save(client);
        return convertToDTO(savedClient);
    }
    
    public ClientDTO updateClient(Long id, ClientDTO clientDTO) {
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Client not found"));
        
        // Provera za unique email
        if (clientDTO.getEmail() != null && !clientDTO.getEmail().isEmpty() && 
            !clientDTO.getEmail().equals(client.getEmail()) && 
            clientRepository.existsByEmail(clientDTO.getEmail())) {
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
    
    public void deleteClient(Long id) {
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Client not found"));
        
        // Soft delete
        client.setActive(false);
        clientRepository.save(client);
    }
    
    public ClientDTO getClientById(Long id) {
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Client not found"));
        return convertToDTOWithStats(client);
    }
    
    public List<ClientDTO> getAllClients() {
        return clientRepository.findAll().stream()
            .map(this::convertToDTOWithStats)
            .collect(Collectors.toList());
    }
    
    public List<ClientDTO> getActiveClients() {
        return clientRepository.findByActiveTrue().stream()
            .map(this::convertToDTOWithStats)
            .collect(Collectors.toList());
    }
    
    public List<ClientDTO> searchClients(String query) {
        return clientRepository.searchActive(query).stream()
            .map(this::convertToDTOWithStats)
            .collect(Collectors.toList());
    }
    
    public long getTotalActiveClients() {
        return clientRepository.countActiveClients();
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
        
        // Dodavanje statistike
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