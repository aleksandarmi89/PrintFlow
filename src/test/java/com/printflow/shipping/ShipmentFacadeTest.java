package com.printflow.shipping;

import com.printflow.dto.WorkOrderDTO;
import com.printflow.entity.WorkOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShipmentFacadeTest {

    @Test
    void normalizePickupClearsCourierFieldsAndSetsPreparingStatus() {
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setDeliveryType(WorkOrder.DeliveryType.PICKUP);
        dto.setCourierName("DHL");
        dto.setTrackingNumber("TRK-001");
        dto.setDeliveryAddress("Main street 1");
        dto.setDeliveryRecipientName("John Doe");
        dto.setDeliveryRecipientPhone("+38160000000");
        dto.setDeliveryCity("Belgrade");
        dto.setDeliveryPostalCode("11000");
        dto.setShippingNote("Leave at reception");

        ShipmentFacade facade = new ShipmentFacade(List.of(
            new ApiShippingProvider(),
            new ManualShippingProvider()
        ));
        facade.normalize(dto);

        assertEquals(WorkOrder.DeliveryType.PICKUP, dto.getDeliveryType());
        assertNull(dto.getCourierName());
        assertNull(dto.getTrackingNumber());
        assertNull(dto.getDeliveryAddress());
        assertNull(dto.getDeliveryRecipientName());
        assertNull(dto.getDeliveryRecipientPhone());
        assertNull(dto.getDeliveryCity());
        assertNull(dto.getDeliveryPostalCode());
        assertNull(dto.getShippingNote());
        assertEquals(WorkOrder.ShipmentStatus.PREPARING, dto.getShipmentStatus());
        assertNull(dto.getShippedAt());
        assertNull(dto.getDeliveredAt());
    }

    @Test
    void normalizeCourierKeepsFieldsAndInitializesStatusWhenMissing() {
        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setDeliveryType(WorkOrder.DeliveryType.COURIER);
        dto.setCourierName("DHL");
        dto.setTrackingNumber("TRK-002");
        dto.setDeliveryAddress("Main street 2");
        dto.setDeliveryRecipientName("Jane Doe");
        dto.setDeliveryRecipientPhone("+38161111111");
        dto.setDeliveryCity("Novi Sad");

        ShipmentFacade facade = new ShipmentFacade(List.of(
            new ApiShippingProvider(),
            new ManualShippingProvider()
        ));
        facade.normalize(dto);

        assertEquals(WorkOrder.DeliveryType.COURIER, dto.getDeliveryType());
        assertEquals("DHL", dto.getCourierName());
        assertEquals("TRK-002", dto.getTrackingNumber());
        assertEquals("Main street 2", dto.getDeliveryAddress());
        assertEquals("Jane Doe", dto.getDeliveryRecipientName());
        assertEquals("+38161111111", dto.getDeliveryRecipientPhone());
        assertEquals("Novi Sad", dto.getDeliveryCity());
        assertNotNull(dto.getShipmentStatus());
        assertEquals(WorkOrder.ShipmentStatus.PREPARING, dto.getShipmentStatus());
    }
}
