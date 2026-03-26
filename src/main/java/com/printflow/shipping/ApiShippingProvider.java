package com.printflow.shipping;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.WorkOrder.DeliveryType;
import org.springframework.stereotype.Component;

/**
 * Placeholder for future courier API integrations.
 * Intentionally disabled in the current manual-first release.
 */
@Component
public class ApiShippingProvider implements ShippingProvider {

    @Override
    public String providerCode() {
        return "API_PLACEHOLDER";
    }

    @Override
    public boolean supports(DeliveryType deliveryType) {
        return false;
    }

    @Override
    public void normalize(WorkOrderDTO order) {
        // No-op. Kept for forward compatibility once API shipping is enabled.
    }
}
