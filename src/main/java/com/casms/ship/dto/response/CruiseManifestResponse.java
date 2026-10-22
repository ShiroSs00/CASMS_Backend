package com.casms.ship.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CruiseManifestResponse {
    private UUID cruiseId;
    private String cruiseCode;
    private String cruiseName;
    private String shipName;
    private Integer totalCapacity;
    private Long bookedPassengers;
    private Instant departureTime;
    private Instant returnTime;
    private String originPort;
    private String destinationPort;
    private List<CruiseStaffResponse> staffList;
    private List<PassengerManifestItem> passengerList;

    @Getter
    @Builder
    public static class PassengerManifestItem {
        private UUID bookingPassengerId;
        private String bookingCode;
        private String fullName;
        private String identificationNumber;
        private String cabinNumber;
        private String deckName;
    }
}
