package com.printflow.repository;

import com.printflow.entity.EmailOutbox;
import com.printflow.entity.enums.EmailOutboxStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {
    Page<EmailOutbox> findByCompany_IdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    Page<EmailOutbox> findByCompany_IdAndStatusOrderByCreatedAtDesc(Long companyId, EmailOutboxStatus status, Pageable pageable);

    long countByCompany_Id(Long companyId);
    long countByCompany_IdAndStatus(Long companyId, EmailOutboxStatus status);

    @Transactional
    long deleteByCompany_IdAndStatusAndCreatedAtBefore(Long companyId, EmailOutboxStatus status, LocalDateTime cutoff);

    @Transactional
    long deleteByCompany_IdAndStatusAndCreatedAtLessThanEqual(Long companyId, EmailOutboxStatus status, LocalDateTime cutoff);

    @Transactional
    long deleteByCompany_IdAndStatus(Long companyId, EmailOutboxStatus status);
}
