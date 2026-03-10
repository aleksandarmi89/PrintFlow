package com.printflow.entity.enums;

public enum OrderStatus {
    NEW,
    IN_DESIGN,
    WAITING_CLIENT_APPROVAL,
    APPROVED_FOR_PRINT,
    IN_PRINT,
    WAITING_QUALITY_CHECK,
    READY_FOR_DELIVERY,
    SENT,
    OVERDUE,
    COMPLETED,
    CANCELLED,
    IN_PROGRESS;

    public String getMessageKey() {
        return "orders.status." + name();
    }
}
