package com.printflow.dto;

import java.time.LocalDate;

public class PlannerHeatmapDTO {
    private LocalDate date;
    private int day;
    private int count;

    public PlannerHeatmapDTO(LocalDate date, int day, int count) {
        this.date = date;
        this.day = day;
        this.count = count;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
