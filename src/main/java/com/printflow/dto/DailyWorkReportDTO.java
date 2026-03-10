package com.printflow.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DailyWorkReportDTO {
    private LocalDate date;
    private List<TaskTimeEntryDTO> tasks;
    private int totalTasks;
    private double totalHours;  // Promenjeno iz int u double
    private int completedTasks;
    private int inProgressTasks;
    private String productivityScore;
    private double efficiencyRate;  // DODATO
    
    public static class TaskTimeEntryDTO {
        private Long taskId;
        private String taskName;
        private String orderNumber;
        private String clientName;
        private double timeSpentHours;  // Promenjeno iz int minutes u double hours
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        
        public TaskTimeEntryDTO() {
        }

        public TaskTimeEntryDTO(Long taskId, String taskName, String orderNumber, String clientName,
                double timeSpentHours, String status, LocalDateTime startTime, LocalDateTime endTime) {
            this.taskId = taskId;
            this.taskName = taskName;
            this.orderNumber = orderNumber;
            this.clientName = clientName;
            this.timeSpentHours = timeSpentHours;
            this.status = status;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        // Getters and Setters
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        
        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }
        
        public double getTimeSpentHours() { return timeSpentHours; }
        public void setTimeSpentHours(double timeSpentHours) { this.timeSpentHours = timeSpentHours; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        // Helper metode za kompatibilnost
        public int getTimeSpentMinutes() {
            return (int) (timeSpentHours * 60);
        }
        
        public void setTimeSpentMinutes(int minutes) {
            this.timeSpentHours = minutes / 60.0;
        }
    }
    
    public DailyWorkReportDTO() {
    }
    
    public DailyWorkReportDTO(LocalDate date, List<TaskTimeEntryDTO> tasks, int totalTasks, double totalHours,
            int completedTasks, int inProgressTasks, String productivityScore, double efficiencyRate) {
        this.date = date;
        this.tasks = tasks;
        this.totalTasks = totalTasks;
        this.totalHours = totalHours;
        this.completedTasks = completedTasks;
        this.inProgressTasks = inProgressTasks;
        this.productivityScore = productivityScore;
        this.efficiencyRate = efficiencyRate;
    }
    
    // Getters and Setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    
    public List<TaskTimeEntryDTO> getTasks() { return tasks; }
    public void setTasks(List<TaskTimeEntryDTO> tasks) { this.tasks = tasks; }
    
    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }
    
    public double getTotalHours() { return totalHours; }
    public void setTotalHours(double totalHours) { this.totalHours = totalHours; }
    
    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
    
    public int getInProgressTasks() { return inProgressTasks; }
    public void setInProgressTasks(int inProgressTasks) { this.inProgressTasks = inProgressTasks; }
    
    public String getProductivityScore() { return productivityScore; }
    public void setProductivityScore(String productivityScore) { this.productivityScore = productivityScore; }
    
    public double getEfficiencyRate() { return efficiencyRate; }
    public void setEfficiencyRate(double efficiencyRate) { this.efficiencyRate = efficiencyRate; }
    
    // Helper metode za kompatibilnost
    public void setTotalHours(int hours) {
        this.totalHours = hours;
    }
    
    // Dodatne helper metode
    public double getAverageHoursPerTask() {
        if (totalTasks == 0) return 0;
        return totalHours / totalTasks;
    }
    
    public double getCompletionRate() {
        if (totalTasks == 0) return 0;
        return (double) completedTasks / totalTasks * 100;
    }
    
    public String getFormattedTotalHours() {
        int hours = (int) totalHours;
        int minutes = (int) ((totalHours - hours) * 60);
        if (minutes > 0) {
            return String.format("%d h %d min", hours, minutes);
        } else {
            return String.format("%d h", hours);
        }
    }
}