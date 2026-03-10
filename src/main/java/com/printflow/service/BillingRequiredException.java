package com.printflow.service;

public class BillingRequiredException extends RuntimeException {
    public BillingRequiredException(String message) {
        super(message);
    }
}
