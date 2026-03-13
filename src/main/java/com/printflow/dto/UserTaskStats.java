package com.printflow.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class UserTaskStats {
    private static final String NOT_AVAILABLE = "N/A";
    private static final String NEVER = "Nikad";
    // Order statistics
    private int assignedOrders;
    private int completedOrders;
    private int inProgressOrders;
    private int pendingOrders;
    private int overdueOrders;
    
    // Task statistics
    private int assignedTasks;
    private int completedTasks;
    private int inProgressTasks;
    private int pendingTasks;
    private int overdueTasks;
    private int newTasks;
    
    // Time statistics
    private Double averageTaskCompletionHours;
    private Double averageOrderCompletionHours;
    private LocalDateTime lastLogin;
    private LocalDateTime lastTaskCompletion;
    private LocalDateTime lastOrderCompletion;
    
    // Performance metrics
    private Double completionRate;
    private Double onTimeDeliveryRate;
    private Integer totalHoursWorked;
    private Integer totalHoursEstimated;
    
    // Recent activity
    private int ordersCompletedThisWeek;
    private int tasksCompletedThisWeek;
    private int ordersCompletedToday;
    private int tasksCompletedToday;
    
    // Workload
    private int currentWorkload; // Current number of active tasks/orders
    private int maxWorkload; // Maximum recommended workload
    private Double workloadPercentage;
    
    // Konstruktori
    public UserTaskStats() {}
    
    public UserTaskStats(int assignedOrders, int completedOrders, int inProgressOrders, LocalDateTime lastLogin) {
        this.assignedOrders = assignedOrders;
        this.completedOrders = completedOrders;
        this.inProgressOrders = inProgressOrders;
        this.lastLogin = lastLogin;
    }
    
    // Kompletan konstruktor
    public UserTaskStats(int assignedOrders, int completedOrders, int inProgressOrders, 
                         int assignedTasks, int completedTasks, int inProgressTasks,
                         LocalDateTime lastLogin, Double averageTaskCompletionHours) {
        this.assignedOrders = assignedOrders;
        this.completedOrders = completedOrders;
        this.inProgressOrders = inProgressOrders;
        this.assignedTasks = assignedTasks;
        this.completedTasks = completedTasks;
        this.inProgressTasks = inProgressTasks;
        this.lastLogin = lastLogin;
        this.averageTaskCompletionHours = averageTaskCompletionHours;
    }
    
    // ==================== GETTERS I SETTERS ====================
    
    // Order statistics
    public int getAssignedOrders() {
        return assignedOrders;
    }
    
    public void setAssignedOrders(int assignedOrders) {
        this.assignedOrders = assignedOrders;
    }
    
    public int getCompletedOrders() {
        return completedOrders;
    }
    
    public void setCompletedOrders(int completedOrders) {
        this.completedOrders = completedOrders;
    }
    
    public int getInProgressOrders() {
        return inProgressOrders;
    }
    
    public void setInProgressOrders(int inProgressOrders) {
        this.inProgressOrders = inProgressOrders;
    }
    
    public int getPendingOrders() {
        return pendingOrders;
    }
    
    public void setPendingOrders(int pendingOrders) {
        this.pendingOrders = pendingOrders;
    }
    
    public int getOverdueOrders() {
        return overdueOrders;
    }
    
    public void setOverdueOrders(int overdueOrders) {
        this.overdueOrders = overdueOrders;
    }
    
    // Task statistics
    public int getAssignedTasks() {
        return assignedTasks;
    }
    
    public void setAssignedTasks(int assignedTasks) {
        this.assignedTasks = assignedTasks;
    }
    
    public int getCompletedTasks() {
        return completedTasks;
    }
    
    public void setCompletedTasks(int completedTasks) {
        this.completedTasks = completedTasks;
    }
    
    public int getInProgressTasks() {
        return inProgressTasks;
    }
    
    public void setInProgressTasks(int inProgressTasks) {
        this.inProgressTasks = inProgressTasks;
    }
    
    public int getPendingTasks() {
        return pendingTasks;
    }
    
    public void setPendingTasks(int pendingTasks) {
        this.pendingTasks = pendingTasks;
    }
    
    public int getOverdueTasks() {
        return overdueTasks;
    }
    
    public void setOverdueTasks(int overdueTasks) {
        this.overdueTasks = overdueTasks;
    }
    
    public int getNewTasks() {
        return newTasks;
    }
    
    public void setNewTasks(int newTasks) {
        this.newTasks = newTasks;
    }
    
    // Time statistics
    public Double getAverageTaskCompletionHours() {
        return averageTaskCompletionHours;
    }
    
    public void setAverageTaskCompletionHours(Double averageTaskCompletionHours) {
        this.averageTaskCompletionHours = averageTaskCompletionHours;
    }
    
    public Double getAverageOrderCompletionHours() {
        return averageOrderCompletionHours;
    }
    
    public void setAverageOrderCompletionHours(Double averageOrderCompletionHours) {
        this.averageOrderCompletionHours = averageOrderCompletionHours;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public LocalDateTime getLastTaskCompletion() {
        return lastTaskCompletion;
    }
    
    public void setLastTaskCompletion(LocalDateTime lastTaskCompletion) {
        this.lastTaskCompletion = lastTaskCompletion;
    }
    
    public LocalDateTime getLastOrderCompletion() {
        return lastOrderCompletion;
    }
    
    public void setLastOrderCompletion(LocalDateTime lastOrderCompletion) {
        this.lastOrderCompletion = lastOrderCompletion;
    }
    
    // Performance metrics
    public Double getCompletionRate() {
        return completionRate;
    }
    
    public void setCompletionRate(Double completionRate) {
        this.completionRate = completionRate;
    }
    
    public Double getOnTimeDeliveryRate() {
        return onTimeDeliveryRate;
    }
    
    public void setOnTimeDeliveryRate(Double onTimeDeliveryRate) {
        this.onTimeDeliveryRate = onTimeDeliveryRate;
    }
    
    public Integer getTotalHoursWorked() {
        return totalHoursWorked;
    }
    
    public void setTotalHoursWorked(Integer totalHoursWorked) {
        this.totalHoursWorked = totalHoursWorked;
    }
    
    public Integer getTotalHoursEstimated() {
        return totalHoursEstimated;
    }
    
    public void setTotalHoursEstimated(Integer totalHoursEstimated) {
        this.totalHoursEstimated = totalHoursEstimated;
    }
    
    // Recent activity
    public int getOrdersCompletedThisWeek() {
        return ordersCompletedThisWeek;
    }
    
    public void setOrdersCompletedThisWeek(int ordersCompletedThisWeek) {
        this.ordersCompletedThisWeek = ordersCompletedThisWeek;
    }
    
    public int getTasksCompletedThisWeek() {
        return tasksCompletedThisWeek;
    }
    
    public void setTasksCompletedThisWeek(int tasksCompletedThisWeek) {
        this.tasksCompletedThisWeek = tasksCompletedThisWeek;
    }
    
    public int getOrdersCompletedToday() {
        return ordersCompletedToday;
    }
    
    public void setOrdersCompletedToday(int ordersCompletedToday) {
        this.ordersCompletedToday = ordersCompletedToday;
    }
    
    public int getTasksCompletedToday() {
        return tasksCompletedToday;
    }
    
    public void setTasksCompletedToday(int tasksCompletedToday) {
        this.tasksCompletedToday = tasksCompletedToday;
    }
    
    // Workload
    public int getCurrentWorkload() {
        return currentWorkload;
    }
    
    public void setCurrentWorkload(int currentWorkload) {
        this.currentWorkload = currentWorkload;
    }
    
    public int getMaxWorkload() {
        return maxWorkload;
    }
    
    public void setMaxWorkload(int maxWorkload) {
        this.maxWorkload = maxWorkload;
    }
    
    public Double getWorkloadPercentage() {
        return workloadPercentage;
    }
    
    public void setWorkloadPercentage(Double workloadPercentage) {
        this.workloadPercentage = workloadPercentage;
    }
    
    // ==================== HELPER METODE ====================
    
    // Izračunaj ukupan broj zadataka
    public int getTotalTasks() {
        return assignedTasks + completedTasks + inProgressTasks + pendingTasks + newTasks;
    }
    
    // Izračunaj ukupan broj naloga
    public int getTotalOrders() {
        return assignedOrders + completedOrders + inProgressOrders + pendingOrders;
    }
    
    // Izračunaj procenat završenih zadataka
    public double getTaskCompletionPercentage() {
        int total = getTotalTasks();
        if (total == 0) return 0.0;
        return (double) completedTasks / total * 100;
    }
    
    // Izračunaj procenat završenih naloga
    public double getOrderCompletionPercentage() {
        int total = getTotalOrders();
        if (total == 0) return 0.0;
        return (double) completedOrders / total * 100;
    }
    
    // Proveri da li je korisnik aktivan (prijavljen u poslednjih 24h)
    public boolean isCurrentlyActive() {
        if (lastLogin == null) return false;
        long hoursSinceLastLogin = ChronoUnit.HOURS.between(lastLogin, LocalDateTime.now());
        return hoursSinceLastLogin < 24;
    }
    
    // Proveri da li je korisnik prijavljen danas
    public boolean isLoggedInToday() {
        if (lastLogin == null) return false;
        LocalDateTime today = LocalDateTime.now();
        return lastLogin.toLocalDate().equals(today.toLocalDate());
    }
    
    // Izračunaj procenat kašnjenja za zadatke
    public double getTaskOverduePercentage() {
        int totalActiveTasks = assignedTasks + inProgressTasks + pendingTasks;
        if (totalActiveTasks == 0) return 0.0;
        return (double) overdueTasks / totalActiveTasks * 100;
    }
    
    // Izračunaj procenat kašnjenja za naloge
    public double getOrderOverduePercentage() {
        int totalActiveOrders = assignedOrders + inProgressOrders + pendingOrders;
        if (totalActiveOrders == 0) return 0.0;
        return (double) overdueOrders / totalActiveOrders * 100;
    }
    
    // Izračunaj procenat iskorišćenosti (radno vreme)
    public double getEfficiencyPercentage() {
        if (totalHoursEstimated == null || totalHoursEstimated == 0 || 
            totalHoursWorked == null || totalHoursWorked == 0) {
            return 0.0;
        }
        return (double) totalHoursWorked / totalHoursEstimated * 100;
    }
    
    // Izračunaj procenat opterećenja
    public double calculateWorkloadPercentage() {
        if (maxWorkload == 0) return 0.0;
        return (double) currentWorkload / maxWorkload * 100;
    }
    
    // Vrati formatiranu vrednost prosečnog vremena završetka
    public String getAverageTaskCompletionFormatted() {
        if (averageTaskCompletionHours == null) return NOT_AVAILABLE;
        if (averageTaskCompletionHours < 1) {
            return String.format("%.0f min", averageTaskCompletionHours * 60);
        } else if (averageTaskCompletionHours < 24) {
            return String.format("%.1f h", averageTaskCompletionHours);
        } else {
            return String.format("%.1f dana", averageTaskCompletionHours / 24);
        }
    }
    
    // Vrati formatiranu vrednost za poslednju prijavu
    public String getLastLoginFormatted() {
        if (lastLogin == null) return NEVER;
        
        LocalDateTime now = LocalDateTime.now();
        long days = ChronoUnit.DAYS.between(lastLogin, now);
        
        if (days == 0) {
            long hours = ChronoUnit.HOURS.between(lastLogin, now);
            if (hours == 0) {
                long minutes = ChronoUnit.MINUTES.between(lastLogin, now);
                return minutes + " minuta pre";
            }
            return hours + " sati pre";
        } else if (days == 1) {
            return "Juče";
        } else if (days < 7) {
            return days + " dana pre";
        } else if (days < 30) {
            return (days / 7) + " nedelja pre";
        } else {
            return (days / 30) + " meseci pre";
        }
    }
    
    // Vrati klasu za boju opterećenja (za frontend)
    public String getWorkloadClass() {
        if (workloadPercentage == null) {
            workloadPercentage = calculateWorkloadPercentage();
        }
        
        if (workloadPercentage < 50) {
            return "workload-low";
        } else if (workloadPercentage < 80) {
            return "workload-medium";
        } else if (workloadPercentage < 100) {
            return "workload-high";
        } else {
            return "workload-overload";
        }
    }
    
    // Vrati klasu za status aktivnosti
    public String getActivityClass() {
        if (isLoggedInToday()) {
            return "activity-active";
        } else if (isCurrentlyActive()) {
            return "activity-recent";
        } else {
            return "activity-inactive";
        }
    }
    
    @Override
    public String toString() {
        return "UserTaskStats{" +
                "assignedOrders=" + assignedOrders +
                ", completedOrders=" + completedOrders +
                ", inProgressOrders=" + inProgressOrders +
                ", assignedTasks=" + assignedTasks +
                ", completedTasks=" + completedTasks +
                ", inProgressTasks=" + inProgressTasks +
                ", lastLogin=" + lastLogin +
                '}';
    }
}
