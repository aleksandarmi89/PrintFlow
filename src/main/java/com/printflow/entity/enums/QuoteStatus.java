package com.printflow.entity.enums;

public enum QuoteStatus {
    NONE,
    PREPARING,
    READY,
    SENT,
    APPROVED;

    public String getMessageKey() {
        return "orders.quote.status." + name();
    }
}
