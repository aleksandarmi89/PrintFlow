package com.printflow.dto;

public class TimeSummaryDTO {
    private double todayHours;
    private double weekHours;
    private double totalHours;
    private double completedHours;
    private double remainingHours;
    private double efficiencyRate;
    private int totalEntries; // DODATO

    // Prazan konstruktor
    public TimeSummaryDTO() {
    }

    // Konstruktor sa svim poljima
    public TimeSummaryDTO(double todayHours, double weekHours, double totalHours, 
                          double completedHours, double remainingHours, double efficiencyRate, 
                          int totalEntries) {
        this.todayHours = todayHours;
        this.weekHours = weekHours;
        this.totalHours = totalHours;
        this.completedHours = completedHours;
        this.remainingHours = remainingHours;
        this.efficiencyRate = efficiencyRate;
        this.totalEntries = totalEntries;
    }

    // Geteri i Seteri
    public double getTodayHours() { return todayHours; }
    public void setTodayHours(double todayHours) { this.todayHours = todayHours; }

    public double getWeekHours() { return weekHours; }
    public void setWeekHours(double weekHours) { this.weekHours = weekHours; }

    public double getTotalHours() { return totalHours; }
    public void setTotalHours(double totalHours) { this.totalHours = totalHours; }

    public double getCompletedHours() { return completedHours; }
    public void setCompletedHours(double completedHours) { this.completedHours = completedHours; }

    public double getRemainingHours() { return remainingHours; }
    public void setRemainingHours(double remainingHours) { this.remainingHours = remainingHours; }

    public double getEfficiencyRate() { return efficiencyRate; }
    public void setEfficiencyRate(double efficiencyRate) { this.efficiencyRate = efficiencyRate; }

    public int getTotalEntries() { return totalEntries; } // DODATO
    public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }
}