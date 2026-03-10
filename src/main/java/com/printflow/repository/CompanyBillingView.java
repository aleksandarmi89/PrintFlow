package com.printflow.repository;

import java.time.LocalDateTime;

public interface CompanyBillingView {
    LocalDateTime getTrialEnd();
    Boolean getBillingOverrideActive();
    LocalDateTime getBillingOverrideUntil();
}
