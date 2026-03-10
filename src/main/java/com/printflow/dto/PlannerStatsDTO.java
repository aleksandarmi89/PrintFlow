package com.printflow.dto;

public class PlannerStatsDTO {
    private int totalOpenOrders;
    private int overdueOrders;
    private int dueSoonOrders;
    private int unassignedOrders;
    private int inDesignOrders;
    private int inPrintOrders;
    private int totalWorkers;
    private int availableWorkers;
    private double capacityUtilization;
    private double paidRevenueMonth;
    private double outstandingRevenue;
    private int createdThisMonth;

    public int getTotalOpenOrders() { return totalOpenOrders; }
    public void setTotalOpenOrders(int totalOpenOrders) { this.totalOpenOrders = totalOpenOrders; }
    public int getOverdueOrders() { return overdueOrders; }
    public void setOverdueOrders(int overdueOrders) { this.overdueOrders = overdueOrders; }
    public int getDueSoonOrders() { return dueSoonOrders; }
    public void setDueSoonOrders(int dueSoonOrders) { this.dueSoonOrders = dueSoonOrders; }
    public int getUnassignedOrders() { return unassignedOrders; }
    public void setUnassignedOrders(int unassignedOrders) { this.unassignedOrders = unassignedOrders; }
    public int getInDesignOrders() { return inDesignOrders; }
    public void setInDesignOrders(int inDesignOrders) { this.inDesignOrders = inDesignOrders; }
    public int getInPrintOrders() { return inPrintOrders; }
    public void setInPrintOrders(int inPrintOrders) { this.inPrintOrders = inPrintOrders; }
    public int getTotalWorkers() { return totalWorkers; }
    public void setTotalWorkers(int totalWorkers) { this.totalWorkers = totalWorkers; }
    public int getAvailableWorkers() { return availableWorkers; }
    public void setAvailableWorkers(int availableWorkers) { this.availableWorkers = availableWorkers; }
    public double getCapacityUtilization() { return capacityUtilization; }
    public void setCapacityUtilization(double capacityUtilization) { this.capacityUtilization = capacityUtilization; }
    public double getPaidRevenueMonth() { return paidRevenueMonth; }
    public void setPaidRevenueMonth(double paidRevenueMonth) { this.paidRevenueMonth = paidRevenueMonth; }
    public double getOutstandingRevenue() { return outstandingRevenue; }
    public void setOutstandingRevenue(double outstandingRevenue) { this.outstandingRevenue = outstandingRevenue; }
    public int getCreatedThisMonth() { return createdThisMonth; }
    public void setCreatedThisMonth(int createdThisMonth) { this.createdThisMonth = createdThisMonth; }
}
