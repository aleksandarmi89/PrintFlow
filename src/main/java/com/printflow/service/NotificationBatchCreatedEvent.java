package com.printflow.service;

import java.util.List;

public class NotificationBatchCreatedEvent {
    private final List<Long> userIds;

    public NotificationBatchCreatedEvent(List<Long> userIds) {
        this.userIds = userIds;
    }

    public List<Long> getUserIds() {
        return userIds;
    }
}
