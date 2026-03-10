package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.repository.CompanyRepository;
import com.printflow.security.CustomUserPrincipal;
import org.springframework.stereotype.Service;

@Service
public class TenantContextService {

    private final CurrentUserContext currentUserContext;
    private final CompanyRepository companyRepository;

    public TenantContextService(CurrentUserContext currentUserContext,
                                CompanyRepository companyRepository) {
        this.currentUserContext = currentUserContext;
        this.companyRepository = companyRepository;
    }

    public User getCurrentUser() {
        return currentUserContext.getUser();
    }

    public Company getCurrentCompany() {
        Long companyId = getCurrentCompanyId();
        if (companyId == null) {
            return null;
        }
        return companyRepository.findById(companyId).orElse(null);
    }

    public Long getCurrentUserId() {
        return currentUserContext.getUserId();
    }

    public Long getCurrentCompanyId() {
        return currentUserContext.getCompanyId();
    }

    public boolean isSuperAdmin() {
        Object principal = currentUserContext.getUser() != null ? currentUserContext.getUser() : null;
        if (principal instanceof User user) {
            return user.getRole() == Role.SUPER_ADMIN;
        }
        Object authPrincipal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
            ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()
            : null;
        if (authPrincipal instanceof CustomUserPrincipal p) {
            return p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.SUPER_ADMIN.name()));
        }
        return false;
    }

    public Long requireCompanyId() {
        Long companyId = getCurrentCompanyId();
        if (companyId != null) {
            return companyId;
        }
        throw new RuntimeException("No authenticated user company");
    }
}
