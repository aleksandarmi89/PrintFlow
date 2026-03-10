package com.printflow.dto;

import java.time.LocalDateTime;

public class TimeEntryDTO {
    private Long id;
    private Long taskId;
    private String taskTitle;  // Promenjeno iz taskName u taskTitle
    private String userName;   // Dodato
    private Integer hours;     // Dodato
    private Integer minutes;   // Dodato
    private String description;
    private LocalDateTime date; // Promenjeno iz startTime u date
    
    // Konstruktori
    public TimeEntryDTO() {}
    
    public TimeEntryDTO(Long id, Long taskId, String taskTitle, String userName, 
                       Integer hours, Integer minutes, String description, LocalDateTime date) {
        this.id = id;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.userName = userName;
        this.hours = hours;
        this.minutes = minutes;
        this.description = description;
        this.date = date;
    }
    
    // Getters i Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    
    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }
    
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    
    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }
    
    // Helper metode
    public double getTotalHours() {
        return hours + (minutes / 60.0);
    }
    
    public String getFormattedDuration() {
        if (hours > 0 && minutes > 0) {
            return String.format("%d h %d min", hours, minutes);
        } else if (hours > 0) {
            return String.format("%d h", hours);
        } else {
            return String.format("%d min", minutes);
        }
    }
    
    // Kompatibilnost sa starim kodom (ako postoji)
    public LocalDateTime getStartTime() { return date; }
    public void setStartTime(LocalDateTime startTime) { this.date = startTime; }
    
    public int getDurationMinutes() { 
        return (hours * 60) + minutes; 
    }
    
    public void setDurationMinutes(int durationMinutes) {
        this.hours = durationMinutes / 60;
        this.minutes = durationMinutes % 60;
    }
    
    // Ovo je za setter koji prima double (ako postoji takav kod)
    public void setDurationMinutes(double totalMinutes) {
        this.hours = (int) (totalMinutes / 60);
        this.minutes = (int) (totalMinutes % 60);
    }
    
    // Ostala polja za kompatibilnost
    public String getTaskName() { return taskTitle; }
    public void setTaskName(String taskName) { this.taskTitle = taskName; }
    
    public String getClientName() { return null; } // Nije dostupno u osnovnom DTO
    public void setClientName(String clientName) { /* Ignorišemo */ }
    
    public LocalDateTime getEndTime() { return null; } // Nije dostupno
    public void setEndTime(LocalDateTime endTime) { /* Ignorišemo */ }
    
    public boolean isBillable() { return true; } // Podrazumevano
    public void setBillable(boolean billable) { /* Ignorišemo */ }
    
    @Override
    public String toString() {
        return "TimeEntryDTO{" +
                "id=" + id +
                ", taskId=" + taskId +
                ", taskTitle='" + taskTitle + '\'' +
                ", hours=" + hours +
                ", minutes=" + minutes +
                ", description='" + description + '\'' +
                ", date=" + date +
                '}';
    }
}