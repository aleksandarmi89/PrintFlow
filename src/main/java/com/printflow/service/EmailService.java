package com.printflow.service;

import com.printflow.dto.EmailMessage;
import com.printflow.entity.Company;
import com.printflow.entity.EmailOutbox;
import com.printflow.entity.MailSettings;
import com.printflow.entity.enums.EmailOutboxStatus;
import com.printflow.repository.EmailOutboxRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern DATA_URI_IMG_PATTERN =
        Pattern.compile("src=[\"']data:([^;]+);base64,([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final MailSenderResolver mailSenderResolver;
    private final EmailOutboxRepository outboxRepository;
    private final TenantEmailRateLimiter rateLimiter;
    private final boolean emailEnabled;
    private final String defaultFrom;
    private final int sendAttempts;

    public EmailService(MailSenderResolver mailSenderResolver,
                        EmailOutboxRepository outboxRepository,
                        TenantEmailRateLimiter rateLimiter,
                        @Value("${app.notification.email.enabled:false}") boolean emailEnabled,
                        @Value("${app.notification.email.from:no-reply@printflow.local}") String defaultFrom,
                        @Value("${app.notification.email.send-attempts:2}") int sendAttempts) {
        this.mailSenderResolver = mailSenderResolver;
        this.outboxRepository = outboxRepository;
        this.rateLimiter = rateLimiter;
        this.emailEnabled = emailEnabled;
        this.defaultFrom = defaultFrom;
        this.sendAttempts = Math.max(1, sendAttempts);
    }

    @Async("emailExecutor")
    public void send(EmailMessage message, Company company, String templateName) {
        try {
            sendInternal(message, company, templateName, false);
        } catch (Exception ex) {
            // Async path must never propagate to SimpleAsyncUncaughtExceptionHandler.
            log.warn("Async email pipeline failed for to={} subject={}", message.getTo(), message.getSubject(), ex);
        }
    }

    public void sendNow(EmailMessage message, Company company, String templateName) {
        sendInternal(message, company, templateName, true);
    }

    private void sendInternal(EmailMessage message, Company company, String templateName, boolean throwOnError) {
        if (!emailEnabled) {
            log.info("Email sending is disabled. Would send to: {} - Subject: {}", message.getTo(), message.getSubject());
            return;
        }
        if (message.getTo() == null || message.getTo().isBlank()) {
            log.warn("Email recipient missing. Skipping email with subject: {}", message.getSubject());
            return;
        }
        Long companyId = company != null ? company.getId() : null;
        if (!rateLimiter.tryAcquire(companyId)) {
            log.warn("Email rate limit exceeded for company {}", companyId);
            return;
        }

        EmailOutbox outbox;
        try {
            outbox = new EmailOutbox();
            outbox.setCompany(company);
            outbox.setToEmail(message.getTo());
            outbox.setSubject(message.getSubject());
            outbox.setTemplate(templateName);
            outbox.setStatus(EmailOutboxStatus.PENDING);
            outbox = outboxRepository.save(outbox);
        } catch (Exception ex) {
            log.warn("Unable to persist email outbox row for to={} subject={}", message.getTo(), message.getSubject(), ex);
            if (throwOnError) {
                throw new RuntimeException(ex);
            }
            return;
        }

        try {
            MailSenderResolver.ResolvedMailSender resolved = mailSenderResolver.resolve(company);
            JavaMailSender sender = resolved.getSender();
            if (sender == null) {
                throw new IllegalStateException("No mail sender configured");
            }
            MailSettings settings = resolved.getSettings();
            String from = resolveFrom(settings, company);
            boolean hasHtml = message.getHtmlBody() != null && !message.getHtmlBody().isBlank();
            boolean hasText = message.getTextBody() != null && !message.getTextBody().isBlank();
            ProcessedHtml processed = hasHtml ? convertDataUriImagesToCid(message.getHtmlBody()) : ProcessedHtml.empty();
            boolean hasInlineImages = !processed.images().isEmpty();
            boolean multipart = hasHtml && (hasText || hasInlineImages);
            MimeMessageHelper helper = new MimeMessageHelper(sender.createMimeMessage(), multipart, "UTF-8");
            helper.setTo(message.getTo());
            if (message.getCc() != null && !message.getCc().isEmpty()) {
                helper.setCc(message.getCc().toArray(new String[0]));
            }
            if (message.getBcc() != null && !message.getBcc().isEmpty()) {
                helper.setBcc(message.getBcc().toArray(new String[0]));
            }
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setSubject(message.getSubject());
            if (hasHtml && hasText) {
                helper.setText(message.getTextBody(), processed.html());
            } else if (hasHtml) {
                helper.setText(processed.html(), true);
            } else {
                helper.setText(hasText ? message.getTextBody() : "", false);
            }
            for (InlineImage image : processed.images()) {
                helper.addInline(image.cid(), new ByteArrayResource(image.data()), image.mimeType());
            }
            sendWithRetry(sender, helper.getMimeMessage(), message.getTo(), message.getSubject());
            outbox.setStatus(EmailOutboxStatus.SENT);
            outbox.setSentAt(LocalDateTime.now());
            outboxRepository.save(outbox);
        } catch (Exception ex) {
            try {
                outbox.setStatus(EmailOutboxStatus.FAILED);
                outbox.setErrorMessage(ex.getMessage());
                outboxRepository.save(outbox);
            } catch (Exception outboxEx) {
                log.warn("Unable to persist FAILED outbox state for to={} subject={}", message.getTo(), message.getSubject(), outboxEx);
            }
            log.warn("Failed to send email to: {} - Subject: {}", message.getTo(), message.getSubject(), ex);
            if (throwOnError) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void sendWithRetry(JavaMailSender sender, MimeMessage mimeMessage, String to, String subject) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= sendAttempts; attempt++) {
            try {
                sender.send(mimeMessage);
                if (attempt > 1) {
                    log.info("Email send succeeded after retry for to={} subject={} attempts={}", to, subject, attempt);
                }
                return;
            } catch (Exception ex) {
                last = ex;
                if (attempt < sendAttempts) {
                    log.warn("Email send attempt failed for to={} subject={} attempt={}/{}",
                        to, subject, attempt, sendAttempts, ex);
                }
            }
        }
        throw last != null ? last : new IllegalStateException("Unknown email send failure");
    }

    private String resolveFrom(MailSettings settings, Company company) {
        if (settings != null) {
            String fromEmail = settings.getFromEmail();
            String fromName = settings.getFromName();
            if (fromEmail != null && !fromEmail.isBlank()) {
                if (fromName != null && !fromName.isBlank()) {
                    return String.format("%s <%s>", fromName.trim(), fromEmail.trim());
                }
                return fromEmail.trim();
            }
        }
        if (company != null && company.getEmail() != null && !company.getEmail().isBlank()) {
            return company.getEmail().trim();
        }
        return defaultFrom;
    }

    private ProcessedHtml convertDataUriImagesToCid(String html) {
        if (html == null || html.isBlank()) {
            return ProcessedHtml.empty();
        }
        Matcher matcher = DATA_URI_IMG_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        List<InlineImage> images = new ArrayList<>();
        int index = 0;
        while (matcher.find()) {
            String mimeType = matcher.group(1);
            String base64 = matcher.group(2);
            String cid = "logo-inline-" + (++index);
            try {
                byte[] data = Base64.getDecoder().decode(base64);
                images.add(new InlineImage(cid, data, mimeType));
                matcher.appendReplacement(sb, "src=\"cid:" + cid + "\"");
            } catch (IllegalArgumentException ex) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return new ProcessedHtml(sb.toString(), images);
    }

    private record InlineImage(String cid, byte[] data, String mimeType) {}

    private record ProcessedHtml(String html, List<InlineImage> images) {
        private static ProcessedHtml empty() {
            return new ProcessedHtml("", List.of());
        }
    }
}
