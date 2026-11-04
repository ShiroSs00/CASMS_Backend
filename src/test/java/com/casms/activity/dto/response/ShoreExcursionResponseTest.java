package com.casms.activity.dto.response;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Port;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class ShoreExcursionResponseTest {
    @Test
    void portCatalogTourDoesNotRequireAnOperationalCruiseStop() {
        var tour = new ShoreExcursion();
        tour.setShoreExcursionId(UUID.randomUUID());
        var port = new Port();
        port.setPortId(UUID.randomUUID());
        tour.setPort(port);
        var supplier = new Supplier();
        supplier.setSupplierId(UUID.randomUUID());
        supplier.setSupplierName("Catalog supplier");
        tour.setSupplier(supplier);
        tour.setMaximumCapacity(20);
        var response = ShoreExcursionResponse.from(tour, 3);
        assertNull(response.cruiseStopId());
        assertEquals(supplier.getSupplierId(), response.supplierId());
        assertEquals(17, response.availableCapacity());
        var stop = new CruiseStop();
        stop.setCruiseStopId(UUID.randomUUID());
        tour.setCruiseStop(stop);
        assertEquals(stop.getCruiseStopId(), ShoreExcursionResponse.from(tour, 3).cruiseStopId());
    }
}
