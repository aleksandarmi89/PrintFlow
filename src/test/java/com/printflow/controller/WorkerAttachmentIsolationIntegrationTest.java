package com.printflow.controller;

import com.printflow.entity.Attachment;
import com.printflow.entity.Comment;
import com.printflow.entity.Company;
import com.printflow.entity.Task;
import com.printflow.entity.User;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CommentRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.testsupport.TenantTestFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkerAttachmentIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private TenantTestFixture fixture;
    private Path tempFile;

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) {
            fixture.cleanup();
        }
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void commentDeleteRejectsTaskAndCommentMismatch() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company = companyRepository.findById(ids.company1Id()).orElseThrow();
        User admin = userRepository.findByUsername("tenant1_admin").orElseThrow();
        Task baseTask = taskRepository.findById(ids.taskId()).orElseThrow();
        baseTask.setAssignedTo(admin);
        taskRepository.save(baseTask);

        Task otherTask = new Task();
        otherTask.setTitle("Other task");
        otherTask.setStatus(TaskStatus.IN_PROGRESS);
        otherTask.setWorkOrder(baseTask.getWorkOrder());
        otherTask.setCompany(company);
        otherTask.setAssignedTo(admin);
        taskRepository.save(otherTask);

        Comment comment = new Comment();
        comment.setTask(baseTask);
        comment.setUser(admin);
        comment.setContent("Owned comment");
        comment.setCreatedAt(LocalDateTime.now());
        comment = commentRepository.save(comment);

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/worker/task/{taskId}/comment/{commentId}/delete", otherTask.getId(), comment.getId())
                .with(csrf())
                .session(tenant1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/worker/task/" + otherTask.getId()));

        assertThat(commentRepository.findById(comment.getId())).isPresent();
    }

    @Test
    void commentAttachmentDeleteRejectsCommentAndAttachmentMismatch() throws Exception {
        fixture = new TenantTestFixture(mockMvc, companyRepository, userRepository, clientRepository,
            workOrderRepository, taskRepository, attachmentRepository, passwordEncoder);
        TenantTestFixture.TenantIds ids = fixture.createTenantData();
        Company company = companyRepository.findById(ids.company1Id()).orElseThrow();
        User admin = userRepository.findByUsername("tenant1_admin").orElseThrow();
        Task task = taskRepository.findById(ids.taskId()).orElseThrow();
        task.setAssignedTo(admin);
        taskRepository.save(task);

        Comment commentA = new Comment();
        commentA.setTask(task);
        commentA.setUser(admin);
        commentA.setContent("A");
        commentA.setCreatedAt(LocalDateTime.now());
        commentA = commentRepository.save(commentA);

        Comment commentB = new Comment();
        commentB.setTask(task);
        commentB.setUser(admin);
        commentB.setContent("B");
        commentB.setCreatedAt(LocalDateTime.now());
        commentB = commentRepository.save(commentB);

        tempFile = Files.createTempFile("worker-attachment-", ".txt");
        Files.writeString(tempFile, "x");
        Attachment attachment = new Attachment();
        attachment.setCompany(company);
        attachment.setTask(task);
        attachment.setComment(commentA);
        attachment.setUploadedBy(admin);
        attachment.setAttachmentType(AttachmentType.OTHER);
        attachment.setFileName("a.txt");
        attachment.setOriginalFileName("a.txt");
        attachment.setMimeType("text/plain");
        attachment.setFilePath(tempFile.toString());
        attachment.setFileSize(1L);
        attachment = attachmentRepository.save(attachment);

        MockHttpSession tenant1 = fixture.login("tenant1_admin", "password");
        mockMvc.perform(post("/worker/task/{taskId}/comment/{commentId}/attachment/{attachmentId}/delete",
                task.getId(), commentB.getId(), attachment.getId())
                .with(csrf())
                .session(tenant1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/worker/task/" + task.getId()));

        Attachment stillThere = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertThat(stillThere.isActive()).isTrue();
    }
}
