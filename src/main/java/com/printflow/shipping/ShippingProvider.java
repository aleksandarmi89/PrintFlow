package com.printflow.shipping;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.WorkOrder.DeliveryType;

public interface ShippingProvider {

    String providerCode();

    boolean supports(DeliveryType deliveryType);

    void normalize(WorkOrderDTO order);
}
