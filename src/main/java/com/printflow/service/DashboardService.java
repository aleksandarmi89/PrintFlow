package com.printflow.service;

import com.printflow.dto.*;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.ClientRepository;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TaskRepository taskRepository;
    
    
    public DashboardService(ClientRepository clientRepository, UserRepository userRepository,
			WorkOrderRepository workOrderRepository, TaskRepository taskRepository) {
		super();
		this.clientRepository = clientRepository;
		this.userRepository = userRepository;
		this.workOrderRepository = workOrderRepository;
		this.taskRepository = taskRepository;
	}

	public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        
        stats.setTotalClients(clientRepository.countActiveClients());
        stats.setTotalUsers(userRepository.countByActiveTrue());
        stats.setTotalOrders(workOrderRepository.count());
        
        stats.setNewOrders(workOrderRepository.countByStatus(OrderStatus.NEW));
        stats.setInDesignOrders(workOrderRepository.countByStatus(OrderStatus.IN_DESIGN));
        stats.setWaitingApprovalOrders(workOrderRepository.countByStatus(OrderStatus.WAITING_CLIENT_APPROVAL));
        stats.setInPrintOrders(workOrderRepository.countByStatus(OrderStatus.IN_PRINT));
        stats.setReadyForDeliveryOrders(workOrderRepository.countByStatus(OrderStatus.READY_FOR_DELIVERY));
        stats.setCompletedOrders(workOrderRepository.countByStatus(OrderStatus.COMPLETED));
        
        long activeOrders = stats.getTotalOrders() - 
                          workOrderRepository.countByStatus(OrderStatus.COMPLETED) -
                          workOrderRepository.countByStatus(OrderStatus.CANCELLED);
        stats.setActiveOrders(activeOrders);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth());
        LocalDateTime endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth());
        
        long overdueOrders = workOrderRepository.findOverdueOrders(
            now, 
            List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.SENT)
        ).size();
        stats.setOverdueOrders(overdueOrders);
        
        double monthlyRevenue = workOrderRepository.findByDateRange(startOfMonth, endOfMonth).stream()
            .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
            .mapToDouble(order -> order.getPrice() != null ? order.getPrice() : 0)
            .sum();
        stats.setMonthlyRevenue(monthlyRevenue);
        
        double pendingRevenue = workOrderRepository.findAll().stream()
            .filter(order -> order.getStatus() != OrderStatus.COMPLETED && 
                           order.getStatus() != OrderStatus.CANCELLED)
            .mapToDouble(order -> order.getPrice() != null ? order.getPrice() : 0)
            .sum();
        stats.setPendingRevenue(pendingRevenue);
        
        return stats;
    }
    
    // NOVA METODA: Worker Dashboard Stats
    public WorkerDashboardStatsDTO getWorkerDashboardStats(Long userId) {
        WorkerDashboardStatsDTO stats = new WorkerDashboardStatsDTO();
        
        // Task statistics
        List<com.printflow.entity.Task> tasks = taskRepository.findByAssignedToId(userId);
        stats.setTotalTasks(tasks.size());
        stats.setCompletedTasks((int) tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
            .count());
        stats.setInProgressTasks((int) tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
            .count());
        stats.setOverdueTasks((int) tasks.stream()
            .filter(t -> t.getDueDate() != null && 
                       t.getDueDate().isBefore(LocalDateTime.now()) && 
                       t.getStatus() != TaskStatus.COMPLETED)
            .count());
        
        // Work order statistics
        stats.setAssignedOrders(workOrderRepository.countByAssignedToId(userId));
        stats.setCompletedOrders(workOrderRepository.countByAssignedToIdAndStatus(userId, OrderStatus.COMPLETED));
        
        // Time tracking
        double totalHours = tasks.stream()
            .mapToDouble(t -> t.getEstimatedHours() != null ? t.getEstimatedHours() : 0)
            .sum();
        stats.setTotalHours(totalHours);
        
        double completedHours = tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
            .mapToDouble(t -> t.getEstimatedHours() != null ? t.getEstimatedHours() : 0)
            .sum();
        stats.setCompletedHours(completedHours);
        
        // Today's stats
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        
        int completedToday = (int) tasks.stream()
            .filter(t -> t.getCompletedAt() != null && 
                       t.getCompletedAt().isAfter(todayStart) && 
                       t.getCompletedAt().isBefore(todayEnd))
            .count();
        stats.setCompletedToday(completedToday);
        
        return stats;
    }
   
}