package com.printflow.dto;

public class PlannerWorkerLoadDTO {
    private String workerName;
    private long count;

    public PlannerWorkerLoadDTO() {}

    public PlannerWorkerLoadDTO(String workerName, long count) {
        this.workerName = workerName;
        this.count = count;
    }

    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
