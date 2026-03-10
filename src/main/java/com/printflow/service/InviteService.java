package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.UserInvite;
import com.printflow.repository.UserInviteRepository;
import com.printflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    private final UserInviteRepository userInviteRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContextService tenantContextService;
    private final BillingAccessService billingAccessService;
    private final PlanLimitService planLimitService;
    private final Clock clock;
    private final int inviteExpiryHours;
    private final String baseUrl;

    public InviteService(UserInviteRepository userInviteRepository,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         TenantContextService tenantContextService,
                         BillingAccessService billingAccessService,
                         PlanLimitService planLimitService,
                         Clock clock,
                         @Value("${app.invites.expiry-hours:48}") int inviteExpiryHours,
                         @Value("${app.base-url:http://localhost:8088}") String baseUrl) {
        this.userInviteRepository = userInviteRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantContextService = tenantContextService;
        this.billingAccessService = billingAccessService;
        this.planLimitService = planLimitService;
        this.clock = clock;
        this.inviteExpiryHours = inviteExpiryHours;
        this.baseUrl = baseUrl;
    }

    public String createInvite(String email, Role role) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (role == null) {
            throw new RuntimeException("Role is required");
        }
        if (!tenantContextService.isSuperAdmin() && role == Role.SUPER_ADMIN) {
            throw new RuntimeException("Not allowed to invite SUPER_ADMIN");
        }
        Company company = tenantContextService.getCurrentCompany();
        if (company == null) {
            throw new RuntimeException("Company is required");
        }
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        planLimitService.assertUserLimit(company);

        UserInvite invite = new UserInvite();
        invite.setToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setEmail(email.trim());
        invite.setRole(role);
        invite.setCompany(company);
        User inviter = tenantContextService.getCurrentUser();
        invite.setInvitedByUserId(inviter != null ? inviter.getId() : null);
        LocalDateTime now = LocalDateTime.now(clock);
        invite.setCreatedAt(now);
        invite.setExpiresAt(now.plusHours(Math.max(1, inviteExpiryHours)));

        userInviteRepository.save(invite);

        String link = baseUrl.replaceAll("/$", "") + "/invite/" + invite.getToken();
        log.info("Invite link for {}: {}", invite.getEmail(), link);
        return link;
    }

    public UserInvite getValidInvite(String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Invalid invitation token");
        }
        UserInvite invite = userInviteRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Invitation not found"));
        if (invite.getUsedAt() != null) {
            throw new RuntimeException("Invitation already used");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (invite.getExpiresAt() != null && now.isAfter(invite.getExpiresAt())) {
            throw new RuntimeException("Invitation expired");
        }
        return invite;
    }

    public void acceptInvite(String token, String username, String fullName, String password) {
        UserInvite invite = getValidInvite(token);
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username is required");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters");
        }
        if (userRepository.existsByUsername(username.trim())) {
            throw new RuntimeException("Username already exists");
        }
        if (invite.getEmail() != null && userRepository.existsByEmail(invite.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Company company = invite.getCompany();
        billingAccessService.assertBillingActiveForPremiumAction(company.getId());
        planLimitService.assertUserLimit(company);

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName != null ? fullName.trim() : null);
        user.setEmail(invite.getEmail());
        user.setRole(invite.getRole());
        user.setActive(true);
        user.setCompany(company);
        user.setCreatedAt(LocalDateTime.now(clock));
        userRepository.save(user);

        invite.setUsedAt(LocalDateTime.now(clock));
        userInviteRepository.save(invite);
    }
}
