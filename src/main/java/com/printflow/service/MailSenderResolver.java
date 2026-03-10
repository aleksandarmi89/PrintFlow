package com.printflow.service;

import com.printflow.config.MailDevProperties;
import com.printflow.entity.Company;
import com.printflow.entity.MailSettings;
import com.printflow.repository.MailSettingsRepository;
import java.util.Optional;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MailSenderResolver {

    private final MailSettingsRepository mailSettingsRepository;
    private final TenantMailSenderFactory senderFactory;
    private final CryptoService cryptoService;
    private final MailDevProperties devProperties;
    private final JavaMailSender fallbackSender;

    public MailSenderResolver(MailSettingsRepository mailSettingsRepository,
                              TenantMailSenderFactory senderFactory,
                              CryptoService cryptoService,
                              MailDevProperties devProperties,
                              Optional<JavaMailSender> fallbackSender) {
        this.mailSettingsRepository = mailSettingsRepository;
        this.senderFactory = senderFactory;
        this.cryptoService = cryptoService;
        this.devProperties = devProperties;
        this.fallbackSender = fallbackSender.orElse(null);
    }

    public ResolvedMailSender resolve(Company company) {
        MailSettings settings = company != null
            ? mailSettingsRepository.findByCompany_Id(company.getId()).orElse(null)
            : null;
        if (settings != null && Boolean.TRUE.equals(settings.getEnabled())
            && settings.getSmtpHost() != null && !settings.getSmtpHost().isBlank()) {
            String decrypted = cryptoService.decrypt(settings.getSmtpPasswordEnc());
            JavaMailSender sender = senderFactory.buildSender(
                settings.getSmtpHost().trim(),
                settings.getSmtpPort(),
                settings.getSmtpUsername(),
                decrypted,
                settings.getSmtpUseTls() == null || settings.getSmtpUseTls(),
                settings.getSmtpUseSsl() != null && settings.getSmtpUseSsl()
            );
            return new ResolvedMailSender(sender, settings);
        }
        JavaMailSender devSender = buildDevSenderIfConfigured();
        if (devSender != null) {
            return new ResolvedMailSender(devSender, null);
        }
        return new ResolvedMailSender(fallbackSender, null);
    }

    private JavaMailSender buildDevSenderIfConfigured() {
        if (devProperties.getHost() == null || devProperties.getHost().isBlank()) {
            return null;
        }
        return senderFactory.buildSender(
            devProperties.getHost().trim(),
            devProperties.getPort(),
            devProperties.getUsername(),
            devProperties.getPassword(),
            true,
            false
        );
    }

    public static class ResolvedMailSender {
        private final JavaMailSender sender;
        private final MailSettings settings;

        public ResolvedMailSender(JavaMailSender sender, MailSettings settings) {
            this.sender = sender;
            this.settings = settings;
        }

        public JavaMailSender getSender() {
            return sender;
        }

        public MailSettings getSettings() {
            return settings;
        }
    }
}
