package com.printflow.repository;

import com.printflow.entity.PublicOrderRequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicOrderRequestAttachmentRepository extends JpaRepository<PublicOrderRequestAttachment, Long> {
    List<PublicOrderRequestAttachment> findByRequest_IdOrderByCreatedAtAsc(Long requestId);
    Optional<PublicOrderRequestAttachment> findByIdAndCompany_Id(Long id, Long companyId);
}
