package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class TenantGuard {

    private final TenantContextService tenantContextService;

    public TenantGuard(TenantContextService tenantContextService) {
        this.tenantContextService = tenantContextService;
    }

    public boolean isSuperAdmin() {
        return tenantContextService.isSuperAdmin();
    }

    public boolean isAuthenticated() {
        return tenantContextService.getCurrentUser() != null;
    }

    public Long requireCompanyId() {
        return tenantContextService.requireCompanyId();
    }

    public Company requireCompany() {
        Company company = tenantContextService.getCurrentCompany();
        if (company == null) {
            throw new AccessDeniedException("No authenticated company");
        }
        return company;
    }

    public User getCurrentUser() {
        return tenantContextService.getCurrentUser();
    }

    public Company getCurrentCompany() {
        return tenantContextService.getCurrentCompany();
    }

    public void assertSameTenant(Company company, String resource) {
        if (isSuperAdmin()) {
            return;
        }
        Long currentCompanyId = requireCompanyId();
        Long entityCompanyId = company != null ? company.getId() : null;
        if (entityCompanyId == null || !entityCompanyId.equals(currentCompanyId)) {
            throw new AccessDeniedException(resource + " does not belong to your company.");
        }
    }

    public void assertSameTenantId(Long companyId, String resource) {
        if (isSuperAdmin()) {
            return;
        }
        Long currentCompanyId = requireCompanyId();
        if (companyId == null || !companyId.equals(currentCompanyId)) {
            throw new AccessDeniedException(resource + " does not belong to your company.");
        }
    }
}
