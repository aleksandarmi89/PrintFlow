package com.printflow.config;

import com.printflow.service.TenantContextService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class HibernateTenantFilter extends OncePerRequestFilter {

    private final EntityManager entityManager;
    private final TenantContextService tenantContextService;

    public HibernateTenantFilter(EntityManager entityManager, TenantContextService tenantContextService) {
        this.entityManager = entityManager;
        this.tenantContextService = tenantContextService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = null;
        boolean enabled = false;

        String path = request.getRequestURI();
        boolean isPublicPath = path != null && (path.startsWith("/public/") || path.startsWith("/portal/"));

        if (!isPublicPath && tenantContextService.getCurrentUser() != null && !tenantContextService.isSuperAdmin()) {
            Long companyId = tenantContextService.requireCompanyId();
            filter = session.enableFilter("tenantFilter");
            filter.setParameter("companyId", companyId);
            enabled = true;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (enabled) {
                session.disableFilter("tenantFilter");
            }
        }
    }
}
