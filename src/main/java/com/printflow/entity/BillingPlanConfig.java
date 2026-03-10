package com.printflow.entity;

import com.printflow.entity.enums.PlanTier;
import com.printflow.entity.enums.BillingInterval;
import jakarta.persistence.*;

@Entity
@Table(name = "billing_plan_configs",
    uniqueConstraints = @UniqueConstraint(name = "uk_billing_plan_interval", columnNames = {"plan", "billing_interval"}))
public class BillingPlanConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 30)
    private PlanTier plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval interval = BillingInterval.MONTHLY;

    @Column(name = "stripe_price_id", length = 255)
    private String stripePriceId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PlanTier getPlan() { return plan; }
    public void setPlan(PlanTier plan) { this.plan = plan; }

    public BillingInterval getInterval() { return interval; }
    public void setInterval(BillingInterval interval) { this.interval = interval; }

    public String getStripePriceId() { return stripePriceId; }
    public void setStripePriceId(String stripePriceId) { this.stripePriceId = stripePriceId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
