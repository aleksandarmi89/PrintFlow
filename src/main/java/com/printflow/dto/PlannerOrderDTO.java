package com.printflow.dto;

import java.time.LocalDateTime;

public class PlannerOrderDTO {
    private Long id;
    private String orderNumber;
    private String title;
    private String clientName;
    private String status;
    private LocalDateTime deadline;
    private String assignedToName;
    private Double price;

    public PlannerOrderDTO() {}

    public PlannerOrderDTO(Long id, String orderNumber, String title, String clientName, String status,
                           LocalDateTime deadline, String assignedToName, Double price) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.title = title;
        this.clientName = clientName;
        this.status = status;
        this.deadline = deadline;
        this.assignedToName = assignedToName;
        this.price = price;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
