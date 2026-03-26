package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.EmailOutbox;
import com.printflow.entity.enums.EmailOutboxStatus;
import com.printflow.repository.EmailOutboxRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmailOutboxService {

    private final EmailOutboxRepository emailOutboxRepository;

    public EmailOutboxService(EmailOutboxRepository emailOutboxRepository) {
        this.emailOutboxRepository = emailOutboxRepository;
    }

    public Page<EmailOutbox> listForCompany(Company company, EmailOutboxStatus status, int page, int size) {
        if (company == null || company.getId() == null) {
            return Page.empty();
        }
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        if (status == null) {
            return emailOutboxRepository.findByCompany_IdOrderByCreatedAtDesc(company.getId(), pageable);
        }
        return emailOutboxRepository.findByCompany_IdAndStatusOrderByCreatedAtDesc(company.getId(), status, pageable);
    }

    public long totalForCompany(Company company) {
        return company == null || company.getId() == null ? 0L : emailOutboxRepository.countByCompany_Id(company.getId());
    }

    public long countForCompanyByStatus(Company company, EmailOutboxStatus status) {
        return company == null || company.getId() == null ? 0L : emailOutboxRepository.countByCompany_IdAndStatus(company.getId(), status);
    }

    @Transactional
    public long cleanupFailed(Company company, int olderThanDays) {
        if (company == null || company.getId() == null) {
            return 0L;
        }
        int days = Math.max(0, olderThanDays);
        if (days == 0) {
            return emailOutboxRepository.deleteByCompany_IdAndStatus(
                company.getId(), EmailOutboxStatus.FAILED);
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return emailOutboxRepository.deleteByCompany_IdAndStatusAndCreatedAtLessThanEqual(
            company.getId(), EmailOutboxStatus.FAILED, cutoff);
    }
}
