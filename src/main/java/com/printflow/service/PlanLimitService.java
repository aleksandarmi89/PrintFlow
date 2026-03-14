package com.printflow.service;

import com.printflow.config.PlanLimitsProperties;
import com.printflow.entity.Company;
import com.printflow.entity.enums.PlanTier;
import com.printflow.repository.AttachmentRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class PlanLimitService {

    private final PlanLimitsProperties planLimitsProperties;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AttachmentRepository attachmentRepository;

    public PlanLimitService(PlanLimitsProperties planLimitsProperties,
                            UserRepository userRepository,
                            WorkOrderRepository workOrderRepository,
                            AttachmentRepository attachmentRepository) {
        this.planLimitsProperties = planLimitsProperties;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public void assertUserLimit(Company company) {
        if (company == null) {
            return;
        }
        PlanLimitsProperties.PlanLimits limits = resolveLimits(company);
        int maxUsers = limits.getMaxUsers();
        if (maxUsers <= 0) {
            return;
        }
        long activeUsers = userRepository.countByCompany_IdAndActiveTrue(company.getId());
        if (activeUsers >= maxUsers) {
            throw new PlanLimitExceededException(
                "plan.limit.users");
        }
    }

    public void assertMonthlyOrdersLimit(Company company) {
        if (company == null) {
            return;
        }
        PlanLimitsProperties.PlanLimits limits = resolveLimits(company);
        int maxOrders = limits.getMaxMonthlyOrders();
        if (maxOrders <= 0) {
            return;
        }
        LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIDNIGHT);
        long monthlyOrders = workOrderRepository.countByCompany_IdAndCreatedAtAfter(company.getId(), monthStart);
        if (monthlyOrders >= maxOrders) {
            throw new PlanLimitExceededException(
                "plan.limit.orders");
        }
    }

    public void assertStorageLimit(Company company, long newBytes) {
        if (company == null) {
            return;
        }
        PlanLimitsProperties.PlanLimits limits = resolveLimits(company);
        long maxBytes = limits.getMaxStorageBytes();
        if (maxBytes <= 0) {
            return;
        }
        Long used = attachmentRepository.sumFileSizeByCompanyId(company.getId());
        long usedBytes = used != null ? used : 0L;
        if (usedBytes + Math.max(0, newBytes) > maxBytes) {
            throw new PlanLimitExceededException(
                "plan.limit.storage");
        }
    }

    public PlanLimitsProperties.PlanLimits getLimitsForCompany(Company company) {
        if (company == null) {
            return safeLimits(planLimitsProperties.getFree());
        }
        return safeLimits(resolveLimits(company));
    }

    private PlanLimitsProperties.PlanLimits resolveLimits(Company company) {
        PlanTier tier = company.getPlan() != null ? company.getPlan() : PlanTier.FREE;
        return switch (tier) {
            case PRO -> planLimitsProperties.getPro();
            case TEAM -> planLimitsProperties.getTeam();
            case FREE -> planLimitsProperties.getFree();
        };
    }

    private PlanLimitsProperties.PlanLimits safeLimits(PlanLimitsProperties.PlanLimits limits) {
        return limits != null ? limits : new PlanLimitsProperties.PlanLimits();
    }
}
