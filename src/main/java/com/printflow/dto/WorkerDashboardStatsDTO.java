package com.printflow.dto;

public class WorkerDashboardStatsDTO {
    private int totalTasks;  // DODATO
    private int completedTasks;  // DODATO
    private int inProgressTasks;  // DODATO
    private int overdueTasks;
    private int completedToday;
    private double totalHours;  // DODATO
    private double completedHours;  // DODATO
    private int assignedOrders;  // DODATO
    private int completedOrders;  // DODATO
    private TaskStatisticsDTO taskStats;
    private Long tasksDueToday;
    private Long recentlyCompleted;
    private Double averageCompletionHours;
    
    // Polja koja možda želite zadržati
    private int pendingApproval;
    private double weeklyProductivity;
    private double averageCompletionTime;
    private int activeTimers;
    private int totalAssignedTasks;  // Možda je ovo isto što i totalTasks?
    
    // Konstruktori
    public WorkerDashboardStatsDTO() {}
    
   
    
    public WorkerDashboardStatsDTO(int totalTasks, int completedTasks, int inProgressTasks, int overdueTasks,
			int completedToday, double totalHours, double completedHours, int assignedOrders, int completedOrders,
			TaskStatisticsDTO taskStats, Long tasksDueToday, Long recentlyCompleted, Double averageCompletionHours,
			int pendingApproval, double weeklyProductivity, double averageCompletionTime, int activeTimers,
			int totalAssignedTasks) {
		
		this.totalTasks = totalTasks;
		this.completedTasks = completedTasks;
		this.inProgressTasks = inProgressTasks;
		this.overdueTasks = overdueTasks;
		this.completedToday = completedToday;
		this.totalHours = totalHours;
		this.completedHours = completedHours;
		this.assignedOrders = assignedOrders;
		this.completedOrders = completedOrders;
		this.taskStats = taskStats;
		this.tasksDueToday = tasksDueToday;
		this.recentlyCompleted = recentlyCompleted;
		this.averageCompletionHours = averageCompletionHours;
		this.pendingApproval = pendingApproval;
		this.weeklyProductivity = weeklyProductivity;
		this.averageCompletionTime = averageCompletionTime;
		this.activeTimers = activeTimers;
		this.totalAssignedTasks = totalAssignedTasks;
	}



	public TaskStatisticsDTO getTaskStats() {
		return taskStats;
	}



	public void setTaskStats(TaskStatisticsDTO taskStats) {
		this.taskStats = taskStats;
	}



	public Long getTasksDueToday() {
		return tasksDueToday;
	}



	public void setTasksDueToday(Long tasksDueToday) {
		this.tasksDueToday = tasksDueToday;
	}



	public Long getRecentlyCompleted() {
		return recentlyCompleted;
	}



	public void setRecentlyCompleted(Long recentlyCompleted) {
		this.recentlyCompleted = recentlyCompleted;
	}



	public Double getAverageCompletionHours() {
		return averageCompletionHours;
	}



	public void setAverageCompletionHours(Double averageCompletionHours) {
		this.averageCompletionHours = averageCompletionHours;
	}



	// Getters i Setters za nova polja
    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }
    
    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
    
    public int getInProgressTasks() { return inProgressTasks; }
    public void setInProgressTasks(int inProgressTasks) { this.inProgressTasks = inProgressTasks; }
    
    public double getTotalHours() { return totalHours; }
    public void setTotalHours(double totalHours) { this.totalHours = totalHours; }
    
    public double getCompletedHours() { return completedHours; }
    public void setCompletedHours(double completedHours) { this.completedHours = completedHours; }
    
    public int getAssignedOrders() { return assignedOrders; }
    public void setAssignedOrders(int assignedOrders) { this.assignedOrders = assignedOrders; }
    
    public int getCompletedOrders() { return completedOrders; }
    public void setCompletedOrders(int completedOrders) { this.completedOrders = completedOrders; }
    
    // Ostali getters i setters (stara polja)
    public int getOverdueTasks() { return overdueTasks; }
    public void setOverdueTasks(int overdueTasks) { this.overdueTasks = overdueTasks; }
    
    public int getCompletedToday() { return completedToday; }
    public void setCompletedToday(int completedToday) { this.completedToday = completedToday; }
    
    public int getPendingApproval() { return pendingApproval; }
    public void setPendingApproval(int pendingApproval) { this.pendingApproval = pendingApproval; }
    
    public double getWeeklyProductivity() { return weeklyProductivity; }
    public void setWeeklyProductivity(double weeklyProductivity) { this.weeklyProductivity = weeklyProductivity; }
    
    public double getAverageCompletionTime() { return averageCompletionTime; }
    public void setAverageCompletionTime(double averageCompletionTime) { this.averageCompletionTime = averageCompletionTime; }
    
    public int getActiveTimers() { return activeTimers; }
    public void setActiveTimers(int activeTimers) { this.activeTimers = activeTimers; }
    
    public int getTotalAssignedTasks() { return totalAssignedTasks; }
    public void setTotalAssignedTasks(int totalAssignedTasks) { this.totalAssignedTasks = totalAssignedTasks; }
    
    // Kompatibilnost sa starim poljem totalHoursLogged
    public int getTotalHoursLogged() { return (int) totalHours; }
    public void setTotalHoursLogged(int totalHoursLogged) { this.totalHours = totalHoursLogged; }
    
    // Helper metode
    public double getCompletionRate() {
        if (totalTasks == 0) return 0;
        return (double) completedTasks / totalTasks * 100;
    }
    
    public double getHoursCompletionRate() {
        if (totalHours == 0) return 0;
        return (double) completedHours / totalHours * 100;
    }
    
    @Override
    public String toString() {
        return "WorkerDashboardStatsDTO{" +
                "totalTasks=" + totalTasks +
                ", completedTasks=" + completedTasks +
                ", inProgressTasks=" + inProgressTasks +
                ", overdueTasks=" + overdueTasks +
                ", completedToday=" + completedToday +
                ", totalHours=" + totalHours +
                ", completedHours=" + completedHours +
                ", assignedOrders=" + assignedOrders +
                ", completedOrders=" + completedOrders +
                '}';
    }
}