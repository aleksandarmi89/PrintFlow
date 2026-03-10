package com.printflow.service;

import com.printflow.dto.MailSettingsDTO;
import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.repository.MailSettingsRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MailSettingsService {

    private final MailSettingsRepository mailSettingsRepository;
    private final CryptoService cryptoService;

    public MailSettingsService(MailSettingsRepository mailSettingsRepository, CryptoService cryptoService) {
        this.mailSettingsRepository = mailSettingsRepository;
        this.cryptoService = cryptoService;
    }

    public MailSettings getOrCreate(Company company) {
        return mailSettingsRepository.findByCompany_Id(company.getId())
            .orElseGet(() -> {
                MailSettings settings = new MailSettings();
                settings.setCompany(company);
                return mailSettingsRepository.save(settings);
            });
    }

    public MailSettingsDTO toDto(MailSettings settings) {
        MailSettingsDTO dto = new MailSettingsDTO();
        dto.setEnabled(settings.getEnabled());
        dto.setSmtpHost(settings.getSmtpHost());
        dto.setSmtpPort(settings.getSmtpPort());
        dto.setSmtpUsername(settings.getSmtpUsername());
        dto.setSmtpUseTls(settings.getSmtpUseTls());
        dto.setSmtpUseSsl(settings.getSmtpUseSsl());
        dto.setFromEmail(settings.getFromEmail());
        dto.setFromName(settings.getFromName());
        return dto;
    }

    public MailSettings saveFromDto(Company company, MailSettingsDTO dto) {
        MailSettings settings = getOrCreate(company);
        settings.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        settings.setSmtpHost(dto.getSmtpHost());
        settings.setSmtpPort(dto.getSmtpPort());
        settings.setSmtpUsername(dto.getSmtpUsername());
        settings.setSmtpUseTls(dto.getSmtpUseTls());
        settings.setSmtpUseSsl(dto.getSmtpUseSsl());
        settings.setFromEmail(dto.getFromEmail());
        settings.setFromName(dto.getFromName());
        if (dto.getSmtpPassword() != null && !dto.getSmtpPassword().isBlank()) {
            settings.setSmtpPasswordEnc(cryptoService.encrypt(dto.getSmtpPassword()));
        }
        return mailSettingsRepository.save(settings);
    }

    public boolean isConfigured(MailSettings settings) {
        return settings != null
            && settings.getSmtpHost() != null && !settings.getSmtpHost().isBlank()
            && settings.getSmtpPort() != null
            && settings.getSmtpUsername() != null && !settings.getSmtpUsername().isBlank()
            && settings.getSmtpPasswordEnc() != null && !settings.getSmtpPasswordEnc().isBlank();
    }
}
