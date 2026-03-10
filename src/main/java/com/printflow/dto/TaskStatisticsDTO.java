package com.printflow.dto;

public class TaskStatisticsDTO {
    private int totalTasks;
    private int inProgressTasks;
    private int completedTasks; // DODATO
    private int pendingTasks; // DODATO
    private int urgentTasks;
    private int completedToday;
    private int overdueTasks;
    private double completionRate; // DODATO
    
    // Konstruktori
    public TaskStatisticsDTO() {}
    
    public TaskStatisticsDTO(int totalTasks, int inProgressTasks, int completedTasks, 
                             int pendingTasks, int urgentTasks, int completedToday, 
                             int overdueTasks, double completionRate) {
        this.totalTasks = totalTasks;
        this.inProgressTasks = inProgressTasks;
        this.completedTasks = completedTasks;
        this.pendingTasks = pendingTasks;
        this.urgentTasks = urgentTasks;
        this.completedToday = completedToday;
        this.overdueTasks = overdueTasks;
        this.completionRate = completionRate;
    }
    
    // Getters i Setters
    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }
    
    public int getInProgressTasks() { return inProgressTasks; }
    public void setInProgressTasks(int inProgressTasks) { this.inProgressTasks = inProgressTasks; }
    
    public int getCompletedTasks() { return completedTasks; } // DODATO
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
    
    public int getPendingTasks() { return pendingTasks; } // DODATO
    public void setPendingTasks(int pendingTasks) { this.pendingTasks = pendingTasks; }
    
    public int getUrgentTasks() { return urgentTasks; }
    public void setUrgentTasks(int urgentTasks) { this.urgentTasks = urgentTasks; }
    
    public int getCompletedToday() { return completedToday; }
    public void setCompletedToday(int completedToday) { this.completedToday = completedToday; }
    
    public int getOverdueTasks() { return overdueTasks; }
    public void setOverdueTasks(int overdueTasks) { this.overdueTasks = overdueTasks; }
    
    public double getCompletionRate() { return completionRate; } // DODATO
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }
}