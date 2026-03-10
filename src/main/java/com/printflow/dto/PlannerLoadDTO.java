package com.printflow.dto;

public class PlannerLoadDTO {
    private String label;
    private int count;

    public PlannerLoadDTO() {}

    public PlannerLoadDTO(String label, int count) {
        this.label = label;
        this.count = count;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
