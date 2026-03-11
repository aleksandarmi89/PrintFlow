package com.printflow.testsupport;

import com.printflow.entity.Attachment;
import com.printflow.entity.Client;
import com.printflow.entity.Company;
import com.printflow.entity.Task;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TenantTestFixture {

    public record TenantIds(Long company1Id, Long company2Id, Long clientId, Long workOrderId, Long taskId, Long attachmentId) {}

    private final MockMvc mockMvc;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TaskRepository taskRepository;
    private final AttachmentRepository attachmentRepository;
    private final PasswordEncoder passwordEncoder;

    private Path tempFile;

    public TenantTestFixture(MockMvc mockMvc,
                             CompanyRepository companyRepository,
                             UserRepository userRepository,
                             ClientRepository clientRepository,
                             WorkOrderRepository workOrderRepository,
                             TaskRepository taskRepository,
                             AttachmentRepository attachmentRepository,
                             PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.workOrderRepository = workOrderRepository;
        this.taskRepository = taskRepository;
        this.attachmentRepository = attachmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public TenantIds createTenantData() throws Exception {
        Company tenant1 = getOrCreateCompany("Tenant One");
        Company tenant2 = getOrCreateCompany("Tenant Two");

        getOrCreateUser("tenant1_admin", tenant1, Role.ADMIN);
        getOrCreateUser("tenant2_admin", tenant2, Role.ADMIN);

        Client client = getOrCreateClient("client1@example.com", tenant1, "Client One");

        WorkOrder workOrder = getOrCreateWorkOrder("WO-1000", tenant1, client);

        Task task = getOrCreateTask(workOrder, tenant1, "Test Task");

        Attachment attachment = getOrCreateAttachment(workOrder, tenant1);

        return new TenantIds(tenant1.getId(), tenant2.getId(), client.getId(), workOrder.getId(), task.getId(), attachment.getId());
    }

    private Company getOrCreateCompany(String name) {
        return companyRepository.findByName(name)
            .orElseGet(() -> {
                Company company = new Company();
                company.setName(name);
                return companyRepository.save(company);
            });
    }

    private User getOrCreateUser(String username, Company company, Role role) {
        return userRepository.findByUsername(username)
            .map(existing -> {
                existing.setCompany(company);
                existing.setRole(role);
                existing.setPassword(passwordEncoder.encode("password"));
                ensureFullName(existing, "Test", "User");
                return userRepository.save(existing);
            })
            .orElseGet(() -> {
                User user = new User();
                user.setUsername(username);
                user.setPassword(passwordEncoder.encode("password"));
                user.setRole(role);
                user.setCompany(company);
                ensureFullName(user, "Test", "User");
                return userRepository.save(user);
            });
    }

    private void ensureFullName(User user, String fallbackFirst, String fallbackLast) {
        String first = user.getFirstName() == null ? fallbackFirst : user.getFirstName();
        String last = user.getLastName() == null ? fallbackLast : user.getLastName();
        user.setFirstName(first);
        user.setLastName(last);
        String full = buildFullName(first, last);
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(full);
        }
    }

    private String buildFullName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        String full = (f + " " + l).trim();
        return full.isBlank() ? "Test User" : full;
    }

    private Client getOrCreateClient(String email, Company company, String companyName) {
        return clientRepository.findByEmail(email)
            .map(existing -> {
                existing.setCompany(company);
                if (existing.getCompanyName() == null) {
                    existing.setCompanyName(companyName);
                }
                if (existing.getPhone() == null) {
                    existing.setPhone("123456");
                }
                return clientRepository.save(existing);
            })
            .orElseGet(() -> {
                Client client = new Client();
                client.setCompanyName(companyName);
                client.setPhone("123456");
                client.setEmail(email);
                client.setCompany(company);
                return clientRepository.save(client);
            });
    }

    private WorkOrder getOrCreateWorkOrder(String orderNumber, Company company, Client client) {
        return workOrderRepository.findByOrderNumber(orderNumber)
            .map(existing -> {
                existing.setCompany(company);
                if (existing.getClient() == null) {
                    existing.setClient(client);
                }
                if (existing.getStatus() == null) {
                    existing.setStatus(OrderStatus.NEW);
                }
                return workOrderRepository.save(existing);
            })
            .orElseGet(() -> {
                WorkOrder workOrder = new WorkOrder();
                workOrder.setOrderNumber(orderNumber);
                workOrder.setTitle("Test Work Order");
                workOrder.setStatus(OrderStatus.NEW);
                workOrder.setClient(client);
                workOrder.setCompany(company);
                return workOrderRepository.save(workOrder);
            });
    }

    private Task getOrCreateTask(WorkOrder workOrder, Company company, String title) {
        return taskRepository.findByWorkOrderIdAndCompany_Id(workOrder.getId(), company.getId())
            .stream()
            .filter(t -> title.equals(t.getTitle()))
            .findFirst()
            .orElseGet(() -> {
                Task task = new Task();
                task.setTitle(title);
                task.setStatus(TaskStatus.NEW);
                task.setWorkOrder(workOrder);
                task.setCompany(company);
                return taskRepository.save(task);
            });
    }

    private Attachment getOrCreateAttachment(WorkOrder workOrder, Company company) throws Exception {
        List<Attachment> attachments = attachmentRepository.findByWorkOrderIdAndCompany_IdAndActiveTrue(workOrder.getId(), company.getId());
        if (!attachments.isEmpty()) {
            Attachment existing = attachments.get(0);
            String filePath = existing.getFilePath();
            if (filePath == null || filePath.isBlank()) {
                tempFile = Files.createTempFile("tenant-test-", ".txt");
                Files.writeString(tempFile, "secret");
                existing.setFilePath(tempFile.toString());
                existing.setFileName("secret.txt");
                existing.setOriginalFileName("secret.txt");
                existing.setMimeType("text/plain");
                existing.setFileSize(Files.size(tempFile));
                existing.setAttachmentType(AttachmentType.DESIGN_PREVIEW);
                existing.setWorkOrder(workOrder);
                existing.setCompany(company);
                return attachmentRepository.save(existing);
            }
            tempFile = Path.of(filePath);
            if (!Files.exists(tempFile)) {
                Files.createDirectories(tempFile.getParent());
                Files.writeString(tempFile, "secret");
            }
            return existing;
        }

        tempFile = Files.createTempFile("tenant-test-", ".txt");
        Files.writeString(tempFile, "secret");

        Attachment attachment = new Attachment();
        attachment.setFileName("secret.txt");
        attachment.setOriginalFileName("secret.txt");
        attachment.setFilePath(tempFile.toString());
        attachment.setMimeType("text/plain");
        attachment.setFileSize(Files.size(tempFile));
        attachment.setAttachmentType(AttachmentType.DESIGN_PREVIEW);
        attachment.setWorkOrder(workOrder);
        attachment.setCompany(company);
        return attachmentRepository.save(attachment);
    }

    public MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/login").user(username).password(password))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    public void cleanup() throws Exception {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }
}
