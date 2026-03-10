package com.printflow.service;

import com.printflow.entity.AuditLog;
import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.enums.AuditAction;
import com.printflow.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final TenantGuard tenantGuard;

    public AuditLogService(AuditLogRepository auditLogRepository, TenantGuard tenantGuard) {
        this.auditLogRepository = auditLogRepository;
        this.tenantGuard = tenantGuard;
    }

    public void log(AuditAction action, String entityType, Long entityId, String oldValue, String newValue, String description) {
        log(action, entityType, entityId, oldValue, newValue, description, null);
    }

    public void log(AuditAction action, String entityType, Long entityId, String oldValue, String newValue,
                    String description, Company company) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setDescription(description);
        log.setCreatedAt(LocalDateTime.now());

        User user = safeGetCurrentUser();
        if (user != null) {
            log.setUser(user);
            if (company == null) {
                company = user.getCompany();
            }
        }
        log.setCompany(company);

        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(request.getHeader("User-Agent"));
        }

        auditLogRepository.save(log);
    }

    public List<AuditLog> getByEntity(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return Collections.emptyList();
        }
        return auditLogRepository.findByEntityTypeAndEntityIdWithUser(entityType, entityId);
    }

    public List<AuditLog> getAuditLogs(Long companyId, AuditAction action) {
        if (tenantGuard.isSuperAdmin()) {
            if (companyId != null) {
                if (action != null) {
                    return auditLogRepository.findByCompanyIdAndActionWithUser(companyId, action);
                }
                return auditLogRepository.findByCompanyIdWithUser(companyId);
            }
            if (action != null) {
                return auditLogRepository.findByActionWithUser(action);
            }
            return auditLogRepository.findAllWithUser();
        }
        Long scopedCompanyId = tenantGuard.requireCompanyId();
        if (action != null) {
            return auditLogRepository.findByCompanyIdAndActionWithUser(scopedCompanyId, action);
        }
        return auditLogRepository.findByCompanyIdWithUser(scopedCompanyId);
    }

    public org.springframework.data.domain.Page<AuditLog> searchAuditLogs(Long companyId, AuditAction action,
                                                                          String query, Long userId,
                                                                          Long entityId, String entityType,
                                                                          org.springframework.data.domain.Pageable pageable) {
        Long scopedCompanyId = companyId;
        if (!tenantGuard.isSuperAdmin()) {
            scopedCompanyId = tenantGuard.requireCompanyId();
        }
        return auditLogRepository.searchAuditLogs(scopedCompanyId, action, query, userId, entityId, entityType, pageable);
    }

    private User safeGetCurrentUser() {
        try {
            return tenantGuard.getCurrentUser();
        } catch (Exception ex) {
            return null;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                return servletAttrs.getRequest();
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
