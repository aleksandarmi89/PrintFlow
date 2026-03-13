package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.repository.MailSettingsRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MailSettingsServiceTest {

    @Test
    void resolveSmtpSourceReturnsMailSettingsWhenConfigured() {
        MailSettingsRepository repo = mock(MailSettingsRepository.class);
        CryptoService crypto = mock(CryptoService.class);
        MailSettingsService service = new MailSettingsService(repo, crypto);

        Company company = new Company();
        MailSettings settings = new MailSettings();
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(587);
        settings.setSmtpUsername("noreply@example.com");
        settings.setSmtpPasswordEnc("enc");

        assertEquals("mail_settings", service.resolveSmtpSource(company, settings));
        assertTrue(service.isConfiguredWithLegacyFallback(company, settings));
    }

    @Test
    void resolveSmtpSourceReturnsLegacyWhenCompanyFieldsConfigured() {
        MailSettingsRepository repo = mock(MailSettingsRepository.class);
        CryptoService crypto = mock(CryptoService.class);
        MailSettingsService service = new MailSettingsService(repo, crypto);

        Company company = new Company();
        company.setSmtpHost("legacy.example.com");
        company.setSmtpPort(587);
        company.setSmtpUser("legacy-user");
        company.setSmtpPassword("legacy-pass");

        MailSettings settings = new MailSettings();

        assertEquals("legacy_company", service.resolveSmtpSource(company, settings));
        assertTrue(service.isConfiguredWithLegacyFallback(company, settings));
    }

    @Test
    void resolveSmtpSourceReturnsNoneWhenNeitherConfigured() {
        MailSettingsRepository repo = mock(MailSettingsRepository.class);
        CryptoService crypto = mock(CryptoService.class);
        MailSettingsService service = new MailSettingsService(repo, crypto);

        Company company = new Company();
        MailSettings settings = new MailSettings();

        assertEquals("none", service.resolveSmtpSource(company, settings));
        assertFalse(service.isConfiguredWithLegacyFallback(company, settings));
    }
}
