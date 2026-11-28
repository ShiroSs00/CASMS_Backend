package com.casms.ship.service;

import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.util.PortTimeZoneHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ShipClassificationExpiryJob {
    private final ShipRepository ships;
    private final ShipClassificationExpiryService expiry;

    @Scheduled(fixedDelayString = "${casms.ship-classification-expiry.poll-millis:60000}")
    public void checkClassification() {
        LocalDate today = LocalDate.now(PortTimeZoneHelper.SHIP_TIME_ZONE);
        var operational = ships.findAllByArchivedAtIsNullAndStatusInAndOperationalProfileIsNotNull(
                List.of(ShipStatus.READY, ShipStatus.IN_SERVICE));
        for (var ship : operational) {
            if (!ShipClassificationExpiryService.isExpired(ship, today)) {
                continue;
            }
            try {
                expiry.process(ship.getShipId(), today);
            } catch (RuntimeException failure) {
                log.error("Could not process classification expiry for ship {}", ship.getShipId(), failure);
            }
        }
    }
}
