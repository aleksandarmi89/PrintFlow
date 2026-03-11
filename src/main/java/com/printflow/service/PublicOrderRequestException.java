package com.printflow.service;

public class PublicOrderRequestException extends RuntimeException {

    private final String messageKey;
    private final Object[] messageArgs;

    public PublicOrderRequestException(String messageKey, Object... messageArgs) {
        super(messageKey);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs != null ? messageArgs : new Object[0];
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}

