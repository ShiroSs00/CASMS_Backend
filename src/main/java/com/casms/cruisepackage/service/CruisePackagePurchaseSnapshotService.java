package com.casms.cruisepackage.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.dto.CruisePackagePurchaseSelection;
import com.casms.cruisepackage.entity.*;
import com.casms.cruisepackage.repository.CruisePackageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/** Only invoked inside the real Booking confirmation transaction; no HTTP purchase/backfill writer. */
@Service @RequiredArgsConstructor
public class CruisePackagePurchaseSnapshotService {
    private final BookingRepository bookings;
    private final BookingPassengerRepository passengers;
    private final CruisePackageRepository repository;
    private final ObjectMapper mapper;
    private final CruisePackageBenefitUsagePolicy usagePolicy;
    private final CruisePackageCabinMappingService cabinMappings;

    public CruisePackagePurchaseSelection intent(com.casms.booking.entity.BookingPassenger passenger) {
        if(passenger.getFareOfferId()==null)return null;
        try {
            var selection=mapper.readValue(passenger.getFareSelectionPayload(),CruisePackagePurchaseSelection.class);
            if(selection==null)throw new IllegalArgumentException("Missing Fare intent");
            return selection;
        } catch(JsonProcessingException|IllegalArgumentException e) {
            throw new BusinessException("FARE_PURCHASE_INTENT_INVALID","Thiếu nội dung gói vé đã chốt từ báo giá; không tự dựng lại từ mẫu hiện tại.");
        }
    }

    public record PurchasedCruisePackage(BookingCruisePackageSnapshot fare,java.util.List<BookingCruisePackageBenefitSnapshot> benefits) {}
    @Transactional(readOnly=true)
    public PurchasedCruisePackage purchased(UUID passengerId) {
        var fare=repository.find(BookingCruisePackageSnapshot.class,passengerId,false);
        return fare==null?null:new PurchasedCruisePackage(fare,repository.purchasedBenefits(passengerId));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void captureConfirmed(UUID bookingId) {
        bookings.flush();
        Booking booking=bookings.findByIdForUpdate(bookingId).orElseThrow(()->new BusinessException("BOOKING_NOT_FOUND","Không tìm thấy đặt chỗ cần xác nhận."));
        if(booking.getBookingStatus()!=BookingStatus.Confirmed||booking.getConfirmedAt()==null||booking.getQuote()==null)
            throw new BusinessException("FARE_CONFIRMATION_REQUIRED","Chỉ ghi gói vé đã mua trong xác nhận đặt chỗ thực tế có báo giá.");
        java.util.List<com.casms.booking.dto.response.QuoteLineItem> quotedLines;
        try {quotedLines=mapper.readValue(booking.getQuote().getQuotePayloadJson(),new com.fasterxml.jackson.core.type.TypeReference<>(){});}
        catch(JsonProcessingException|IllegalArgumentException e){throw new BusinessException("FARE_PURCHASE_QUOTE_INVALID","Không đọc được nội dung báo giá gốc; không dựng lại quyền lợi từ danh mục hiện tại.");}
        if(quotedLines==null)throw new BusinessException("FARE_PURCHASE_QUOTE_INVALID","Thiếu nội dung báo giá gốc.");
        for(var passenger:passengers.findByBookingBookingId(bookingId)) {
            if(passenger.getFareOfferId()==null||passenger.getParticipationStatus()!=BookingPassengerParticipationStatus.Active)continue;
            CruisePackagePurchaseSelection selection=intent(passenger);
            cabinMappings.requirePurchasedCabins(selection);
            if(quotedLines.stream().noneMatch(line->selection.equals(line.getFareSelection())))
                throw new BusinessException("FARE_PURCHASE_QUOTE_MISMATCH","Nội dung gói vé đã giữ không có trong báo giá gốc của đặt chỗ.");
            if(!Objects.equals(selection.offerId(),passenger.getFareOfferId())
                    ||!Objects.equals(selection.templateVersionId(),passenger.getFareTemplateVersionId())
                    ||!Objects.equals(selection.priceRevisionId(),passenger.getFarePriceRevisionId())
                    ||!Objects.equals(selection.cabinType().name(),passenger.getFareCabinCategory())
                    ||booking.getCruise().getShip()==null||!selection.shipId().equals(booking.getCruise().getShip().getShipId()))
                throw new BusinessException("FARE_PURCHASE_INTENT_MISMATCH","Nội dung mua không khớp hành khách, tàu hoặc hạng phòng đã giữ.");
            var price=repository.find(DepartureCruisePackagePrice.class,selection.priceRevisionId(),false);
            var offer=repository.find(DepartureCruisePackage.class,selection.offerId(),false);
            if(price==null||offer==null||!"APPROVED".equals(price.getStatus())
                    ||!offer.getCruiseId().equals(booking.getCruise().getCruiseId())
                    ||!price.getOfferId().equals(selection.offerId())||!price.getTemplateVersionId().equals(selection.templateVersionId())
                    ||price.getOfferCommercialRevision()!=selection.commercialRevision()
                    ||price.getAmount().compareTo(selection.amount())!=0)
                throw new BusinessException("FARE_PURCHASE_PRICE_INVALID","Phiên bản giá trong nội dung mua không hợp lệ.");
            var existing=repository.find(BookingCruisePackageSnapshot.class,passenger.getBookingPassengerId(),false);
            if(existing!=null) {
                if(!existing.getBookingId().equals(bookingId)||!existing.getOfferId().equals(selection.offerId())||!existing.getPriceRevisionId().equals(selection.priceRevisionId()))
                    throw new BusinessException("FARE_PURCHASE_SNAPSHOT_CONFLICT","Bản ghi mua đã có nội dung khác; không được ghi đè.");
                continue;
            }
            repository.insert(BookingCruisePackageSnapshot.capture(passenger.getBookingPassengerId(),bookingId,selection,booking.getConfirmedAt()));
            for(var benefit:selection.benefits()) {
                var definition=repository.find(CruisePackageBenefit.class,benefit.sourceBenefitId(),false);
                if(definition==null||!definition.getTemplateVersionId().equals(selection.templateVersionId()))
                    throw new BusinessException("FARE_PURCHASE_BENEFIT_INVALID","Quyền lợi đã chốt không thuộc phiên bản mẫu đã mua.");
                var captured=BookingCruisePackageBenefitSnapshot.capture(passenger.getBookingPassengerId(),benefit,booking.getConfirmedAt());
                var blockers=usagePolicy.blockers(captured);
                if(!blockers.isEmpty())throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT,"BENEFIT_USAGE_CONFIGURATION_REQUIRED",
                        "Snapshot quyền lợi chưa đủ cấu hình cấu trúc để cấp grant.",java.util.Map.of("blockers",blockers));
                repository.insert(captured);
            }
        }
        repository.flush();
    }
}
