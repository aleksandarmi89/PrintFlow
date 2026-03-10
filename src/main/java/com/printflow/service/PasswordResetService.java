package com.printflow.service;

import com.printflow.entity.PasswordResetToken;
import com.printflow.entity.User;
import com.printflow.repository.PasswordResetTokenRepository;
import com.printflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final com.printflow.repository.CompanyRepository companyRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final String baseUrl;
    private final int tokenMinutes;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                com.printflow.repository.CompanyRepository companyRepository,
                                NotificationService notificationService,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.base-url:http://localhost:8088}") String baseUrl,
                                @Value("${app.password-reset.token-minutes:30}") int tokenMinutes) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.baseUrl = baseUrl;
        this.tokenMinutes = tokenMinutes;
    }

    @Transactional
    public void requestReset(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        String trimmed = identifier.trim();
        Optional<User> userOpt = userRepository.findByUsername(trimmed);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmailIgnoreCase(trimmed);
        }
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        Long companyId = userRepository.findCompanyIdByUserId(user.getId()).orElse(null);
        if (companyId == null) {
            return;
        }
        com.printflow.entity.Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return;
        }
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setCompany(company);
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(tokenMinutes));
        tokenRepository.save(token);

        String url = baseUrl.replaceAll("/$", "") + "/reset-password?token=" + token.getToken();
        notificationService.sendPasswordResetEmail(user, url, token.getToken(), company);
    }

    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByToken(token)
            .filter(t -> t.getUsedAt() == null)
            .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = validateToken(token);
        if (tokenOpt.isEmpty()) {
            return false;
        }
        PasswordResetToken resetToken = tokenOpt.get();
        User user = resetToken.getUser();
        if (user == null) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);
        return true;
    }
}
