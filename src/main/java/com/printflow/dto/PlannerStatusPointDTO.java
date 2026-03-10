package com.printflow.dto;

public class PlannerStatusPointDTO {
    private String label;
    private int newCount;
    private int designCount;
    private int printCount;
    private int readyCount;

    public PlannerStatusPointDTO() {}

    public PlannerStatusPointDTO(String label, int newCount, int designCount, int printCount, int readyCount) {
        this.label = label;
        this.newCount = newCount;
        this.designCount = designCount;
        this.printCount = printCount;
        this.readyCount = readyCount;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getNewCount() { return newCount; }
    public void setNewCount(int newCount) { this.newCount = newCount; }
    public int getDesignCount() { return designCount; }
    public void setDesignCount(int designCount) { this.designCount = designCount; }
    public int getPrintCount() { return printCount; }
    public void setPrintCount(int printCount) { this.printCount = printCount; }
    public int getReadyCount() { return readyCount; }
    public void setReadyCount(int readyCount) { this.readyCount = readyCount; }
}
