package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.repository.UserRepository;
import com.printflow.repository.CompanyRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentContextService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    public CurrentContextService(UserRepository userRepository,
                                 CompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("No authenticated user");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new AccessDeniedException("User not found"));
    }

    public Company currentCompany() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("No authenticated user");
        }
        String username = auth.getName();
        Long companyId = userRepository.findCompanyIdByUsername(username).orElse(null);
        if (companyId == null) {
            throw new AccessDeniedException("No company assigned to user");
        }
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new AccessDeniedException("No company assigned to user"));
    }

    public Long currentCompanyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("No authenticated user");
        }
        String username = auth.getName();
        Long companyId = userRepository.findCompanyIdByUsername(username).orElse(null);
        if (companyId == null) {
            throw new AccessDeniedException("No company assigned to user");
        }
        return companyId;
    }
}
