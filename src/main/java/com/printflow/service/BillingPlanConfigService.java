package com.printflow.service;

import com.printflow.entity.BillingPlanConfig;
import com.printflow.entity.enums.PlanTier;
import com.printflow.entity.enums.BillingInterval;
import com.printflow.repository.BillingPlanConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.EnumMap;

@Service
public class BillingPlanConfigService {

    private final BillingPlanConfigRepository repository;

    public BillingPlanConfigService(BillingPlanConfigRepository repository) {
        this.repository = repository;
    }

    public Map<PlanTier, Map<BillingInterval, String>> getPriceIdsByInterval() {
        Map<PlanTier, Map<BillingInterval, String>> result = new EnumMap<>(PlanTier.class);
        for (PlanTier tier : PlanTier.values()) {
            Map<BillingInterval, String> intervalMap = new EnumMap<>(BillingInterval.class);
            for (BillingInterval interval : BillingInterval.values()) {
                String priceId = repository.findByPlanAndInterval(tier, interval)
                    .map(BillingPlanConfig::getStripePriceId)
                    .orElse("");
                intervalMap.put(interval, priceId != null ? priceId : "");
            }
            result.put(tier, intervalMap);
        }
        return result;
    }

    public String getPriceId(PlanTier plan, BillingInterval interval) {
        if (plan == null || interval == null) {
            return "";
        }
        return repository.findByPlanAndInterval(plan, interval)
            .map(BillingPlanConfig::getStripePriceId)
            .orElse("");
    }

    public void upsertPriceId(PlanTier plan, BillingInterval interval, String priceId) {
        if (plan == null || interval == null) {
            return;
        }
        BillingPlanConfig config = repository.findByPlanAndInterval(plan, interval)
            .orElseGet(BillingPlanConfig::new);
        config.setPlan(plan);
        config.setInterval(interval);
        String trimmed = priceId != null ? priceId.trim() : "";
        config.setStripePriceId(trimmed.isEmpty() ? null : trimmed);
        config.setActive(true);
        repository.save(config);
    }

    public PlanTier findPlanForPriceId(String priceId) {
        if (priceId == null || priceId.isBlank()) {
            return null;
        }
        return repository.findByStripePriceId(priceId.trim())
            .map(BillingPlanConfig::getPlan)
            .orElse(null);
    }
}
