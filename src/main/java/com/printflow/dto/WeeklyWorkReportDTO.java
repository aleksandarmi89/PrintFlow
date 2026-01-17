// WeeklyWorkReportDTO.java
package com.printflow.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WeeklyWorkReportDTO {
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private List<DailySummaryDTO> dailySummaries = new ArrayList<>();
    private int totalHours;
    private int completedTasks;
    private double averageDailyHours;
    private String weeklyEfficiency;
    
    // Getteri i setteri
    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    
    public LocalDate getWeekEnd() { return weekEnd; }
    public void setWeekEnd(LocalDate weekEnd) { this.weekEnd = weekEnd; }
    
    public List<DailySummaryDTO> getDailySummaries() { return dailySummaries; }
    public void setDailySummaries(List<DailySummaryDTO> dailySummaries) { this.dailySummaries = dailySummaries; }
    
    public int getTotalHours() { return totalHours; }
    public void setTotalHours(int totalHours) { this.totalHours = totalHours; }
    
    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
    
    public double getAverageDailyHours() { return averageDailyHours; }
    public void setAverageDailyHours(double averageDailyHours) { this.averageDailyHours = averageDailyHours; }
    
    public String getWeeklyEfficiency() { return weeklyEfficiency; }
    public void setWeeklyEfficiency(String weeklyEfficiency) { this.weeklyEfficiency = weeklyEfficiency; }
    
    public static class DailySummaryDTO {
        private LocalDate date;
        private int hoursWorked;
        private int tasksCompleted;
        private List<String> completedTaskNames = new ArrayList<>();
        
        // Getteri i setteri za DailySummaryDTO
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        
        public int getHoursWorked() { return hoursWorked; }
        public void setHoursWorked(int hoursWorked) { this.hoursWorked = hoursWorked; }
        
        public int getTasksCompleted() { return tasksCompleted; }
        public void setTasksCompleted(int tasksCompleted) { this.tasksCompleted = tasksCompleted; }
        
        public List<String> getCompletedTaskNames() { return completedTaskNames; }
        public void setCompletedTaskNames(List<String> completedTaskNames) { this.completedTaskNames = completedTaskNames; }
    }
}