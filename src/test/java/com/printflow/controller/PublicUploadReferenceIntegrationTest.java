package com.printflow.controller;

import com.printflow.entity.WorkOrder;
import com.printflow.entity.Attachment;
import com.printflow.entity.enums.AttachmentType;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.TaskRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.service.RateLimitService;
import com.printflow.testsupport.TenantTestFixture;
import com.printflow.testsupport.TenantTestFixture.TenantIds;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class PublicUploadReferenceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RateLimitService rateLimitService;

    private TenantTestFixture fixture;
    private TenantIds ids;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new TenantTestFixture(
            mockMvc,
            companyRepository,
            userRepository,
            clientRepository,
            workOrderRepository,
            taskRepository,
            attachmentRepository,
            passwordEncoder
        );
        ids = fixture.createTenantData();
        rateLimitService.clearInMemoryState();
    }

    @AfterEach
    void tearDown() throws Exception {
        rateLimitService.clearInMemoryState();
        if (fixture != null) {
            fixture.cleanup();
        }
    }

    @Test
    void uploadReferenceRejectsMissingMetadataJson() throws Exception {
        String token = assignPublicToken("upload-metadata-1");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "proof.pdf",
            "application/pdf",
            "hello".getBytes()
        );

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?uploadErrorKey=public.upload.error.metadata_mismatch"));

        assertClientFileCount(0L);
    }

    @Test
    void uploadReferenceRejectsMetadataThatDoesNotMatchPostedFile() throws Exception {
        String token = assignPublicToken("upload-metadata-2");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "proof.pdf",
            "application/pdf",
            "hello".getBytes()
        );
        String badMetaJson = "[{\"index\":0,\"name\":\"other.pdf\",\"size\":5,\"type\":\"application/pdf\",\"description\":\"x\"}]";

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("fileMetaJson", badMetaJson)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?uploadErrorKey=public.upload.error.metadata_mismatch"));

        assertClientFileCount(0L);
    }

    @Test
    void uploadReferencePreservesLangInErrorRedirect() throws Exception {
        String token = assignPublicToken("upload-metadata-lang");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "proof.pdf",
            "application/pdf",
            "hello".getBytes()
        );

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("lang", "en")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?uploadErrorKey=public.upload.error.metadata_mismatch&lang=en"));

        assertClientFileCount(0L);
    }

    @Test
    void uploadReferenceIgnoresUnsupportedLangInErrorRedirect() throws Exception {
        String token = assignPublicToken("upload-metadata-bad-lang");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "proof.pdf",
            "application/pdf",
            "hello".getBytes()
        );

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("lang", "de")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?uploadErrorKey=public.upload.error.metadata_mismatch"));

        assertClientFileCount(0L);
    }

    @Test
    void uploadReferenceNormalizesUppercaseLangInErrorRedirect() throws Exception {
        String token = assignPublicToken("upload-metadata-upper-lang");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "proof.pdf",
            "application/pdf",
            "hello".getBytes()
        );

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("lang", "EN")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token + "?uploadErrorKey=public.upload.error.metadata_mismatch&lang=en"));

        assertClientFileCount(0L);
    }

    @Test
    void uploadReferenceStoresClientFileWhenMetadataMatches() throws Exception {
        String token = assignPublicToken("upload-metadata-3");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brief.pdf",
            "application/pdf",
            "hello".getBytes()
        );
        String okMetaJson = "[{\"index\":0,\"name\":\"brief.pdf\",\"size\":5,\"type\":\"application/pdf\",\"description\":\"Client brief\"}]";

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("fileMetaJson", okMetaJson)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/public/order/" + token));

        List<Attachment> clientFiles = attachmentRepository
            .findByWorkOrderIdAndCompany_IdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(
                ids.workOrderId(),
                ids.company1Id(),
                AttachmentType.CLIENT_FILE
            );
        assertThat(clientFiles).hasSize(1);
        Attachment saved = clientFiles.get(0);
        assertThat(saved.getWorkOrder()).isNotNull();
        assertThat(saved.getWorkOrder().getId()).isEqualTo(ids.workOrderId());
        assertThat(saved.getCompany()).isNotNull();
        assertThat(saved.getCompany().getId()).isEqualTo(ids.company1Id());
        assertThat(saved.getDescription()).isEqualTo("Client brief");
        assertThat(saved.getUploadedBy()).isNull();
    }

    @Test
    void uploadReferenceRejectsOversizedMetadataPayload() throws Exception {
        String token = assignPublicToken("upload-metadata-4");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brief.pdf",
            "application/pdf",
            "hello".getBytes()
        );

        String oversizedMetaJson = "[" + "x".repeat(25000) + "]";
        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("fileMetaJson", oversizedMetaJson)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/public/order/" + token + "?uploadErrorKey=*"));

        List<Attachment> clientFiles = attachmentRepository
            .findByWorkOrderIdAndCompany_IdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(
                ids.workOrderId(),
                ids.company1Id(),
                AttachmentType.CLIENT_FILE
            );
        assertThat(clientFiles).isEmpty();
    }

    @Test
    void uploadReferenceRejectsSuspiciousFileName() throws Exception {
        String token = assignPublicToken("upload-metadata-5");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../brief.pdf",
            "application/pdf",
            "hello".getBytes()
        );
        String metaJson = "[{\"index\":0,\"name\":\"../brief.pdf\",\"size\":5,\"type\":\"application/pdf\",\"description\":\"Client brief\"}]";

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("fileMetaJson", metaJson)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/public/order/" + token + "?uploadErrorKey=*"));

        List<Attachment> clientFiles = attachmentRepository
            .findByWorkOrderIdAndCompany_IdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(
                ids.workOrderId(),
                ids.company1Id(),
                AttachmentType.CLIENT_FILE
            );
        assertThat(clientFiles).isEmpty();
    }

    @Test
    void uploadReferenceRejectsMimeTypeAndExtensionMismatch() throws Exception {
        String token = assignPublicToken("upload-metadata-6");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brief.pdf",
            "text/plain",
            "hello".getBytes()
        );
        String metaJson = "[{\"index\":0,\"name\":\"brief.pdf\",\"size\":5,\"type\":\"text/plain\",\"description\":\"Client brief\"}]";

        mockMvc.perform(multipart("/public/order/{token}/upload-reference", token)
                .file(file)
                .param("fileMetaJson", metaJson)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/public/order/" + token + "?uploadErrorKey=*"));

        List<Attachment> clientFiles = attachmentRepository
            .findByWorkOrderIdAndCompany_IdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(
                ids.workOrderId(),
                ids.company1Id(),
                AttachmentType.CLIENT_FILE
            );
        assertThat(clientFiles).isEmpty();
    }

    private String assignPublicToken(String token) {
        WorkOrder order = workOrderRepository.findById(ids.workOrderId()).orElseThrow();
        order.setPublicToken(token);
        order.setPublicTokenCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setPublicTokenExpiresAt(LocalDateTime.now().plusDays(1));
        workOrderRepository.save(order);
        return token;
    }

    private void assertClientFileCount(long expected) {
        long count = attachmentRepository.countByWorkOrderIdAndAttachmentTypeAndActiveTrue(
            ids.workOrderId(),
            AttachmentType.CLIENT_FILE
        );
        assertThat(count).isEqualTo(expected);
    }
}
