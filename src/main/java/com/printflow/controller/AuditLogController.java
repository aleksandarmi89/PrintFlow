package com.printflow.controller;

import com.printflow.entity.AuditLog;
import com.printflow.entity.enums.AuditAction;
import com.printflow.service.AuditLogService;
import com.printflow.service.CompanyService;
import com.printflow.service.TenantContextService;
import com.printflow.config.PaginationConfig;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/admin/audit-logs")
public class AuditLogController extends BaseController {

    private final AuditLogService auditLogService;
    private final CompanyService companyService;
    private final TenantContextService tenantContextService;
    private final PaginationConfig paginationConfig;

    public AuditLogController(AuditLogService auditLogService,
                              CompanyService companyService,
                              TenantContextService tenantContextService,
                              PaginationConfig paginationConfig) {
        this.auditLogService = auditLogService;
        this.companyService = companyService;
        this.tenantContextService = tenantContextService;
        this.paginationConfig = paginationConfig;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long companyId,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String query,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(required = false) Long entityId,
                       @RequestParam(required = false) String entityType,
                       @RequestParam(name = "pageNumber", required = false) Integer pageNumber,
                       @RequestParam(required = false) Integer size,
                       Model model) {
        if (!tenantContextService.isSuperAdmin()) {
            companyId = tenantContextService.requireCompanyId();
        }
        String normalizedAction = normalizeOptional(action);
        String normalizedQuery = normalizeOptional(query);
        String normalizedEntityType = normalizeOptional(entityType);
        AuditAction actionEnum = parseAction(normalizedAction);
        int page = (pageNumber != null && pageNumber > 0) ? pageNumber - 1 : 0;
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        var logsPage = auditLogService.searchAuditLogs(
            companyId,
            actionEnum,
            normalizedQuery,
            userId,
            entityId,
            normalizedEntityType,
            org.springframework.data.domain.PageRequest.of(safePage, pageSize)
        );

        model.addAttribute("logsPage", logsPage);
        model.addAttribute("logs", logsPage.getContent());
        model.addAttribute("actions", AuditAction.values());
        model.addAttribute("action", normalizedAction);
        model.addAttribute("query", normalizedQuery);
        model.addAttribute("userId", userId);
        model.addAttribute("entityId", entityId);
        model.addAttribute("entityType", normalizedEntityType);
        model.addAttribute("page", logsPage.getNumber());
        model.addAttribute("pageNumber", logsPage.getNumber() + 1);
        model.addAttribute("size", pageSize);
        model.addAttribute("allowedSizes", paginationConfig.getAllowedSizes());
        model.addAttribute("totalPages", logsPage.getTotalPages());
        model.addAttribute("companyId", companyId);
        if (tenantContextService.isSuperAdmin()) {
            model.addAttribute("companies", companyService.getCompanies(null));
        }
        return "admin/audit-logs/list";
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) Long companyId,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String query,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(required = false) Long entityId,
                       @RequestParam(required = false) String entityType,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "5000") int size,
                       HttpServletResponse response) throws Exception {
        if (!tenantContextService.isSuperAdmin()) {
            companyId = tenantContextService.requireCompanyId();
        }
        String normalizedAction = normalizeOptional(action);
        String normalizedQuery = normalizeOptional(query);
        String normalizedEntityType = normalizeOptional(entityType);
        AuditAction actionEnum = parseAction(normalizedAction);
        int safePage = paginationConfig.normalizePage(page);
        int pageSize = paginationConfig.normalizeSize(size);
        var logs = auditLogService.searchAuditLogs(
            companyId,
            actionEnum,
            normalizedQuery,
            userId,
            entityId,
            normalizedEntityType,
            org.springframework.data.domain.PageRequest.of(safePage, pageSize)
        ).getContent();

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit-logs.csv\"");

        StringBuilder sb = new StringBuilder();
        sb.append("Created At,Action,Entity,Entity ID,User,Company,IP,Description,Old Value,New Value\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (AuditLog log : logs) {
            sb.append(csv(fmt.format(log.getCreatedAt()))).append(',')
              .append(csv(String.valueOf(log.getAction()))).append(',')
              .append(csv(log.getEntityType())).append(',')
              .append(csv(log.getEntityId() != null ? log.getEntityId().toString() : "")).append(',')
              .append(csv(log.getUser() != null ? log.getUser().getFullName() : "System")).append(',')
              .append(csv(log.getCompany() != null ? log.getCompany().getName() : "")).append(',')
              .append(csv(log.getIpAddress())).append(',')
              .append(csv(log.getDescription())).append(',')
              .append(csv(log.getOldValue())).append(',')
              .append(csv(log.getNewValue())).append('\n');
        }
        response.getWriter().write(sb.toString());
    }

    private AuditAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(action.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
