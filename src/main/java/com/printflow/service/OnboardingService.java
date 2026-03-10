package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.repository.CompanyRepository;
import com.printflow.repository.UserRepository;
import com.printflow.util.SlugUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional
public class OnboardingService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final int trialDays;
    private final Clock clock;
    private final TemplateSeederService templateSeederService;

    public OnboardingService(CompanyRepository companyRepository,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${app.billing.trial-days:14}") int trialDays,
                             Clock clock,
                             TemplateSeederService templateSeederService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.trialDays = trialDays;
        this.clock = clock;
        this.templateSeederService = templateSeederService;
    }

    public void registerCompanyAndAdmin(String companyName,
                                        String username,
                                        String fullName,
                                        String email,
                                        String phone,
                                        String rawPassword) {
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new RuntimeException("Company name is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters");
        }
        if (companyRepository.existsByName(companyName.trim())) {
            throw new RuntimeException("Company name already exists");
        }
        if (userRepository.existsByUsername(username.trim())) {
            throw new RuntimeException("Username already exists");
        }
        if (email != null && !email.isBlank() && userRepository.existsByEmail(email.trim())) {
            throw new RuntimeException("Email already exists");
        }

        Company company = new Company();
        String normalizedName = companyName.trim();
        company.setName(normalizedName);
        company.setSlug(generateUniqueSlug(normalizedName));
        company.setActive(true);
        LocalDateTime now = LocalDateTime.now(clock);
        company.setTrialStart(now);
        company.setTrialEnd(now.plusDays(Math.max(0, trialDays)));
        Company savedCompany = companyRepository.save(company);

        User admin = new User();
        admin.setUsername(username.trim());
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setFullName(fullName != null ? fullName.trim() : null);
        admin.setEmail(email != null ? email.trim() : null);
        admin.setPhone(phone != null ? phone.trim() : null);
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setCreatedAt(now);
        admin.setCompany(savedCompany);
        userRepository.save(admin);

        templateSeederService.seedDefaultTemplates(savedCompany);
    }

    private String generateUniqueSlug(String name) {
        String base = SlugUtil.toSlug(name);
        String slug = base;
        int i = 2;
        while (companyRepository.existsBySlug(slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }
}
