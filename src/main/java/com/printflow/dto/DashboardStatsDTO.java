package com.printflow.dto;

import lombok.Data;


@Data
public class DashboardStatsDTO {
    private long totalClients;
    private long totalOrders;
    private long activeOrders;
    private long completedOrders;
    private long overdueOrders;
    private long totalUsers;
    private double monthlyRevenue;
    private double pendingRevenue;
    
    // Order status breakdown
    private long newOrders;
    private long inDesignOrders;
    private long waitingApprovalOrders;
    private long inPrintOrders;
    private long readyForDeliveryOrders;
    
    
    
	public DashboardStatsDTO() {
		
		// TODO Auto-generated constructor stub
	}
	public DashboardStatsDTO(long totalClients, long totalOrders, long activeOrders, long completedOrders,
			long overdueOrders, long totalUsers, double monthlyRevenue, double pendingRevenue, long newOrders,
			long inDesignOrders, long waitingApprovalOrders, long inPrintOrders, long readyForDeliveryOrders) {
		
		this.totalClients = totalClients;
		this.totalOrders = totalOrders;
		this.activeOrders = activeOrders;
		this.completedOrders = completedOrders;
		this.overdueOrders = overdueOrders;
		this.totalUsers = totalUsers;
		this.monthlyRevenue = monthlyRevenue;
		this.pendingRevenue = pendingRevenue;
		this.newOrders = newOrders;
		this.inDesignOrders = inDesignOrders;
		this.waitingApprovalOrders = waitingApprovalOrders;
		this.inPrintOrders = inPrintOrders;
		this.readyForDeliveryOrders = readyForDeliveryOrders;
	}
	public long getTotalClients() {
		return totalClients;
	}
	public void setTotalClients(long totalClients) {
		this.totalClients = totalClients;
	}
	public long getTotalOrders() {
		return totalOrders;
	}
	public void setTotalOrders(long totalOrders) {
		this.totalOrders = totalOrders;
	}
	public long getActiveOrders() {
		return activeOrders;
	}
	public void setActiveOrders(long activeOrders) {
		this.activeOrders = activeOrders;
	}
	public long getCompletedOrders() {
		return completedOrders;
	}
	public void setCompletedOrders(long completedOrders) {
		this.completedOrders = completedOrders;
	}
	public long getOverdueOrders() {
		return overdueOrders;
	}
	public void setOverdueOrders(long overdueOrders) {
		this.overdueOrders = overdueOrders;
	}
	public long getTotalUsers() {
		return totalUsers;
	}
	public void setTotalUsers(long totalUsers) {
		this.totalUsers = totalUsers;
	}
	public double getMonthlyRevenue() {
		return monthlyRevenue;
	}
	public void setMonthlyRevenue(double monthlyRevenue) {
		this.monthlyRevenue = monthlyRevenue;
	}
	public double getPendingRevenue() {
		return pendingRevenue;
	}
	public void setPendingRevenue(double pendingRevenue) {
		this.pendingRevenue = pendingRevenue;
	}
	public long getNewOrders() {
		return newOrders;
	}
	public void setNewOrders(long newOrders) {
		this.newOrders = newOrders;
	}
	public long getInDesignOrders() {
		return inDesignOrders;
	}
	public void setInDesignOrders(long inDesignOrders) {
		this.inDesignOrders = inDesignOrders;
	}
	public long getWaitingApprovalOrders() {
		return waitingApprovalOrders;
	}
	public void setWaitingApprovalOrders(long waitingApprovalOrders) {
		this.waitingApprovalOrders = waitingApprovalOrders;
	}
	public long getInPrintOrders() {
		return inPrintOrders;
	}
	public void setInPrintOrders(long inPrintOrders) {
		this.inPrintOrders = inPrintOrders;
	}
	public long getReadyForDeliveryOrders() {
		return readyForDeliveryOrders;
	}
	public void setReadyForDeliveryOrders(long readyForDeliveryOrders) {
		this.readyForDeliveryOrders = readyForDeliveryOrders;
	}
    
    
}