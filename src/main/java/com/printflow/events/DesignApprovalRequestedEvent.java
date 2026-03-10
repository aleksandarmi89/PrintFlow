package com.printflow.events;

public class DesignApprovalRequestedEvent {
    private final Long orderId;

    public DesignApprovalRequestedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
