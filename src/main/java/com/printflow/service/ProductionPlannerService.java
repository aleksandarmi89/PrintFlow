package com.printflow.service;

import com.printflow.dto.PlannerOrderDTO;
import com.printflow.dto.PlannerStatsDTO;
import com.printflow.dto.PlannerLoadDTO;
import com.printflow.dto.PlannerWorkerLoadDTO;
import com.printflow.dto.PlannerProfitDTO;
import com.printflow.dto.PlannerStatusPointDTO;
import com.printflow.dto.PlannerHeatmapDTO;
import com.printflow.dto.PlannerProfitTrendDTO;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.PrintType;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductionPlannerService {
    private final WorkOrderRepository workOrderRepository;
    private final TenantContextService tenantContextService;
    private final UserService userService;
    private final com.printflow.repository.AuditLogRepository auditLogRepository;
    private final double marginRateDtf;
    private final double marginRateLaser;
    private final double marginRateOther;

    public ProductionPlannerService(WorkOrderRepository workOrderRepository,
                                    TenantContextService tenantContextService,
                                    UserService userService,
                                    com.printflow.repository.AuditLogRepository auditLogRepository,
                                    @Value("${app.profit.margin-rate.dtf:0.35}") double marginRateDtf,
                                    @Value("${app.profit.margin-rate.laser:0.3}") double marginRateLaser,
                                    @Value("${app.profit.margin-rate.other:0.25}") double marginRateOther) {
        this.workOrderRepository = workOrderRepository;
        this.tenantContextService = tenantContextService;
        this.userService = userService;
        this.auditLogRepository = auditLogRepository;
        this.marginRateDtf = marginRateDtf;
        this.marginRateLaser = marginRateLaser;
        this.marginRateOther = marginRateOther;
    }

    public PlannerStatsDTO getStats(java.time.YearMonth month) {
        List<WorkOrder> orders = tenantContextService.isSuperAdmin()
            ? workOrderRepository.findAll()
            : workOrderRepository.findByCompany_Id(tenantContextService.requireCompanyId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusDays(7);
        EnumSet<OrderStatus> closed = EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);

        int totalOpen = 0;
        int overdue = 0;
        int dueSoon = 0;
        int unassigned = 0;
        int inDesign = 0;
        int inPrint = 0;
        int assignedActive = 0;
        double paidMonth = 0.0;
        double outstanding = 0.0;
        int createdThisMonth = 0;

        java.time.YearMonth target = month != null ? month : java.time.YearMonth.from(now);
        LocalDateTime monthStart = target.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = target.atEndOfMonth().atTime(LocalTime.MAX);

        for (WorkOrder order : orders) {
            if (order.getStatus() != null && !closed.contains(order.getStatus())) {
                totalOpen++;
            }
            if (order.getDeadline() != null && order.getStatus() != null && !closed.contains(order.getStatus())) {
                if (order.getDeadline().isBefore(now)) {
                    overdue++;
                } else if (!order.getDeadline().isAfter(soon)) {
                    dueSoon++;
                }
            }
            if (order.getAssignedTo() == null) {
                unassigned++;
            } else if (order.getStatus() != null && !closed.contains(order.getStatus())) {
                assignedActive++;
            }
            if (order.getStatus() == OrderStatus.IN_DESIGN || order.getStatus() == OrderStatus.WAITING_CLIENT_APPROVAL) {
                inDesign++;
            }
            if (order.getStatus() == OrderStatus.IN_PRINT || order.getStatus() == OrderStatus.WAITING_QUALITY_CHECK) {
                inPrint++;
            }
            if (order.getCreatedAt() != null && !order.getCreatedAt().isBefore(monthStart) && !order.getCreatedAt().isAfter(monthEnd)) {
                createdThisMonth++;
                if (Boolean.TRUE.equals(order.getPaid()) && order.getPrice() != null) {
                    paidMonth += order.getPrice();
                } else if (!Boolean.TRUE.equals(order.getPaid()) && order.getPrice() != null) {
                    outstanding += order.getPrice();
                }
            }
        }

        int totalWorkers = userService.getWorkers().size();
        int availableWorkers = userService.getAvailableWorkers().size();
        double utilization = totalWorkers == 0 ? 0.0 : Math.min(1.0, assignedActive / (double) totalWorkers);

        PlannerStatsDTO stats = new PlannerStatsDTO();
        stats.setTotalOpenOrders(totalOpen);
        stats.setOverdueOrders(overdue);
        stats.setDueSoonOrders(dueSoon);
        stats.setUnassignedOrders(unassigned);
        stats.setInDesignOrders(inDesign);
        stats.setInPrintOrders(inPrint);
        stats.setTotalWorkers(totalWorkers);
        stats.setAvailableWorkers(availableWorkers);
        stats.setCapacityUtilization(utilization);
        stats.setPaidRevenueMonth(paidMonth);
        stats.setOutstandingRevenue(outstanding);
        stats.setCreatedThisMonth(createdThisMonth);
        return stats;
    }

    public PlannerStatsDTO getStats() {
        return getStats(null);
    }

    public List<com.printflow.dto.UserDTO> getWorkers() {
        return userService.getWorkers();
    }

    private List<WorkOrder> getOrdersForPlanner(Long workerId) {
        if (workerId != null) {
            return tenantContextService.isSuperAdmin()
                ? workOrderRepository.findByAssignedToId(workerId)
                : workOrderRepository.findByAssignedToIdAndCompany_Id(workerId, tenantContextService.requireCompanyId());
        }
        return tenantContextService.isSuperAdmin()
            ? workOrderRepository.findAll()
            : workOrderRepository.findByCompany_Id(tenantContextService.requireCompanyId());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<PlannerOrderDTO> getDueSoonOrders(int limit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusDays(7);
        EnumSet<OrderStatus> closed = EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
        List<OrderStatus> excluded = new ArrayList<>(closed);
        PageRequest page = PageRequest.of(0, Math.max(1, limit));
        List<WorkOrder> due = tenantContextService.isSuperAdmin()
            ? workOrderRepository.findDueSoonOrders(now, soon, excluded, page)
            : workOrderRepository.findDueSoonOrdersByCompany(tenantContextService.requireCompanyId(), now, soon, excluded, page);

        List<PlannerOrderDTO> result = new ArrayList<>();
        for (WorkOrder order : due) {
            result.add(new PlannerOrderDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getTitle(),
                order.getClient() != null ? order.getClient().getCompanyName() : null,
                order.getStatus() != null ? order.getStatus().name() : null,
                order.getDeadline(),
                order.getAssignedTo() != null ? order.getAssignedTo().getFullName() : null,
                order.getPrice()
            ));
        }
        return result;
    }

    public List<PlannerLoadDTO> getDailyLoad(int days, Long workerId) {
        List<WorkOrder> orders = getOrdersForPlanner(workerId);
        LocalDateTime now = LocalDateTime.now();
        EnumSet<OrderStatus> closed = EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
        List<PlannerLoadDTO> list = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDateTime dayStart = now.toLocalDate().plusDays(i).atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);
            int count = 0;
            for (WorkOrder order : orders) {
                if (order.getDeadline() == null || order.getStatus() == null || closed.contains(order.getStatus())) {
                    continue;
                }
                if (!order.getDeadline().isBefore(dayStart) && !order.getDeadline().isAfter(dayEnd)) {
                    count++;
                }
            }
            String label = dayStart.getDayOfWeek().toString().substring(0, 3);
            list.add(new PlannerLoadDTO(label, count));
        }
        return list;
    }

    public List<PlannerLoadDTO> getDailyLoad(int days) {
        return getDailyLoad(days, null);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<PlannerWorkerLoadDTO> getWorkerLoad(int limit) {
        EnumSet<OrderStatus> closed = EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
        List<OrderStatus> excluded = new ArrayList<>(closed);
        PageRequest page = PageRequest.of(0, Math.max(1, limit));
        return tenantContextService.isSuperAdmin()
            ? workOrderRepository.findWorkerLoad(excluded, page)
            : workOrderRepository.findWorkerLoadByCompany(tenantContextService.requireCompanyId(), excluded, page);
    }

    public List<PlannerProfitDTO> getProfitByPrintType(String filter, java.time.YearMonth month, Long workerId) {
        List<WorkOrder> orders = getOrdersForPlanner(workerId);
        java.util.Map<PrintType, Double> gross = new java.util.EnumMap<>(PrintType.class);
        java.util.Map<PrintType, Double> margin = new java.util.EnumMap<>(PrintType.class);
        for (PrintType t : PrintType.values()) {
            gross.put(t, 0.0);
            margin.put(t, 0.0);
        }
        LocalDateTime monthStart = null;
        LocalDateTime monthEnd = null;
        if (month != null) {
            monthStart = month.atDay(1).atStartOfDay();
            monthEnd = month.atEndOfMonth().atTime(LocalTime.MAX);
        }
        for (WorkOrder order : orders) {
            if (order.getPrice() == null) {
                continue;
            }
            if (monthStart != null && monthEnd != null) {
                if (order.getCreatedAt() == null || order.getCreatedAt().isBefore(monthStart) || order.getCreatedAt().isAfter(monthEnd)) {
                    continue;
                }
            }
            PrintType type = order.getPrintType() != null ? order.getPrintType() : PrintType.OTHER;
            gross.put(type, gross.get(type) + order.getPrice());
            double cost = order.getCost() != null ? order.getCost() : 0.0;
            margin.put(type, margin.get(type) + (order.getPrice() - cost));
        }
        List<PlannerProfitDTO> result = new ArrayList<>();
        for (PrintType type : PrintType.values()) {
            if (filter != null && !filter.isBlank() && !filter.equalsIgnoreCase(type.name())) {
                continue;
            }
            double g = gross.get(type);
            double m = margin.get(type);
            result.add(new PlannerProfitDTO(type.name(), g, m));
        }
        return result;
    }

    public List<PlannerProfitDTO> getProfitByPrintType(String filter, java.time.YearMonth month) {
        return getProfitByPrintType(filter, month, null);
    }

    public double getMaxGross(java.util.List<PlannerProfitDTO> rows) {
        double max = 0.0;
        for (PlannerProfitDTO row : rows) {
            if (row.getGross() > max) {
                max = row.getGross();
            }
        }
        return max;
    }

    public double getMaxGrossLog(java.util.List<PlannerProfitDTO> rows) {
        double max = 0.0;
        for (PlannerProfitDTO row : rows) {
            double value = Math.log1p(row.getGross());
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    public int getMaxLoadCount(List<PlannerLoadDTO> rows) {
        int max = 0;
        for (PlannerLoadDTO row : rows) {
            if (row.getCount() > max) {
                max = row.getCount();
            }
        }
        return max;
    }

    public long getMaxWorkerLoadCount(List<PlannerWorkerLoadDTO> rows) {
        long max = 0;
        for (PlannerWorkerLoadDTO row : rows) {
            if (row.getCount() > max) {
                max = row.getCount();
            }
        }
        return max;
    }

    public int getMaxStatusCount(List<PlannerStatusPointDTO> rows) {
        int max = 0;
        for (PlannerStatusPointDTO row : rows) {
            max = Math.max(max, row.getNewCount());
            max = Math.max(max, row.getDesignCount());
            max = Math.max(max, row.getPrintCount());
            max = Math.max(max, row.getReadyCount());
        }
        return max;
    }

    public int getMaxHeatmapCount(List<PlannerHeatmapDTO> rows) {
        int max = 0;
        for (PlannerHeatmapDTO row : rows) {
            if (row.getCount() > max) {
                max = row.getCount();
            }
        }
        return max;
    }

    public List<PlannerProfitDTO> getProfitByPrintType(String filter) {
        return getProfitByPrintType(filter, null, null);
    }

    public List<PlannerStatusPointDTO> getStatusTimeline(int days, java.time.YearMonth month, Long workerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now;
        if (month != null) {
            end = month.atEndOfMonth().atTime(LocalTime.MAX);
        }
        LocalDateTime start = end.toLocalDate().minusDays(days - 1).atStartOfDay();
        List<com.printflow.entity.AuditLog> logs = tenantContextService.isSuperAdmin()
            ? auditLogRepository.findByEntityTypeAndActionAndCreatedAtBetween(
                "WorkOrder", com.printflow.entity.enums.AuditAction.STATUS_CHANGE, start, end)
            : auditLogRepository.findByEntityTypeAndActionAndCompany_IdAndCreatedAtBetween(
                "WorkOrder", com.printflow.entity.enums.AuditAction.STATUS_CHANGE, tenantContextService.requireCompanyId(), start, end);
        java.util.Set<Long> allowedOrderIds = null;
        if (workerId != null) {
            allowedOrderIds = getOrdersForPlanner(workerId).stream().map(WorkOrder::getId).collect(Collectors.toSet());
        }

        List<PlannerStatusPointDTO> points = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime dayStart = end.toLocalDate().minusDays(i).atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);
            int newCount = 0;
            int designCount = 0;
            int printCount = 0;
            int readyCount = 0;
            for (com.printflow.entity.AuditLog log : logs) {
                if (log.getCreatedAt() == null || log.getCreatedAt().isBefore(dayStart) || log.getCreatedAt().isAfter(dayEnd)) {
                    continue;
                }
                if (allowedOrderIds != null) {
                    if (log.getEntityId() == null || !allowedOrderIds.contains(log.getEntityId())) {
                        continue;
                    }
                }
                if (log.getNewValue() == null) {
                    continue;
                }
                String status = log.getNewValue();
                switch (status) {
                    case "NEW" -> newCount++;
                    case "IN_DESIGN", "WAITING_CLIENT_APPROVAL" -> designCount++;
                    case "IN_PRINT", "WAITING_QUALITY_CHECK" -> printCount++;
                    case "READY_FOR_DELIVERY", "SENT" -> readyCount++;
                    default -> {
                    }
                }
            }
            String label = dayStart.getDayOfWeek().toString().substring(0, 3);
            points.add(new PlannerStatusPointDTO(label, newCount, designCount, printCount, readyCount));
        }
        return points;
    }

    public List<PlannerStatusPointDTO> getStatusTimeline(int days, java.time.YearMonth month) {
        return getStatusTimeline(days, month, null);
    }

    public List<PlannerStatusPointDTO> getStatusTimeline(int days) {
        return getStatusTimeline(days, null, null);
    }

    public List<PlannerHeatmapDTO> getDailyHeatmap(java.time.YearMonth month, Long workerId) {
        java.time.YearMonth target = month != null ? month : java.time.YearMonth.now();
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.atEndOfMonth().atTime(LocalTime.MAX);
        List<com.printflow.entity.AuditLog> logs = tenantContextService.isSuperAdmin()
            ? auditLogRepository.findByEntityTypeAndActionAndCreatedAtBetween(
                "WorkOrder", com.printflow.entity.enums.AuditAction.STATUS_CHANGE, start, end)
            : auditLogRepository.findByEntityTypeAndActionAndCompany_IdAndCreatedAtBetween(
                "WorkOrder", com.printflow.entity.enums.AuditAction.STATUS_CHANGE, tenantContextService.requireCompanyId(), start, end);
        java.util.Set<Long> allowedOrderIds = null;
        if (workerId != null) {
            allowedOrderIds = getOrdersForPlanner(workerId).stream().map(WorkOrder::getId).collect(Collectors.toSet());
        }
        java.util.Map<java.time.LocalDate, Integer> counts = new java.util.HashMap<>();
        for (com.printflow.entity.AuditLog log : logs) {
            if (log.getCreatedAt() == null) {
                continue;
            }
            if (allowedOrderIds != null) {
                if (log.getEntityId() == null || !allowedOrderIds.contains(log.getEntityId())) {
                    continue;
                }
            }
            java.time.LocalDate date = log.getCreatedAt().toLocalDate();
            counts.put(date, counts.getOrDefault(date, 0) + 1);
        }
        List<PlannerHeatmapDTO> result = new ArrayList<>();
        int daysInMonth = target.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            java.time.LocalDate date = target.atDay(day);
            int count = counts.getOrDefault(date, 0);
            result.add(new PlannerHeatmapDTO(date, day, count));
        }
        return result;
    }

    public List<PlannerHeatmapDTO> getDeadlineHeatmap(java.time.YearMonth month, Long workerId) {
        java.time.YearMonth target = month != null ? month : java.time.YearMonth.now();
        List<WorkOrder> orders = getOrdersForPlanner(workerId);
        java.util.Map<java.time.LocalDate, Integer> counts = new java.util.HashMap<>();
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.atEndOfMonth().atTime(LocalTime.MAX);
        for (WorkOrder order : orders) {
            if (order.getDeadline() == null) {
                continue;
            }
            if (order.getDeadline().isBefore(start) || order.getDeadline().isAfter(end)) {
                continue;
            }
            java.time.LocalDate date = order.getDeadline().toLocalDate();
            counts.put(date, counts.getOrDefault(date, 0) + 1);
        }
        List<PlannerHeatmapDTO> result = new ArrayList<>();
        int daysInMonth = target.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            java.time.LocalDate date = target.atDay(day);
            int count = counts.getOrDefault(date, 0);
            result.add(new PlannerHeatmapDTO(date, day, count));
        }
        return result;
    }

    public List<PlannerProfitTrendDTO> getProfitTrend(int months, java.time.YearMonth endMonth, String printType, Long workerId) {
        java.time.YearMonth end = endMonth != null ? endMonth : java.time.YearMonth.now();
        int count = Math.max(3, Math.min(months, 24));
        List<WorkOrder> orders = getOrdersForPlanner(workerId);
        List<PlannerProfitTrendDTO> rows = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            java.time.YearMonth current = end.minusMonths(i);
            LocalDateTime start = current.atDay(1).atStartOfDay();
            LocalDateTime finish = current.atEndOfMonth().atTime(LocalTime.MAX);
            double gross = 0.0;
            double margin = 0.0;
            double cost = 0.0;
            for (WorkOrder order : orders) {
                if (order.getPrice() == null || order.getCreatedAt() == null) {
                    continue;
                }
                if (order.getCreatedAt().isBefore(start) || order.getCreatedAt().isAfter(finish)) {
                    continue;
                }
                PrintType type = order.getPrintType() != null ? order.getPrintType() : PrintType.OTHER;
                if (printType != null && !printType.isBlank() && !printType.equalsIgnoreCase(type.name())) {
                    continue;
                }
                double price = order.getPrice();
                double c = order.getCost() != null ? order.getCost() : 0.0;
                gross += price;
                cost += c;
                margin += (price - c);
            }
            String label = current.getMonth().toString().substring(0, 3);
            rows.add(new PlannerProfitTrendDTO(label, gross, margin, cost));
        }
        return rows;
    }
}
