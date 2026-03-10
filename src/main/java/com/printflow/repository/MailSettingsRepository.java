package com.printflow.repository;

import com.printflow.entity.MailSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailSettingsRepository extends JpaRepository<MailSettings, Long> {
    Optional<MailSettings> findByCompany_Id(Long companyId);
}
