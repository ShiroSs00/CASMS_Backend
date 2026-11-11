package com.casms.booking.service;

import com.casms.booking.dto.request.PassengerBookingItemRequest;
import com.casms.booking.dto.request.PassengerQuoteItem;
import com.casms.booking.dto.response.QuoteLineItem;
import com.casms.booking.enums.CabinSelectionMode;
import com.casms.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

record BookingResourceSelection(UUID cabinId, UUID tourPackageId, CabinSelectionMode mode,
                                String groupKey, UUID offerId, UUID priceRevisionId) {
    static BookingResourceSelection from(PassengerQuoteItem item) {
        return new BookingResourceSelection(item.getCabinId(), item.getTourPackageId(),
                item.getCabinSelectionMode(), item.getCabinGroupKey(),
                item.getFarePackageOfferId(), item.getFarePackagePriceRevisionId());
    }

    static BookingResourceSelection from(PassengerBookingItemRequest item) {
        return new BookingResourceSelection(item.getCabinId(), item.getTourPackageId(),
                item.getCabinSelectionMode(), item.getCabinGroupKey(),
                item.getFarePackageOfferId(), item.getFarePackagePriceRevisionId());
    }

    static BookingResourceSelection from(QuoteLineItem line) {
        var fare = line.getFareSelection();
        return new BookingResourceSelection(line.getCabinId(), line.getTourPackageId(),
                line.getCabinSelectionMode(), line.getCabinGroupKey(),
                fare == null ? null : fare.offerId(), fare == null ? null : fare.priceRevisionId());
    }

    BookingResourceSelection validate(boolean unified) {
        if (!unified) {
            if (offerId != null || priceRevisionId != null || mode != null || groupKey != null)
                throw invalid("FARE_SELECTION_NOT_SUPPORTED", "Chuyến khởi hành không bán gói du thuyền theo báo giá này.");
            if (cabinId == null)
                throw invalid("CABIN_ID_REQUIRED", "Thông tin phòng của hành khách không được để trống.");
            return this;
        }
        if (offerId == null || priceRevisionId == null || mode == null || tourPackageId != null)
            throw invalid("FARE_SELECTION_REQUIRED", "Chọn gói du thuyền, phiên bản giá và cách nhận cabin.");
        if ((mode == CabinSelectionMode.PHYSICAL_CABIN) != (cabinId != null))
            throw invalid("FARE_CABIN_MODE_MISMATCH", "Cabin đã chọn không khớp cách nhận cabin.");
        String group = groupKey == null ? "" : groupKey.trim();
        if (group.length() > 100)
            throw invalid("FARE_CABIN_GROUP_INVALID", "Nhóm cabin không hợp lệ.");
        if (group.isEmpty())
            group = mode == CabinSelectionMode.CATEGORY_HOLD ? "BOOKING_GROUP" : "CABIN:" + cabinId;
        return new BookingResourceSelection(cabinId, null, mode, group, offerId, priceRevisionId);
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }
}
