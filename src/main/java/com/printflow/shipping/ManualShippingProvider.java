package com.printflow.shipping;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.WorkOrder.DeliveryType;
import org.springframework.stereotype.Component;

@Component
public class ManualShippingProvider implements ShippingProvider {

    @Override
    public String providerCode() {
        return "MANUAL";
    }

    @Override
    public boolean supports(DeliveryType deliveryType) {
        return deliveryType == null
            || deliveryType == DeliveryType.PICKUP
            || deliveryType == DeliveryType.COURIER
            || deliveryType == DeliveryType.EXPRESS_POST;
    }

    @Override
    public void normalize(WorkOrderDTO order) {
        if (order == null) {
            return;
        }
        if (order.getDeliveryType() == null) {
            order.setDeliveryType(DeliveryType.PICKUP);
        }
        if (order.getDeliveryType() == DeliveryType.PICKUP) {
            order.setCourierName(null);
            order.setTrackingNumber(null);
            order.setDeliveryAddress(null);
            order.setDeliveryDate(null);
            order.setDeliveryRecipientName(null);
            order.setDeliveryRecipientPhone(null);
            order.setDeliveryCity(null);
            order.setDeliveryPostalCode(null);
            order.setShipmentStatus(WorkOrder.ShipmentStatus.PREPARING);
            order.setShippedAt(null);
            order.setDeliveredAt(null);
            order.setShipmentPrice(null);
            order.setShippingNote(null);
            return;
        }
        if (order.getShipmentStatus() == null) {
            order.setShipmentStatus(WorkOrder.ShipmentStatus.PREPARING);
        }
    }
}
