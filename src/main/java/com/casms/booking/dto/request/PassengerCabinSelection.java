package com.casms.booking.dto.request;

import com.casms.booking.enums.CabinSelectionMode;
import java.util.UUID;

/** Shared additive request contract, implemented by existing passenger quote/create DTOs. */
public interface PassengerCabinSelection {
    UUID getCabinId();
    UUID getTourPackageId();
    UUID getFarePackageOfferId();
    UUID getFarePackagePriceRevisionId();
    CabinSelectionMode getCabinSelectionMode();
    String getCabinGroupKey();
}
