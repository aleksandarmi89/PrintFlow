package com.printflow.shipping;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.WorkOrder.DeliveryType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShipmentFacade {

    private final List<ShippingProvider> providers;

    public ShipmentFacade(List<ShippingProvider> providers) {
        this.providers = providers;
    }

    public void normalize(WorkOrderDTO order) {
        DeliveryType deliveryType = order != null ? order.getDeliveryType() : null;
        for (ShippingProvider provider : providers) {
            if (provider.supports(deliveryType)) {
                provider.normalize(order);
                return;
            }
        }
        throw new IllegalArgumentException("No shipping provider supports delivery type: " + deliveryType);
    }
}
