package com.printflow.service;

import com.printflow.dto.DashboardStatsDTO;
import com.printflow.dto.WorkerDashboardStatsDTO;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardService {
    
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TaskRepository taskRepository;
    private final TenantContextService tenantContextService;
    
    private static final long DASHBOARD_TTL_MS = 15000L;
    
    // Dve odvojene cache mape za dva tipa dashboard-a
    private final ConcurrentHashMap<Long, CachedDashboardStats> dashboardCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedWorkerStats> workerDashboardCache = new ConcurrentHashMap<>();
    
    public DashboardService(ClientRepository clientRepository, UserRepository userRepository,
            WorkOrderRepository workOrderRepository, TaskRepository taskRepository, 
            TenantContextService tenantContextService) {
        super();
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.taskRepository = taskRepository;
        this.tenantContextService = tenantContextService;
    }

    public DashboardStatsDTO getDashboardStats() {
        Long companyId = tenantContextService.isSuperAdmin() ? -1L : tenantContextService.requireCompanyId();
        
        // Provera cache-a za admin dashboard
        CachedDashboardStats cached = dashboardCache.get(companyId);
        long nowMs = System.currentTimeMillis();
        
        if (cached != null && (nowMs - cached.timestampMs) < DASHBOARD_TTL_MS) {
            return cached.stats;
        }
        
        // Ako nije u cache-u ili je istekao, izračunaj
        DashboardStatsDTO stats = calculateDashboardStats(companyId);
        
        // Sačuvaj u cache
        dashboardCache.put(companyId, new CachedDashboardStats(stats, nowMs));
        
        return stats;
    }
    
    private DashboardStatsDTO calculateDashboardStats(Long companyId) {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        
        stats.setTotalClients(tenantContextService.isSuperAdmin()
            ? clientRepository.countActiveClients()
            : clientRepository.countActiveClientsByCompany(companyId));
        
        stats.setTotalUsers(tenantContextService.isSuperAdmin()
            ? userRepository.countByActiveTrue()
            : userRepository.findByCompany_IdAndActiveTrue(companyId, org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
        
        stats.setTotalOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.count()
            : workOrderRepository.countByCompany_Id(companyId));
        
        stats.setNewOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByStatus(OrderStatus.NEW)
            : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.NEW));
        
        stats.setInDesignOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByStatus(OrderStatus.IN_DESIGN)
            : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.IN_DESIGN));
        
        stats.setWaitingApprovalOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByStatus(OrderStatus.WAITING_CLIENT_APPROVAL)
            : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.WAITING_CLIENT_APPROVAL));
        
        stats.setInPrintOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByStatus(OrderStatus.IN_PRINT)
            : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.IN_PRINT));
        
        stats.setReadyForDeliveryOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByStatus(OrderStatus.READY_FOR_DELIVERY)
            : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.READY_FOR_DELIVERY));
        
        stats.setCompletedOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByStatus(OrderStatus.COMPLETED)
            : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.COMPLETED));
        
        long activeOrders = stats.getTotalOrders() - 
                          (tenantContextService.isSuperAdmin()
                              ? workOrderRepository.countByStatus(OrderStatus.COMPLETED)
                              : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.COMPLETED)) -
                          (tenantContextService.isSuperAdmin()
                              ? workOrderRepository.countByStatus(OrderStatus.CANCELLED)
                              : workOrderRepository.countByStatusAndCompanyId(companyId, OrderStatus.CANCELLED));
        stats.setActiveOrders(activeOrders);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth());
        LocalDateTime endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth());
        
        List<OrderStatus> overdueExcludedStatuses = List.of(
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED,
            OrderStatus.SENT
        );
        
        long overdueOrders = tenantContextService.isSuperAdmin()
            ? workOrderRepository.countOverdueOrders(now, overdueExcludedStatuses)
            : workOrderRepository.countOverdueOrdersByCompany(companyId, now, overdueExcludedStatuses);
        stats.setOverdueOrders(overdueOrders);
        
        double monthlyRevenue = tenantContextService.isSuperAdmin()
            ? workOrderRepository.sumPriceByStatusAndDateRange(OrderStatus.COMPLETED, startOfMonth, endOfMonth)
            : workOrderRepository.sumPriceByStatusAndDateRangeAndCompany(companyId, OrderStatus.COMPLETED, startOfMonth, endOfMonth);
        stats.setMonthlyRevenue(monthlyRevenue);
        
        List<OrderStatus> pendingExcludedStatuses = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
        double pendingRevenue = tenantContextService.isSuperAdmin()
            ? workOrderRepository.sumPriceByStatusNotIn(pendingExcludedStatuses)
            : workOrderRepository.sumPriceByStatusNotInAndCompany(companyId, pendingExcludedStatuses);
        stats.setPendingRevenue(pendingRevenue);
        
        return stats;
    }
    
    public WorkerDashboardStatsDTO getWorkerDashboardStats(Long userId) {
        Long companyId = tenantContextService.isSuperAdmin() ? null : tenantContextService.requireCompanyId();
        
        // Kreiranje jedinstvenog ključa za cache (kombinacija companyId + userId)
        String cacheKey;
        if (companyId == null) {
            cacheKey = "superadmin_" + userId;
        } else {
            cacheKey = companyId + "_" + userId;
        }
        
        // Provera cache-a za worker dashboard
        CachedWorkerStats cached = workerDashboardCache.get(cacheKey);
        long nowMs = System.currentTimeMillis();
        
        if (cached != null && (nowMs - cached.timestampMs) < DASHBOARD_TTL_MS) {
            return cached.stats;
        }
        
        // Ako nije u cache-u ili je istekao, izračunaj
        WorkerDashboardStatsDTO stats = calculateWorkerDashboardStats(userId, companyId);
        
        // Sačuvaj u cache
        workerDashboardCache.put(cacheKey, new CachedWorkerStats(stats, nowMs));
        
        return stats;
    }
    
    private WorkerDashboardStatsDTO calculateWorkerDashboardStats(Long userId, Long companyId) {
        WorkerDashboardStatsDTO stats = new WorkerDashboardStatsDTO();
        
        // Task statistics
        stats.setTotalTasks((int) (tenantContextService.isSuperAdmin()
            ? taskRepository.countByAssignedToId(userId)
            : taskRepository.countByAssignedToIdAndCompany_Id(userId, companyId)));
        
        stats.setCompletedTasks((int) (tenantContextService.isSuperAdmin()
            ? taskRepository.countByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED)
            : taskRepository.countByAssignedToIdAndStatusAndCompany_Id(userId, TaskStatus.COMPLETED, companyId)));
        
        stats.setInProgressTasks((int) (tenantContextService.isSuperAdmin()
            ? taskRepository.countByAssignedToIdAndStatus(userId, TaskStatus.IN_PROGRESS)
            : taskRepository.countByAssignedToIdAndStatusAndCompany_Id(userId, TaskStatus.IN_PROGRESS, companyId)));
        
        stats.setOverdueTasks((int) (tenantContextService.isSuperAdmin()
            ? taskRepository.countOverdueByAssignedToId(userId, LocalDateTime.now())
            : taskRepository.countOverdueByAssignedToIdAndCompany(userId, companyId, LocalDateTime.now())));
        
        // Work order statistics
        stats.setAssignedOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByAssignedToId(userId)
            : workOrderRepository.countByAssignedToIdAndCompany_Id(userId, companyId));
        
        stats.setCompletedOrders(tenantContextService.isSuperAdmin()
            ? workOrderRepository.countByAssignedToIdAndStatus(userId, OrderStatus.COMPLETED)
            : workOrderRepository.countByAssignedToIdAndStatusAndCompany_Id(userId, OrderStatus.COMPLETED, companyId));
        
        // Time tracking
        stats.setTotalHours(taskRepository.sumEstimatedHoursByAssignedToId(userId));
        stats.setCompletedHours(taskRepository.sumEstimatedHoursByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED));
        
        // Today's stats
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        
        stats.setCompletedToday((int) taskRepository.countCompletedByAssignedToIdBetween(userId, todayStart, todayEnd));
        
        return stats;
    }
    
    // Cache klase za dva tipa dashboard-a
    private static class CachedDashboardStats {
        private final DashboardStatsDTO stats;
        private final long timestampMs;

        private CachedDashboardStats(DashboardStatsDTO stats, long timestampMs) {
            this.stats = stats;
            this.timestampMs = timestampMs;
        }
    }
    
    private static class CachedWorkerStats {
        private final WorkerDashboardStatsDTO stats;
        private final long timestampMs;

        private CachedWorkerStats(WorkerDashboardStatsDTO stats, long timestampMs) {
            this.stats = stats;
            this.timestampMs = timestampMs;
        }
    }
    
    // Metode za čišćenje cache-a (opciono, korisno za testiranje)
    public void clearDashboardCache() {
        dashboardCache.clear();
    }
    
    public void clearWorkerDashboardCache() {
        workerDashboardCache.clear();
    }
    
    public void clearAllCache() {
        dashboardCache.clear();
        workerDashboardCache.clear();
    }
    
    // Metode za praćenje stanja cache-a (opciono, za debug)
    public int getDashboardCacheSize() {
        return dashboardCache.size();
    }
    
    public int getWorkerDashboardCacheSize() {
        return workerDashboardCache.size();
    }
}