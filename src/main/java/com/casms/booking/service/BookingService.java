package com.casms.booking.service;

import com.casms.booking.dto.request.BookingCancelRequest;
import com.casms.booking.dto.request.BookingContractAcceptRequest;
import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.CabinChangeRequest;
import com.casms.booking.dto.request.PassengerBookingItemRequest;
import com.casms.booking.dto.response.AvailableCabinResponse;
import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.booking.dto.response.BookingPassengerResponse;
import com.casms.booking.dto.response.BookingSummaryResponse;
import com.casms.booking.dto.response.InstallmentResponse;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.entity.CabinAssignment;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.booking.dto.request.BookingQuoteRequest;
import com.casms.booking.dto.request.PassengerQuoteItem;
import com.casms.booking.dto.response.BookingQuoteResponse;
import com.casms.booking.dto.response.QuoteLineItem;
import com.casms.booking.entity.BookingQuote;
import com.casms.booking.repository.BookingQuoteRepository;
import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.repository.CruisePricingPolicyRepository;
import com.casms.ship.enums.PricingPolicyStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Function;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.response.Ship3dConfigurationResponse.Vector3;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.entity.Refund;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.InstallmentType;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.RefundRepository;
import com.casms.payment.enums.RefundStatus;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.entity.BookingPassengerPackage;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.repository.BookingPassengerPackageRepository;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.entity.ShipModelObject;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseTourPackageRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.casms.ship.repository.ShipModelRepository;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final CabinAssignmentRepository cabinAssignmentRepository;
    private final BookingContractRepository bookingContractRepository;
    private final CruiseRepository cruiseRepository;
    private final CabinRepository cabinRepository;
    private final ShipModelRepository shipModelRepository;
    private final ShipModelObjectRepository shipModelObjectRepository;
    private final PassengerRepository passengerRepository;
    private final TourPackageRepository tourPackageRepository;
    private final BookingPassengerPackageRepository bookingPassengerPackageRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final NotificationRepository notificationRepository;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final CruiseTourPackageRepository cruiseTourPackageRepository;
    private final ItineraryRepository itineraryRepository;
    private final CruisePricingPolicyRepository cruisePricingPolicyRepository;
    private final BookingQuoteRepository bookingQuoteRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager bookingEntityManager;
    private final org.springframework.jdbc.core.JdbcTemplate bookingJdbc;
    private final com.casms.cruisepackage.service.CruisePackageService farePackages;
    private final com.casms.cruisepackage.service.CruisePackagePurchaseSnapshotService farePurchases;
    private final com.casms.cruisepackage.service.CruisePackageCabinMappingService cabinMappings;

    /**
     * Tra cứu danh sách các phòng còn trống trên tàu của chuyến đi để khách hàng tự chọn.
     */
    private final com.casms.ship.service.CruiseMainflowService mainflow;

    @Transactional(readOnly = true)
    public List<AvailableCabinResponse> getAvailableCabins(UUID cruiseId, UUID deckId, CabinType cabinType, Integer minCapacity) {
        return findAvailableCabins(cruiseId, deckId, cabinType, minCapacity, false);
    }

    @Transactional(readOnly = true)
    public List<AvailableCabinResponse> getThreeDimensionalAvailableCabins(UUID cruiseId, UUID deckId,
            CabinType cabinType, Integer minCapacity) {
        return findAvailableCabins(cruiseId, deckId, cabinType, minCapacity, true);
    }

    private List<AvailableCabinResponse> findAvailableCabins(UUID cruiseId, UUID deckId, CabinType cabinType,
            Integer minCapacity, boolean includeModelPlacement) {
        return findAvailableCabins(cruiseId,deckId,cabinType,minCapacity,includeModelPlacement,null);
    }

    @Transactional(readOnly=true)
    public List<AvailableCabinResponse> getAvailableCabinsForBooking(UUID cruiseId,UUID deckId,CabinType cabinType,
            Integer minCapacity,UUID passengerId,CasmsPrincipal principal,boolean includeModelPlacement) {
        var passenger=bookingPassengerRepository.findById(passengerId).orElseThrow(()->new ResourceNotFoundException("BookingPassenger",passengerId));
        validateAccess(passenger.getBooking(),principal);
        var booking=passenger.getBooking();
        if(!booking.getCruise().getCruiseId().equals(cruiseId)||passenger.getFareOfferId()==null)
            throw new BusinessException("BOOKING_CRUISE_MISMATCH","Ngữ cảnh phân cabin không thuộc đặt chỗ gói vé của chuyến này.");
        if(!fareCabinActorAllowed(booking,principal)||passenger.getParticipationStatus()!=BookingPassengerParticipationStatus.Active)
            throw new BusinessException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chưa có quyền chọn cabin cho nhóm này.");
        if(booking.getBookingStatus()==BookingStatus.Cancelled||booking.getCruise().getStatus()==CruiseStatus.COMPLETED||booking.getCruise().getStatus()==CruiseStatus.CANCELLED)
            throw new BusinessException("CRUISE_NOT_ASSIGNABLE","Không phân cabin cho đặt chỗ hoặc chuyến đã hủy/hoàn tất.");
        if(booking.getBookingStatus()==BookingStatus.Draft&&(booking.getHoldExpiresAt()==null||!booking.getHoldExpiresAt().isAfter(Instant.now())))
            throw new BusinessException("BOOKING_HOLD_EXPIRED","Giữ chỗ đã hết hạn.");
        var type=CabinType.valueOf(passenger.getFareCabinCategory());
        if(cabinType!=null&&cabinType!=type)throw new BusinessException("FARE_CABIN_CATEGORY_MISMATCH","Hạng cabin tra cứu không khớp hạng đã chọn/mua.");
        return findAvailableCabins(cruiseId,deckId,null,minCapacity,includeModelPlacement,passenger);
    }

    private List<AvailableCabinResponse> findAvailableCabins(UUID cruiseId, UUID deckId, CabinType cabinType,
            Integer minCapacity, boolean includeModelPlacement,BookingPassenger context) {
        Cruise cruise = getCruise(cruiseId);
        if(context==null) {
        mainflow.requireBookable(cruiseId);
        if (cruise.getOpenedForSaleAt() == null || cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException(HttpStatus.CONFLICT, "CRUISE_NOT_OPEN_FOR_SALE",
                    "Chuyến khởi hành chưa được công bố mở bán (Trạng thái: " + cruise.getStatus() + ")");
        }
        if (cruise.getStoppedAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "CRUISE_SALE_STOPPED",
                    "Chuyến khởi hành đang tạm dừng mở bán, không thể tra cứu hoặc đặt phòng");
        }
        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "CRUISE_NOT_OPEN_FOR_SALE", "Chuyến khởi hành chưa có lịch trình mở bán"));
        if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
            throw new BusinessException(HttpStatus.CONFLICT, "SALE_SUSPENDED",
                    "Chuyến khởi hành đang tạm dừng mở bán, không thể tra cứu hoặc đặt phòng");
        }
        if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
            throw new BusinessException(HttpStatus.CONFLICT, "CRUISE_NOT_OPEN_FOR_SALE",
                    "Lịch trình chuyến khởi hành chưa mở bán thương mại");
        }
        if (cruise.getDepartureDatetime() != null && cruise.getDepartureDatetime().isBefore(Instant.now())) {
            throw new BusinessException("CRUISE_ALREADY_DEPARTED",
                    "Chuyến khởi hành đã khởi hành, không thể đặt phòng");
        }
        }
        if (cruise.getShip() == null) {
            throw new BusinessException("CRUISE_NO_SHIP", "Cruise does not have an assigned ship yet");
        }

        Optional<CruisePricingPolicy> policyOpt = cruisePricingPolicyRepository.findByCruise_CruiseId(cruiseId)
                .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED);
        if (context==null&&policyOpt.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "PRICING_POLICY_NOT_APPROVED",
                    "Chính sách giá của chuyến khởi hành chưa được phê duyệt, không thể tra cứu giá hoặc phòng mở bán");
        }

        UUID shipId = cruise.getShip().getShipId();

        ShipModel model = includeModelPlacement ? shipModelRepository
                .findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(shipId, ShipModelStatus.READY)
                .stream().findFirst().orElse(null) : null;
        Map<UUID, ShipModelObject> modelObjectsByCabinId = model == null ? Map.of()
                : shipModelObjectRepository.findByShipModelShipModelId(model.getShipModelId()).stream()
                        .filter(object -> object.getCabin() != null)
                        .collect(Collectors.toMap(object -> object.getCabin().getCabinId(), object -> object, (first, ignored) -> first));

        List<Cabin> cabins = cabinRepository.searchAvailable(shipId, deckId, cabinType, minCapacity, null);
        List<AvailableCabinResponse> results = new ArrayList<>();
        var group=context==null?List.<BookingPassenger>of():bookingPassengerRepository.findByBookingBookingId(context.getBooking().getBookingId()).stream()
                .filter(p->p.getParticipationStatus()==BookingPassengerParticipationStatus.Active&&p.getFareOfferId()!=null&&Objects.equals(p.getCabinGroupKey(),context.getCabinGroupKey())).toList();
        var fulfilled=group.stream().map(BookingPassenger::getBookingPassengerId).collect(Collectors.toSet());
        boolean fareManaged=farePackages.requiresFareSelection(cruiseId);
        var sellableMappedCabins=context==null&&fareManaged?farePackages.sellableCabinIds(cruiseId):Set.<UUID>of();

        for (Cabin cabin : cabins) {
            if(context==null&&fareManaged&&!sellableMappedCabins.contains(cabin.getCabinId()))continue;
            if(context!=null&&!cabinMappings.requirePurchasedCabins(farePurchases.intent(context)).contains(cabin.getCabinId()))continue;
            long currentOccupied = cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabin.getCabinId(), cruiseId);
            int remaining = cabin.getCapacity() - (int) currentOccupied;
            if(context!=null)remaining+=(int)group.stream().filter(p->cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(p.getBookingPassengerId())
                    .map(a->a.getCabin().getCabinId().equals(cabin.getCabinId())).orElse(false)).count();
            if(context!=null&&remaining<group.size())continue;
            try {
                requireCabinOwnership(cabin.getCabinId(),cruiseId,context==null?null:context.getBooking().getBookingId(),context!=null||fareManaged);
                assertCategoryAllocation(cruiseId,shipId,List.of(),Set.of(cabin.getCabinId()),fulfilled);
            } catch(BusinessException blocked) {
                if(Set.of("CATEGORY_HOLD_ALLOCATION_BLOCKED","CABIN_RESERVED_FOR_OTHER_BOOKING").contains(blocked.getCode()))continue;
                throw blocked;
            }

            if (remaining > 0 && (minCapacity == null || remaining >= minCapacity)) {
                AvailableCabinResponse res = new AvailableCabinResponse();
                res.setCabinId(cabin.getCabinId());
                res.setCabinNumber(cabin.getCabinNumber());
                res.setDeckId(cabin.getDeck() != null ? cabin.getDeck().getDeckId() : null);
                res.setDeckNumber(cabin.getDeck() != null ? cabin.getDeck().getDeckNumber() : null);
                res.setDeckName(cabin.getDeck() != null ? cabin.getDeck().getDeckName() : null);
                res.setCabinType(cabin.getCabinType());
                res.setCapacity(cabin.getCapacity());
                res.setRemainingCapacity(remaining);
                res.setIsAccessible(cabin.getIsAccessible());
                BigDecimal multiplier = (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                        ? cabin.getDeck().getPriceMultiplier()
                        : BigDecimal.valueOf(1.00);
                res.setPriceMultiplier(multiplier);
                BigDecimal baseFare = getBaseCabinFareForCruise(cruiseId, cabin.getCabinType());
                res.setEstimatedPrice(context==null&&!fareManaged&&baseFare != null ? baseFare.multiply(multiplier).setScale(2, RoundingMode.HALF_UP) : null);
                if (includeModelPlacement) {
                    ShipModelObject mapping = modelObjectsByCabinId.get(cabin.getCabinId());
                    if (model != null) res.setModelId(model.getShipModelId());
                    if (mapping != null) {
                        res.setModelNodeKey(mapping.getNodeKey());
                    }
                    Double x = mapping != null && mapping.getPositionX() != null ? mapping.getPositionX() : cabin.getModelPositionX();
                    Double y = mapping != null && mapping.getPositionY() != null ? mapping.getPositionY() : cabin.getModelPositionY();
                    Double z = mapping != null && mapping.getPositionZ() != null ? mapping.getPositionZ() : cabin.getModelPositionZ();
                    Double rx = mapping != null && mapping.getRotationX() != null ? mapping.getRotationX() : cabin.getModelRotationX();
                    Double ry = mapping != null && mapping.getRotationY() != null ? mapping.getRotationY() : cabin.getModelRotationY();
                    Double rz = mapping != null && mapping.getRotationZ() != null ? mapping.getRotationZ() : cabin.getModelRotationZ();
                    if (x != null || y != null || z != null) {
                        res.setModelPosition(new Vector3(x, y, z));
                    }
                    if (rx != null || ry != null || rz != null) {
                        res.setModelRotation(new Vector3(rx, ry, rz));
                    }
                    if (cabin.getModelWidth() != null || cabin.getModelHeight() != null || cabin.getModelDepth() != null) {
                        res.setModelDimensions(new Vector3(
                                cabin.getModelWidth(), cabin.getModelHeight(), cabin.getModelDepth()));
                    }
                }
                results.add(res);
            }
        }

        return results;
    }

    /**
     * Tự động giải phóng các đơn đặt chỗ tạm (Draft/Hold) đã quá hạn giữ chỗ (Hold TTL).
     */
    @Transactional
    public int releaseExpiredHolds() {
        Instant now = Instant.now();
        List<Booking> expired = bookingRepository.findExpiredHoldBookings(now);
        int count = 0;
        for (Booking candidate : expired.stream().sorted(Comparator.comparing(Booking::getBookingId)).toList()) {
            Booking b=bookingRepository.findByIdForUpdate(candidate.getBookingId()).orElse(null);
            if(b==null)continue;
            // A concurrent confirmation must never be cancelled by a stale expiry scan.
            bookingEntityManager.refresh(b);
            if(b.getBookingStatus()!=BookingStatus.Draft||b.getHoldExpiresAt()==null||b.getHoldExpiresAt().isAfter(now))continue;
            b.setBookingStatus(BookingStatus.Cancelled);
            b.setCancelledAt(now);
            b.setCancellationReason("HOLD_EXPIRED");
            bookingRepository.save(b);

            List<BookingPassenger> passengers = bookingPassengerRepository.findByBookingBookingId(b.getBookingId());
            for (BookingPassenger bp : passengers) {
                bp.setParticipationStatus(BookingPassengerParticipationStatus.Cancelled);
                bookingPassengerRepository.save(bp);

                cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(bp.getBookingPassengerId())
                        .ifPresent(ca -> {
                            ca.setValidTo(now);
                            ca.setChangeReason("Đơn giữ chỗ đã hết hạn (Hold TTL expired)");
                            cabinAssignmentRepository.save(ca);
                        });
            }
            count++;
        }
        if (count > 0) {
            log.info("Released {} expired booking holds", count);
        }
        return count;
    }

    /**
     * Đặt chỗ chuyến đi mới - Hành khách tự chọn phòng cụ thể và gói dịch vụ.
     */
    @Transactional
    public BookingDetailResponse createBooking(BookingCreateRequest request, CasmsPrincipal principal) {
        log.info("Creating new booking for cruise: {}", request.getCruiseId());
        if (request.getCurrency() != null && !request.getCurrency().isBlank())
            com.casms.common.util.NewWriteCurrency.requireUsd(request.getCurrency().trim().toUpperCase(java.util.Locale.ROOT));
        releaseExpiredHolds();

        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new BusinessException("PASSENGERS_REQUIRED", "At least one passenger must be included in the booking");
        }
        boolean fareBooking=validateSelectionShape(request.getPassengers());

        // Validate primary passenger contact information for guest booking
        PassengerBookingItemRequest primaryPassenger = request.getPassengers().get(0);
        if (principal == null && (primaryPassenger.getEmail() == null || primaryPassenger.getEmail().isBlank())) {
            throw new BusinessException("PRIMARY_EMAIL_REQUIRED", "Email of the primary passenger is required for booking confirmation and contract signing");
        }

        // Validate document numbers, cabin assignment presence, and consistency within the booking request
        Set<String> documentNumbers = new HashSet<>();
        for (PassengerBookingItemRequest pReq : request.getPassengers()) {
            if (pReq.getDocumentNumber() != null && !pReq.getDocumentNumber().isBlank()) {
                String doc = pReq.getDocumentNumber().trim().toUpperCase();
                if (!documentNumbers.add(doc)) {
                    throw new BusinessException("DUPLICATE_PASSENGER_DOCUMENT",
                            "Duplicate passenger document number in the same booking: " + pReq.getDocumentNumber());
                }
            }
            if (pReq.getDocumentType() != null && (pReq.getDocumentNumber() == null || pReq.getDocumentNumber().isBlank())) {
                throw new BusinessException("DOCUMENT_NUMBER_REQUIRED",
                        "Document number is required when document type is specified for passenger: " + pReq.getFullName());
            }
        }

        User bookedByUser;
        if (principal != null && principal.getUserId() != null) {
            bookedByUser = getUser(principal.getUserId());
        } else {
            // Guest booking: Hành khách đặt tour khi chưa đăng nhập
            bookedByUser = resolveOrCreateGuestUser(primaryPassenger);
        }

        // 1. Kiểm tra Chuyến đi (Cruise)
        Cruise cruise = cruiseRepository.findByIdForUpdate(request.getCruiseId())
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", request.getCruiseId()));
        mainflow.requireBookable(cruise.getCruiseId());
        if (cruise.getOpenedForSaleAt() == null || cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("INVALID_CRUISE_STATUS", "Bookings can only be created for PUBLISHED cruises (Current: " + cruise.getStatus() + ")");
        }
        // BE-08B / BE-R00: Gating - Check if itinerary exists and is OPEN_FOR_SALE
        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId())
                .orElseThrow(() -> new BusinessException("CRUISE_NOT_OPEN_FOR_SALE", "Chuyến khởi hành chưa có lịch trình mở bán thương mại"));
        if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
            throw new BusinessException("SALE_SUSPENDED", "Chuyến khởi hành đang tạm dừng mở bán, không thể tiếp nhận đặt chỗ mới");
        }
        if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
            throw new BusinessException("CRUISE_NOT_OPEN_FOR_SALE", "Chuyến khởi hành chưa mở bán thương mại (Trạng thái lịch trình: " + itinerary.getStatus() + ")");
        }
        if (cruise.getDepartureDatetime().isBefore(Instant.now())) {
            throw new BusinessException("CRUISE_ALREADY_DEPARTED", "Cannot book a cruise that has already departed");
        }
        if (cruise.getShip() == null) {
            throw new BusinessException("CRUISE_NO_SHIP", "Cruise does not have an assigned ship yet");
        }

        cabinResources(cruise.getShip().getShipId(),true);
        var fareSelections=prepareFares(cruise.getCruiseId(),request.getPassengers());
        // 2. Kiểm tra tổng sức chứa tàu
        int totalNewPassengers = request.getPassengers().size();
        long currentBookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruise.getCruiseId());
        if (currentBookedPassengers + totalNewPassengers > cruise.getShip().getTotalCapacity()) {
            throw new BusinessException("EXCEEDS_SHIP_CAPACITY",
                    String.format("Not enough capacity on ship (Total: %d, Booked: %d, Requested: %d)",
                            cruise.getShip().getTotalCapacity(), currentBookedPassengers, totalNewPassengers));
        }

        // 3. Kiểm tra các cabin khách hàng tự chọn
        // Gom nhóm hành khách theo cabinId để kiểm tra sức chứa phòng
        Map<UUID, List<PassengerBookingItemRequest>> cabinPassengersMap = request.getPassengers().stream()
                .filter(p->p.getCabinId()!=null)
                .collect(Collectors.groupingBy(PassengerBookingItemRequest::getCabinId));

        List<UUID> sortedCabinIds = cabinPassengersMap.keySet().stream().sorted().toList();

        Map<UUID, Cabin> validatedCabins = new HashMap<>();
        for (UUID cabinId : sortedCabinIds) {
            List<PassengerBookingItemRequest> passengersInCabin = cabinPassengersMap.get(cabinId);

            Cabin cabin = cabinRepository.findByIdForUpdate(cabinId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId));

            if (!cabin.getShip().getShipId().equals(cruise.getShip().getShipId())) {
                throw new BusinessException("CABIN_SHIP_MISMATCH",
                        "Cabin " + cabin.getCabinNumber() + " does not belong to the cruise ship: " + cruise.getShip().getShipName());
            }

            if (cabin.getStatus() != CabinStatus.AVAILABLE) {
                throw new BusinessException("CABIN_NOT_AVAILABLE",
                        "Cabin " + cabin.getCabinNumber() + " is currently in status: " + cabin.getStatus());
            }

            long currentOccupied = cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabinId, cruise.getCruiseId());
            requireCabinOwnership(cabinId,cruise.getCruiseId(),null,fareBooking);
            for(var passenger:passengersInCabin)if(fareSelections.containsKey(passenger)&&!cabinMappings.requirePurchasedCabins(fareSelections.get(passenger)).contains(cabin.getCabinId()))
                throw new BusinessException("FARE_CABIN_CATEGORY_MISMATCH","Cabin vật lý không phù hợp với hạng phòng của gói vé đã chọn.");
            int remaining = cabin.getCapacity() - (int) currentOccupied;

            if (passengersInCabin.size() > remaining) {
                throw new BusinessException("CABIN_OVERCAPACITY",
                        String.format("Cabin %s has only %d available slot(s), but %d passengers were assigned to it",
                                cabin.getCabinNumber(), remaining, passengersInCabin.size()));
            }

            validatedCabins.put(cabinId, cabin);
        }
        assertCategoryAllocation(cruise.getCruiseId(),cruise.getShip().getShipId(),categoryDemand(request.getPassengers(),fareSelections),cabinPassengersMap.keySet(),Set.of());

        // BE-10: Kiểm tra tồn kho theo từng CabinType (ngăn oversell theo hạng phòng)
        Map<CabinType, Long> requestedPassengersByType = request.getPassengers().stream()
                .map(p -> fareSelections.containsKey(p)?fareSelections.get(p).cabinType():validatedCabins.get(p.getCabinId()).getCabinType())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        for (Map.Entry<CabinType, Long> entry : requestedPassengersByType.entrySet()) {
            CabinType type = entry.getKey();
            Long requestedSlots = entry.getValue();
            Long totalSlotsForType = cabinRepository.sumCapacityByShipIdAndCabinType(cruise.getShip().getShipId(), type);
            if (totalSlotsForType == null || totalSlotsForType == 0) {
                throw new BusinessException("CABIN_TYPE_NOT_CONFIGURED",
                        "Hạng phòng " + type + " chưa có chỗ khả dụng trên tàu: " + cruise.getShip().getShipName());
            }
            long activeAssignmentsForType = cabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise(type, cruise.getCruiseId());
            long remainingSlotsForType = Math.max(0, totalSlotsForType - activeAssignmentsForType);
            if (requestedSlots > remainingSlotsForType) {
                throw new BusinessException("CABIN_TYPE_EXHAUSTED",
                        String.format("Hạng phòng %s không còn đủ chỗ khả dụng (Còn lại: %d, Yêu cầu: %d). Hệ thống từ chối bán để chống oversell.",
                                type, remainingSlotsForType, requestedSlots));
            }
        }

        // 4. Kiểm tra các gói tour (TourPackage) và tính tổng tiền vé
        Map<UUID, TourPackage> validatedPackages = new HashMap<>();
        BigDecimal totalPackageAmount = BigDecimal.ZERO;
        BigDecimal totalCabinBaseAmount = BigDecimal.ZERO;
        BigDecimal totalFareAmount=BigDecimal.ZERO;

        BookingQuote validatedQuote = null;
        boolean isStaff = principal != null && (principal.hasAuthority("BOOKING_MANAGE") || principal.hasAuthority("ROLE_ADMIN"));
        if (request.getQuoteId() == null && (!isStaff||fareBooking)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "QUOTE_REQUIRED",
                    "Báo giá (quoteId) là bắt buộc đối với khách hàng khi tạo đơn đặt chỗ.");
        }

        if (request.getQuoteId() != null) {
            validatedQuote = bookingQuoteRepository.findById(request.getQuoteId())
                    .orElseThrow(() -> new ResourceNotFoundException("BookingQuote", request.getQuoteId()));

            if ("COMMITTED".equalsIgnoreCase(validatedQuote.getStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_ALREADY_COMMITTED",
                        "Báo giá này đã được sử dụng để tạo đơn đặt chỗ trước đó. Không thể tái sử dụng.");
            }
            if (!validatedQuote.getCruise().getCruiseId().equals(cruise.getCruiseId())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_CRUISE_MISMATCH",
                        "Báo giá không thuộc về chuyến khởi hành này");
            }
            if ("INVALIDATED".equalsIgnoreCase(validatedQuote.getStatus()) || "CANCELLED".equalsIgnoreCase(validatedQuote.getStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_INVALIDATED",
                        "Báo giá không còn hiệu lực do chính sách giá đã thay đổi hoặc đã bị hủy");
            }
            if (validatedQuote.isExpired()) {
                throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_EXPIRED",
                        "Báo giá đã hết hạn, vui lòng yêu cầu báo giá mới trước khi hoàn tất đặt vé");
            }
            if (validatedQuote.getUser() != null) {
                boolean isOwner = principal != null && validatedQuote.getUser().getUserId().equals(principal.getUserId());
                if (!isOwner && !isStaff) {
                    throw new BusinessException(HttpStatus.FORBIDDEN, "QUOTE_OWNERSHIP_MISMATCH",
                            "Không thể sử dụng báo giá của tài khoản người dùng khác");
                }
            } else if (!isStaff && (validatedQuote.getGuestToken() == null || validatedQuote.getGuestToken().isBlank() || request.getQuoteGuestToken() == null
                    || !java.security.MessageDigest.isEqual(request.getQuoteGuestToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.util.Objects.toString(validatedQuote.getGuestToken(), "").getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "QUOTE_OWNERSHIP_MISMATCH",
                        "Không thể sử dụng báo giá của khách vãng lai khác");
            }

            // BE-H03: Verify exact line-by-line selection against quote snapshot
            List<QuoteLineItem> quoteLineItems = null;
            if (validatedQuote.getQuotePayloadJson() != null && !validatedQuote.getQuotePayloadJson().isBlank()) {
                try {
                    quoteLineItems = objectMapper.readValue(validatedQuote.getQuotePayloadJson(),
                            new TypeReference<List<QuoteLineItem>>() {});
                } catch (Exception e) {
                    log.error("Failed to parse quotePayloadJson for quoteId {}: {}", validatedQuote.getQuoteId(), e.getMessage());
                }
            }
            if (quoteLineItems == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_PAYLOAD_MISSING",
                        "Dữ liệu chi tiết các dòng báo giá không tồn tại hoặc bị lỗi.");
            }
            if (request.getPassengers().size() != quoteLineItems.size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "QUOTE_SELECTION_MISMATCH",
                        String.format("Số lượng hành khách (%d) không khớp với báo giá (%d).",
                                request.getPassengers().size(), quoteLineItems.size()));
            }
            Map<String, Long> requestSelections = request.getPassengers().stream()
                    .map(this::selectionKey)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            Map<String, Long> quoteSelections = quoteLineItems.stream()
                    .map(this::selectionKey)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            if (!requestSelections.equals(quoteSelections)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "QUOTE_SELECTION_MISMATCH",
                        "Danh sách phòng hoặc gói dịch vụ của hành khách không khớp chính xác với báo giá đã cam kết.");
            }
            if(fareBooking)for(var item:request.getPassengers()) {
                var captured=quoteLineItems.stream().filter(line->selectionKey(line).equals(selectionKey(item))).findFirst().orElseThrow().getFareSelection();
                var current=fareSelections.get(item);
                if(captured==null||!captured.templateVersionId().equals(current.templateVersionId())
                        ||captured.commercialRevision()!=current.commercialRevision()||captured.cabinType()!=current.cabinType()
                        ||captured.amount().compareTo(current.amount())!=0||!captured.shipId().equals(current.shipId())
                        ||!Objects.equals(captured.logicalCategoryId(),current.logicalCategoryId())
                        ||!Objects.equals(captured.cabinMappingId(),current.cabinMappingId())
                        ||!new HashSet<>(captured.physicalCabinIds()).equals(new HashSet<>(current.physicalCabinIds())))
                    throw new BusinessException(HttpStatus.CONFLICT,"FARE_QUOTED_PRICE_STALE","Nội dung gói vé không còn khớp báo giá; cần lấy báo giá mới.");
                // Keep the exact quoted rights/names, never reconstruct them from current catalog content.
                fareSelections.put(item,captured);
            }
        }

        CruisePricingPolicy policy = cruisePricingPolicyRepository.findByCruise_CruiseId(cruise.getCruiseId())
                .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_MISSING",
                        "Chuyến khởi hành chưa có chính sách giá được phê duyệt. Không thể hoàn tất đặt chỗ."));

        com.casms.common.util.NewWriteCurrency.requireUsd(policy.getCurrency());
        if (validatedQuote != null) {
            com.casms.common.util.NewWriteCurrency.requireUsd(validatedQuote.getCurrency());
            if (!policy.getPolicyVersion().equals(validatedQuote.getPolicyVersion())) {
                throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_POLICY_MISMATCH",
                        "Báo giá thuộc phiên bản chính sách giá cũ (" + validatedQuote.getPolicyVersion() +
                        "), chính sách hiện tại là (" + policy.getPolicyVersion() + "). Vui lòng yêu cầu báo giá mới.");
            }
        }

        for (PassengerBookingItemRequest pReq : request.getPassengers()) {
            if(fareSelections.containsKey(pReq)) { totalFareAmount=totalFareAmount.add(fareSelections.get(pReq).amount()); continue; }
            Cabin cabin = validatedCabins.get(pReq.getCabinId());
            BigDecimal baseFare = policy.getBaseFareForCabinType(cabin.getCabinType());
            if (baseFare == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CABIN_TYPE_UNPRICED",
                        "Hạng phòng " + cabin.getCabinType() + " chưa được định giá trong chính sách của chuyến khởi hành");
            }
            BigDecimal multiplier = (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                    ? cabin.getDeck().getPriceMultiplier()
                    : BigDecimal.valueOf(1.00);
            BigDecimal cabinPrice = baseFare.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            totalCabinBaseAmount = totalCabinBaseAmount.add(cabinPrice);

            if (pReq.getTourPackageId() != null) {
                TourPackage pkg = tourPackageRepository.findById(pReq.getTourPackageId())
                        .orElseThrow(() -> new ResourceNotFoundException("TourPackage", pReq.getTourPackageId()));
                if (pkg.getStatus() != TourPackageStatus.ACTIVE) {
                    throw new BusinessException("PACKAGE_NOT_ACTIVE", "Tour package is not currently ACTIVE: " + pkg.getPackageName());
                }
                // BE-07 / BE-08B / BE-12: Product gate - Package must be assigned to this Cruise and active for sale
                boolean assignedToCruise = cruiseTourPackageRepository
                        .existsByCruise_CruiseIdAndTourPackage_TourPackageIdAndActiveTrue(cruise.getCruiseId(), pkg.getTourPackageId());
                if (!assignedToCruise) {
                    throw new BusinessException("PACKAGE_NOT_AVAILABLE_FOR_CRUISE",
                            "Gói dịch vụ " + pkg.getPackageName() + " chưa được mở bán cho chuyến khởi hành này");
                }
                validatedPackages.put(pReq.getTourPackageId(), pkg);
                com.casms.common.util.NewWriteCurrency.requireUsd(pkg.getCurrencyCode());
                totalPackageAmount = totalPackageAmount.add(pkg.getPrice() != null ? pkg.getPrice() : BigDecimal.ZERO);
            }
        }

        // 5. Xác định hình thức thanh toán & mức cọc (hỗ trợ quote authoritative hoặc dynamic policy)
        PaymentPlan paymentPlan = request.getPaymentPlan() != null ? request.getPaymentPlan() : PaymentPlan.DEPOSIT;
        BigDecimal agreedFareAmount;
        BigDecimal requiredDepositAmount;
        String currency;
        Instant scheduleCreatedAt = Instant.now();
        Instant finalPaymentDueAt;

        if (validatedQuote != null) {
            agreedFareAmount = validatedQuote.getTotalAmount();
            requiredDepositAmount = validatedQuote.getDepositAmount();
            paymentPlan = validatedQuote.getPaymentPlan();
            finalPaymentDueAt = validatedQuote.getFinalPaymentDueAt();
            currency = validatedQuote.getCurrency();

            validatedQuote.setStatus("COMMITTED");
            bookingQuoteRepository.save(validatedQuote);
        } else {
            BigDecimal subtotalAmount = totalCabinBaseAmount.add(totalPackageAmount).add(totalFareAmount);
            BigDecimal portFeePerPassenger = policy.getPortFeePerPassenger() != null ? policy.getPortFeePerPassenger() : BigDecimal.ZERO;
            BigDecimal portFeesAmount = portFeePerPassenger.multiply(BigDecimal.valueOf(request.getPassengers().size()));
            BigDecimal taxRate = policy.getTaxRate() != null ? policy.getTaxRate() : BigDecimal.ZERO;
            BigDecimal taxesAmount = subtotalAmount.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal taxesAndFeesAmount = portFeesAmount.add(taxesAmount);
            agreedFareAmount = subtotalAmount.add(taxesAndFeesAmount);

            int cutoffDays = policy.getFullPaymentCutoffDays();
            if (cruise.getDepartureDatetime().isBefore(scheduleCreatedAt.plus(Duration.ofDays(cutoffDays)))) {
                paymentPlan = PaymentPlan.FULL; // BE-11: booking vượt hạn cutoff phải áp đúng FULL theo policy
            }

            if (paymentPlan == PaymentPlan.FULL) {
                requiredDepositAmount = agreedFareAmount;
            } else {
                BigDecimal depositRate = policy.getDepositPercentage();
                requiredDepositAmount = agreedFareAmount.multiply(depositRate).setScale(4, RoundingMode.HALF_UP);
            }

            currency = (request.getCurrency() != null && !request.getCurrency().isBlank())
                    ? request.getCurrency().toUpperCase()
                    : policy.getCurrency();

            int dueDays = policy.getFinalPaymentDueDays();
            finalPaymentDueAt = cruise.getDepartureDatetime().minus(Duration.ofDays(dueDays));
            if (finalPaymentDueAt.isBefore(scheduleCreatedAt)) {
                finalPaymentDueAt = scheduleCreatedAt;
            }
        }

        validatePaymentSchedule(paymentPlan, scheduleCreatedAt, finalPaymentDueAt, cruise.getDepartureDatetime());

        // Tạo đơn Booking
        Booking booking = new Booking();
        booking.setCruise(cruise);
        booking.setBookedByUser(bookedByUser);
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setAgreedFareAmount(agreedFareAmount);
        booking.setCurrency(currency);
        booking.setRequiredDepositAmount(requiredDepositAmount);
        booking.setFinalPaymentDueAt(finalPaymentDueAt);
        booking.setHoldExpiresAt(scheduleCreatedAt.plus(mainflow.requireTransactionDuration(cruise.getCruiseId(), "BOOKING_HOLD")));
        booking.setBookingReference(businessCodeGenerator.next("BK"));
        if (validatedQuote != null) {
            booking.setQuote(validatedQuote);
            booking.setPricingPolicyVersion(validatedQuote.getPolicyVersion());
        } else {
            booking.setPricingPolicyVersion(policy.getPolicyVersion());
        }
        booking = bookingRepository.save(booking);

        // 6. Tạo danh sách hành khách, gán phòng tự chọn, gán gói dịch vụ
        List<BookingPassenger> createdBookingPassengers = new ArrayList<>();
        BookingPassenger representative = null;

        for (PassengerBookingItemRequest pReq : request.getPassengers()) {
            Cabin cabin = validatedCabins.get(pReq.getCabinId());

            // Lưu hồ sơ Passenger (nếu là hành khách đại diện đầu tiên và có bookedByUser thì liên kết user)
            Passenger passenger;
            if (representative == null && bookedByUser != null) {
                passenger = passengerRepository.findByUser_UserId(bookedByUser.getUserId()).orElseGet(() -> {
                    Passenger p = new Passenger();
                    p.setUser(bookedByUser);
                    return p;
                });
            } else {
                passenger = new Passenger();
            }
            passenger.setFullName(pReq.getFullName().trim());
            passenger.setDateOfBirth(pReq.getDateOfBirth());
            passenger.setNationality(pReq.getNationality() != null ? pReq.getNationality().trim().toUpperCase() : null);
            passenger.setDocumentType(pReq.getDocumentType());
            passenger.setDocumentNumberProtected(pReq.getDocumentNumber() != null ? pReq.getDocumentNumber().trim().toUpperCase() : null);
            passenger.setEmail(pReq.getEmail() != null ? pReq.getEmail().trim().toLowerCase() : null);
            passenger.setPhoneNumber(pReq.getPhoneNumber() != null ? pReq.getPhoneNumber().trim() : null);
            passenger = passengerRepository.save(passenger);

            // Lưu BookingPassenger
            BookingPassenger bp = new BookingPassenger();
            bp.setBooking(booking);
            bp.setPassenger(passenger);
            bp.setParticipationStatus(BookingPassengerParticipationStatus.Active);
            var fare=fareSelections.get(pReq);
            bp.setRequestedCabinCategory(fare==null?cabin.getCabinType().name():fare.cabinType().name());
            if(fare!=null) {
                bp.setCabinSelectionMode(selectionMode(pReq)); bp.setCabinGroupKey(cabinGroup(pReq));
                bp.setFareOfferId(fare.offerId()); bp.setFarePriceRevisionId(fare.priceRevisionId());
                bp.setFareTemplateVersionId(fare.templateVersionId()); bp.setFareCabinCategory(fare.cabinType().name());
                bp.setFareSelectionPayload(serializeFareSelection(fare));
            }
            bp = bookingPassengerRepository.save(bp);
            createdBookingPassengers.add(bp);

            if (representative == null) {
                representative = bp;
            }

            // Gán phòng tự chọn (CabinAssignment)
            if(cabin!=null) {
            CabinAssignment assignment = new CabinAssignment();
            assignment.setBookingPassenger(bp);
            assignment.setCabin(cabin);
            assignment.setValidFrom(Instant.now());
            assignment.setValidTo(null);
            assignment.setAssignedByUser(bookedByUser);
            assignment.setChangeReason("Khách hàng tự chọn phòng khi đặt tour");
            cabinAssignmentRepository.save(assignment);
            }

            // Gán gói dịch vụ (BookingPassengerPackage) nếu có chọn
            if (pReq.getTourPackageId() != null) {
                TourPackage pkg = validatedPackages.get(pReq.getTourPackageId());
                BookingPassengerPackage bpp = new BookingPassengerPackage();
                bpp.setBookingPassenger(bp);
                bpp.setTourPackage(pkg);
                bpp.setAssignedAt(Instant.now());
                bpp.setAgreedPrice(pkg.getPrice());
                bpp.setCurrency(currency);
                bookingPassengerPackageRepository.save(bpp);
            }
        }

        // Cập nhật người đại diện booking
        if (representative != null) {
            booking.setRepresentativeBookingPassengerId(representative.getBookingPassengerId());
            booking = bookingRepository.save(booking);
        }

        // 7. Tạo hợp đồng dự thảo (BookingContract)
        // Lưu ý bảo mật: Hợp đồng chỉ lưu mã tham chiếu đối chiếu, KHÔNG lưu file/nội dung thô vào Database
        BookingContract contract = new BookingContract();
        contract.setBooking(booking);
        contract.setContractReference("CTR-" + booking.getBookingReference());
        contract.setDocumentReference("DOC-REF-SECURE-" + booking.getBookingReference());
        contract.setStatus(BookingContractStatus.Draft);
        bookingContractRepository.save(contract);

        // 8. Khởi tạo các đợt thanh toán (Installments) dựa theo hình thức khách chọn
        createInstallmentsForPlan(booking, paymentPlan, agreedFareAmount, requiredDepositAmount,
                finalPaymentDueAt, cruise.getDepartureDatetime(), scheduleCreatedAt);

        // 9. Tạo thông báo liên lạc mời hành khách tới văn phòng ký hợp đồng bản cứng
        createContractSigningNotification(booking, contract, representative, paymentPlan, cruise);

        log.info("Booking created successfully with reference: {}, PaymentPlan: {}", booking.getBookingReference(), paymentPlan);
        return mapToDetailResponse(booking);
    }

    /**
     * Lấy chi tiết đơn đặt chỗ theo ID.
     */
    @Transactional(readOnly = true)
    public BookingDetailResponse detail(UUID bookingId, CasmsPrincipal principal) {
        Booking booking = getBooking(bookingId);
        validateAccess(booking, principal);
        return mapToDetailResponse(booking);
    }

    /**
     * Tra cứu danh sách đơn đặt chỗ (có phân quyền khách hàng xem của mình, agent xem toàn bộ).
     */
    @Transactional(readOnly = true)
    public Page<BookingSummaryResponse> list(UUID cruiseId, BookingStatus status, Pageable pageable, CasmsPrincipal principal) {
        boolean isStaffOrAgent = principal != null && (principal.isAdmin() ||
                principal.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_BOOKING_AGENT") ||
                        a.getAuthority().equals("ROLE_FINANCE_OFFICER") ||
                        a.getAuthority().equals("ROLE_OPERATIONS_MANAGER") ||
                        (principal.isStaff() && a.getAuthority().equals("BOOKING_READ"))));

        UUID filterUserId = null;
        if (!isStaffOrAgent && principal != null) {
            filterUserId = principal.getUserId();
        }

        Page<Booking> page = bookingRepository.searchBookings(cruiseId, status, filterUserId, pageable);
        return page.map(this::mapToSummaryResponse);
    }

    /**
     * Xác nhận hợp đồng đã ký bản cứng.
     */
    @Transactional
    public BookingDetailResponse acceptContract(UUID bookingId, BookingContractAcceptRequest request, CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null || principal.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("BOOKING_CONTRACT_MANAGE") || a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "BOOKING_CONTRACT_FORBIDDEN",
                    "Accepting physical contract requires BOOKING_CONTRACT_MANAGE permission");
        }
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Cannot accept contract for a cancelled booking");
        }

        BookingContract contract = bookingContractRepository.findByBookingBookingId(bookingId)
                .orElseThrow(() -> new BusinessException("BOOKING_CONTRACT_REQUIRED", "Booking contract is required"));

        if (contract.getStatus() == BookingContractStatus.Accepted) {
            return mapToDetailResponse(booking);
        }

        List<BookingPassenger> passengers = bookingPassengerRepository.findByBookingBookingId(bookingId);
        final UUID requestedSignerId = request.getAcceptedByBookingPassengerId();
        UUID signerId = requestedSignerId;
        if (requestedSignerId != null) {
            boolean belongs = passengers.stream().anyMatch(bp -> bp.getBookingPassengerId().equals(requestedSignerId));
            if (!belongs) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSENGER_MISMATCH",
                        "The signer must be an active passenger in this booking");
            }
        } else if (!passengers.isEmpty()) {
            signerId = passengers.get(0).getBookingPassengerId();
        }

        contract.setStatus(BookingContractStatus.Accepted);
        contract.setDocumentReference(request.getDocumentReference().trim());
        contract.setAcceptedAt(request.getAcceptedAt() != null ? request.getAcceptedAt() : Instant.now());
        contract.setAcceptedByBookingPassengerId(signerId);
        bookingContractRepository.save(contract);

        log.info("Contract {} accepted for booking {} by staff {} with document ref {}",
                contract.getContractReference(), bookingId, principal.getUsername(), request.getDocumentReference());

        // Check if deposit is already paid; if so, auto-confirm booking!
        tryAutoConfirm(booking);

        return mapToDetailResponse(booking);
    }

    /**
     * Xác nhận đơn đặt chỗ (sau khi cọc hoặc thanh toán thành công).
     */
    @Transactional
    public BookingDetailResponse confirm(UUID bookingId, CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null || principal.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("BOOKING_CONFIRM"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "BOOKING_CONFIRM_FORBIDDEN",
                    "Booking confirmation requires the BOOKING_CONFIRM permission");
        }
        return confirmInternal(bookingId);
    }

    /**
     * Thực hiện logic xác nhận đơn đặt chỗ bên trong hệ thống.
     */
    @Transactional
    public BookingDetailResponse confirmInternal(UUID bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getBookingStatus() == BookingStatus.Confirmed) {
            return mapToDetailResponse(booking);
        }
        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Cannot confirm a cancelled booking");
        }

        BookingContract contract = bookingContractRepository.findByBookingBookingId(bookingId)
                .orElseThrow(() -> new BusinessException("BOOKING_CONTRACT_REQUIRED", "Booking contract is required"));
        if (contract.getStatus() != BookingContractStatus.Accepted) {
            throw new BusinessException("BOOKING_CONTRACT_NOT_ACCEPTED", "Booking contract must be accepted before confirmation");
        }

        UUID capacityCruiseId = booking.getCruise().getCruiseId();
        Cruise capacityLockedCruise = cruiseRepository.findByIdForUpdate(capacityCruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", capacityCruiseId));
        if (capacityLockedCruise.getStatus() == CruiseStatus.CANCELLED) {
            throw new BusinessException("CRUISE_CANCELLED", "Cannot confirm booking on a cancelled cruise");
        }
        if (capacityLockedCruise.getStoppedAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "CRUISE_SALE_STOPPED",
                    "Cannot confirm booking when cruise sale is stopped");
        }
        itineraryRepository.findByCruiseCruiseId(capacityCruiseId).ifPresent(itinerary -> {
            if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
                throw new BusinessException(HttpStatus.CONFLICT, "SALE_SUSPENDED",
                        "Cannot confirm booking when cruise sale is suspended");
            }
            if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
                throw new BusinessException(HttpStatus.CONFLICT, "CRUISE_NOT_OPEN_FOR_SALE",
                        "Cruise is not currently open for sale");
            }
        });
        // Recheck the persisted Ready, commercial approval and booking window under
        // the same Cruise lock used by create, quote, Open Sale and Stop Sale.
        // This gate does not subtract this booking's own cabin/category hold.
        mainflow.requireBookable(capacityCruiseId);
        String bookingCurrency = booking.getCurrency();
        BigDecimal collectedAmount = paymentRepository.findForBooking(bookingId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.Confirmed
                        && payment.getConfirmedAt() != null
                        && payment.getConfirmedAmount() != null
                        && payment.getConfirmedAmount().signum() > 0
                        && paymentBelongsToBooking(payment, bookingId)
                        && paymentHasSettlementEvidence(payment)
                        && payment.getOperationType() == PaymentOperationType.Collect
                        && payment.getCurrency() != null
                        && payment.getCurrency().trim().equalsIgnoreCase(bookingCurrency))
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedRefunds = refundRepository.findForBooking(bookingId).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.Completed
                        && refund.getCompletedAt() != null
                        && refund.getApprovedAmount() != null
                        && refund.getApprovedAmount().signum() > 0
                        && refund.getCurrency() != null
                        && refund.getCurrency().trim().equalsIgnoreCase(bookingCurrency)
                        && refund.getProcessorOrReceiptReference() != null
                        && !refund.getProcessorOrReceiptReference().isBlank())
                .map(Refund::getApprovedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        collectedAmount = collectedAmount.subtract(completedRefunds);
        if (collectedAmount.compareTo(booking.getRequiredDepositAmount()) < 0) {
            throw new BusinessException("BOOKING_DEPOSIT_NOT_PAID",
                    "Verified collected payments do not cover the required booking deposit");
        }

        if (capacityLockedCruise.getShip() == null
                || bookingPassengerRepository.countActivePassengersByCruiseId(capacityLockedCruise.getCruiseId())
                    > capacityLockedCruise.getShip().getTotalCapacity()) {
            throw new BusinessException("BOOKING_CAPACITY_INVALID", "Booking capacity is no longer valid");
        }
        List<BookingPassenger> capacityPassengers = bookingPassengerRepository.findByBookingBookingId(bookingId);
        for (BookingPassengerPackage purchased : bookingPassengerPackageRepository.findByBookingPassengerBookingBookingId(bookingId)) {
            if (purchased.getBookingPassenger().getParticipationStatus() != BookingPassengerParticipationStatus.Active) continue;
            TourPackage pkg = purchased.getTourPackage();
            if (pkg == null || pkg.getStatus() != TourPackageStatus.ACTIVE
                    || "CABIN_ONLY".equals(capacityLockedCruise.getSaleScope())
                    || !cruiseTourPackageRepository.existsByCruise_CruiseIdAndTourPackage_TourPackageIdAndActiveTrue(
                            capacityCruiseId, pkg.getTourPackageId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "PACKAGE_NOT_AVAILABLE_FOR_CRUISE",
                        "Gói dịch vụ không còn được mở bán trong phạm vi chuyến khởi hành này.");
            }
        }
        for (BookingPassenger passenger : capacityPassengers) {
            if (passenger.getParticipationStatus() == BookingPassengerParticipationStatus.Active
                    && passenger.getFareOfferId() != null) {
                farePackages.validateHeldPurchase(capacityCruiseId, farePurchases.intent(passenger));
            }
        }
        if((booking.getHoldExpiresAt()!=null&&!booking.getHoldExpiresAt().isAfter(Instant.now()))
                ||(booking.getHoldExpiresAt()==null&&capacityPassengers.stream().anyMatch(p->p.getFareOfferId()!=null)))
            throw new BusinessException(HttpStatus.CONFLICT,"BOOKING_HOLD_EXPIRED","Giữ chỗ không còn hiệu lực; không xác nhận trên tài nguyên đã được giải phóng.");
        cabinResources(capacityLockedCruise.getShip().getShipId(),true);
        assertCategoryAllocation(capacityCruiseId,capacityLockedCruise.getShip().getShipId(),List.of(),Set.of(),Set.of());
        List<UUID> cabinIds = capacityPassengers.stream()
                .filter(passenger -> passenger.getParticipationStatus() == BookingPassengerParticipationStatus.Active)
                .filter(passenger -> !categoryHeld(passenger))
                .map(passenger -> cabinAssignmentRepository
                        .findByBookingPassengerBookingPassengerIdAndValidToIsNull(passenger.getBookingPassengerId())
                        .map(assignment -> assignment.getCabin().getCabinId())
                        .orElseThrow(() -> new BusinessException("BOOKING_CABIN_ASSIGNMENT_MISSING",
                                "An active booking passenger no longer has a cabin assignment")))
                .distinct().sorted().toList();
        Map<UUID, Cabin> capacityLockedCabins = new HashMap<>();
        for (UUID cabinId : cabinIds) {
            capacityLockedCabins.put(cabinId, cabinRepository.findByIdForUpdate(cabinId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId)));
        }
        for (BookingPassenger passenger : capacityPassengers) {
            if (passenger.getParticipationStatus() != BookingPassengerParticipationStatus.Active) continue;
            if(categoryHeld(passenger))continue;
            CabinAssignment assignment = cabinAssignmentRepository
                    .findByBookingPassengerBookingPassengerIdAndValidToIsNull(passenger.getBookingPassengerId())
                    .orElseThrow(() -> new BusinessException("BOOKING_CABIN_ASSIGNMENT_MISSING",
                            "An active booking passenger no longer has a cabin assignment"));
            Cabin lockedCabin = assignment.getCabin() == null ? null : capacityLockedCabins.get(assignment.getCabin().getCabinId());
            if (lockedCabin == null || cabinAssignmentRepository
                    .countActiveAssignmentsByCabinAndCruise(lockedCabin.getCabinId(), capacityLockedCruise.getCruiseId())
                    > lockedCabin.getCapacity()) {
                throw new BusinessException("BOOKING_CAPACITY_INVALID", "A cabin no longer has sufficient capacity");
            }
            if(passenger.getFareOfferId()!=null) {
                if(!lockedCabin.getShip().getShipId().equals(capacityLockedCruise.getShip().getShipId())
                        ||!cabinMappings.requirePurchasedCabins(farePurchases.intent(passenger)).contains(lockedCabin.getCabinId())
                        ||lockedCabin.getStatus()!=CabinStatus.AVAILABLE)
                    throw new BusinessException("FARE_CABIN_ASSIGNMENT_INVALID","Cabin giữ cho gói vé không còn phù hợp với tàu, hạng phòng hoặc trạng thái khai thác.");
                requireCabinOwnership(lockedCabin.getCabinId(),capacityCruiseId,bookingId,true);
            }
        }

        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setConfirmedAt(Instant.now());
        booking.setHoldExpiresAt(null);
        booking = bookingRepository.save(booking);

        if(capacityPassengers.stream().anyMatch(p->p.getFareOfferId()!=null))farePurchases.captureConfirmed(bookingId);
        log.info("Confirmed booking reference: {}", booking.getBookingReference());
        return mapToDetailResponse(booking);
    }

    /**
     * Tự động xác nhận đơn nếu hợp đồng đã chấp thuận và số tiền cọc đã thu đủ.
     */
    public boolean tryAutoConfirm(Booking booking) {
        if (booking.getBookingStatus() != BookingStatus.Draft) {
            return false;
        }
        BookingContract contract = bookingContractRepository.findByBookingBookingId(booking.getBookingId()).orElse(null);
        if (contract == null || contract.getStatus() != BookingContractStatus.Accepted) {
            return false;
        }
        String bookingCurrency = booking.getCurrency();
        BigDecimal collectedAmount = paymentRepository.findForBooking(booking.getBookingId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.Confirmed
                        && payment.getConfirmedAt() != null
                        && payment.getConfirmedAmount() != null
                        && payment.getConfirmedAmount().signum() > 0
                        && paymentBelongsToBooking(payment, booking.getBookingId())
                        && paymentHasSettlementEvidence(payment)
                        && payment.getOperationType() == PaymentOperationType.Collect
                        && payment.getCurrency() != null
                        && payment.getCurrency().trim().equalsIgnoreCase(bookingCurrency))
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedRefunds = refundRepository.findForBooking(booking.getBookingId()).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.Completed
                        && refund.getCompletedAt() != null
                        && refund.getApprovedAmount() != null
                        && refund.getApprovedAmount().signum() > 0
                        && refund.getCurrency() != null
                        && refund.getCurrency().trim().equalsIgnoreCase(bookingCurrency)
                        && refund.getProcessorOrReceiptReference() != null
                        && !refund.getProcessorOrReceiptReference().isBlank())
                .map(Refund::getApprovedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        collectedAmount = collectedAmount.subtract(completedRefunds);

        BigDecimal requiredDeposit = booking.getRequiredDepositAmount() != null
                ? booking.getRequiredDepositAmount()
                : booking.getAgreedFareAmount();
        if (requiredDeposit != null && collectedAmount.compareTo(requiredDeposit) >= 0) {
            try {
                confirmInternal(booking.getBookingId());
                return true;
            } catch (Exception e) {
                log.warn("Auto-confirmation failed for booking {}: {}", booking.getBookingId(), e.getMessage());
            }
        }
        return false;
    }

    /**
     * Hủy đơn đặt chỗ.
     */
    @Transactional
    public BookingDetailResponse cancel(UUID bookingId, BookingCancelRequest request, CasmsPrincipal principal) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        validateAccess(booking, principal);

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            return mapToDetailResponse(booking);
        }

        booking.setBookingStatus(BookingStatus.Cancelled);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(request.getReason());
        booking = bookingRepository.save(booking);

        // Hủy trạng thái tham gia của tất cả hành khách
        List<BookingPassenger> passengers = bookingPassengerRepository.findByBookingBookingId(bookingId);
        for (BookingPassenger bp : passengers) {
            bp.setParticipationStatus(BookingPassengerParticipationStatus.Cancelled);
            bookingPassengerRepository.save(bp);

            // Đóng hiệu lực gán phòng hiện hành
            cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(bp.getBookingPassengerId())
                    .ifPresent(ca -> {
                        ca.setValidTo(Instant.now());
                        ca.setChangeReason("Đơn đặt chỗ đã bị hủy: " + request.getReason());
                        cabinAssignmentRepository.save(ca);
                    });
        }

        // Hủy hợp đồng nếu có
        bookingContractRepository.findByBookingBookingId(bookingId).ifPresent(c -> {
            c.setStatus(BookingContractStatus.Cancelled);
            bookingContractRepository.save(c);
        });

        log.info("Cancelled booking reference: {} with reason: {}", booking.getBookingReference(), request.getReason());
        return mapToDetailResponse(booking);
    }

    /**
     * Đổi phòng cho hành khách đã đặt.
     */
    @Transactional
    public BookingDetailResponse changeCabin(CabinChangeRequest request, CasmsPrincipal principal) {
        BookingPassenger passengerSnapshot = bookingPassengerRepository.findById(request.getBookingPassengerId())
                .orElseThrow(() -> new ResourceNotFoundException("BookingPassenger", request.getBookingPassengerId()));

        UUID bookingId = passengerSnapshot.getBooking().getBookingId();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        BookingPassenger bp = bookingPassengerRepository.lockById(request.getBookingPassengerId())
                .orElseThrow(() -> new ResourceNotFoundException("BookingPassenger", request.getBookingPassengerId()));
        validateAccess(booking, principal);

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Cannot change cabin for a cancelled booking");
        }

        Cruise cruise = cruiseRepository.findByIdForUpdate(booking.getCruise().getCruiseId())
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", booking.getCruise().getCruiseId()));
        if(cruise.getShip()==null)throw new BusinessException("CRUISE_NO_SHIP","Chuyến khởi hành chưa có tàu để phân cabin.");
        cabinResources(cruise.getShip().getShipId(),true);
        if(bp.getFareOfferId()!=null)return assignFareCabinGroup(booking,bp,cruise,request,principal);
        // Legacy change remains physical-only and retains its existing staff authority boundary.
        if(!principal.hasAuthority("BOOKING_CABIN_ASSIGN")&&!principal.hasAuthority("BOOKING_UPDATE"))
            throw new BusinessException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chưa có quyền thay đổi cabin cho đặt chỗ này.");
        Cabin newCabin = cabinRepository.findByIdForUpdate(request.getNewCabinId())
                .orElseThrow(() -> new ResourceNotFoundException("Cabin", request.getNewCabinId()));

        if (!newCabin.getShip().getShipId().equals(cruise.getShip().getShipId())) {
            throw new BusinessException("CABIN_SHIP_MISMATCH", "New cabin does not belong to the cruise's ship");
        }
        if (newCabin.getStatus() != CabinStatus.AVAILABLE) {
            throw new BusinessException("CABIN_NOT_AVAILABLE", "New cabin is not AVAILABLE");
        }

        long currentOccupied = cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(newCabin.getCabinId(), cruise.getCruiseId());
        if (currentOccupied >= newCabin.getCapacity()) {
            throw new BusinessException("CABIN_FULL", "New cabin is already at full capacity");
        }
        requireCabinOwnership(newCabin.getCabinId(),cruise.getCruiseId(),bookingId,false);
        assertCategoryAllocation(cruise.getCruiseId(),cruise.getShip().getShipId(),List.of(),Set.of(newCabin.getCabinId()),Set.of(bp.getBookingPassengerId()));

        // Kết thúc gán phòng cũ
        cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(bp.getBookingPassengerId())
                .ifPresent(oldAssignment -> {
                    oldAssignment.setValidTo(Instant.now());
                    oldAssignment.setChangeReason(request.getChangeReason() != null ? request.getChangeReason() : "Chuyển sang phòng " + newCabin.getCabinNumber());
                    cabinAssignmentRepository.save(oldAssignment);
                });

        // Tạo bản ghi gán phòng mới
        User currentUser = getUser(principal.getUserId());
        CabinAssignment newAssignment = new CabinAssignment();
        newAssignment.setBookingPassenger(bp);
        newAssignment.setCabin(newCabin);
        newAssignment.setValidFrom(Instant.now());
        newAssignment.setValidTo(null);
        newAssignment.setAssignedByUser(currentUser);
        newAssignment.setChangeReason(request.getChangeReason() != null ? request.getChangeReason() : "Đổi phòng theo yêu cầu");
        cabinAssignmentRepository.save(newAssignment);

        bp.setRequestedCabinCategory(newCabin.getCabinType().name());
        bookingPassengerRepository.save(bp);

        log.info("Changed cabin for passenger: {} to cabin: {}", bp.getBookingPassengerId(), newCabin.getCabinNumber());
        return mapToDetailResponse(booking);
    }

    // ==========================================
    // Helper Methods & Mappings
    // ==========================================

    private record CabinDemand(String key, CabinType type, int passengers, UUID logicalCategoryId, Set<UUID> cabinIds) {}
    private record CabinResource(UUID id, CabinType type, int capacity, boolean available) {}
    private boolean categoryHeld(BookingPassenger passenger) {
        return passenger.getFareOfferId()!=null&&passenger.getCabinSelectionMode()==com.casms.booking.enums.CabinSelectionMode.CATEGORY_HOLD;
    }

    private BookingDetailResponse assignFareCabinGroup(Booking booking,BookingPassenger selected,Cruise cruise,
            CabinChangeRequest request,CasmsPrincipal principal) {
        if(cruise.getStatus()==CruiseStatus.COMPLETED||cruise.getStatus()==CruiseStatus.CANCELLED)
            throw new BusinessException("CRUISE_NOT_ASSIGNABLE","Không thay đổi phân cabin của chuyến đã hoàn tất hoặc đã hủy.");
        if(!fareCabinActorAllowed(booking,principal))throw new BusinessException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ chủ đặt chỗ hoặc nhân viên có quyền phân cabin được thực hiện thao tác này.");
        if(booking.getBookingStatus()==BookingStatus.Draft&&(booking.getHoldExpiresAt()==null||!booking.getHoldExpiresAt().isAfter(Instant.now())))
            throw new BusinessException(HttpStatus.CONFLICT,"BOOKING_HOLD_EXPIRED","Giữ chỗ đã hết hạn; không thể chuyển sang cabin vật lý.");
        var group=bookingPassengerRepository.findByBookingBookingId(booking.getBookingId()).stream()
                .filter(p->p.getParticipationStatus()==BookingPassengerParticipationStatus.Active&&p.getFareOfferId()!=null
                        &&Objects.equals(p.getCabinGroupKey(),selected.getCabinGroupKey()))
                .sorted(Comparator.comparing(BookingPassenger::getBookingPassengerId)).toList();
        if(group.isEmpty()||selected.getParticipationStatus()!=BookingPassengerParticipationStatus.Active)
            throw new BusinessException("CABIN_GROUP_INVALID","Nhóm cabin không còn hành khách đang tham gia hợp lệ.");
        if(group.size()>1&&!Boolean.TRUE.equals(request.getAssignWholeGroup()))
            throw new BusinessException(HttpStatus.CONFLICT,"CABIN_GROUP_CONFIRMATION_REQUIRED","Cần xác nhận phân cabin cho toàn bộ nhóm; không tự chia nhóm.",Map.of("groupPassengerCount",group.size()));
        var cabin=cabinRepository.findByIdForUpdate(request.getNewCabinId()).orElseThrow(()->new ResourceNotFoundException("Cabin",request.getNewCabinId()));
        if(!cabin.getShip().getShipId().equals(cruise.getShip().getShipId()))throw new BusinessException("CABIN_SHIP_MISMATCH","Cabin không thuộc tàu của chuyến khởi hành.");
        if(cabin.getStatus()!=CabinStatus.AVAILABLE)throw new BusinessException("CABIN_NOT_AVAILABLE","Cabin không còn khả dụng.");
        for(var passenger:group)if(!cabinMappings.requirePurchasedCabins(farePurchases.intent(passenger)).contains(cabin.getCabinId()))
            throw new BusinessException("FARE_CABIN_CATEGORY_MISMATCH","Cabin không phù hợp với hạng phòng đã mua; không tự nâng hoặc đổi hạng.");
        requireCabinOwnership(cabin.getCabinId(),cruise.getCruiseId(),booking.getBookingId(),true);
        Set<UUID> fulfilled=group.stream().map(BookingPassenger::getBookingPassengerId).collect(Collectors.toSet());
        long alreadyInTarget=group.stream().filter(p->cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(p.getBookingPassengerId())
                .map(a->a.getCabin().getCabinId().equals(cabin.getCabinId())).orElse(false)).count();
        long other=cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabin.getCabinId(),cruise.getCruiseId())-alreadyInTarget;
        if(other+group.size()>cabin.getCapacity())throw new BusinessException("CABIN_OVERCAPACITY","Cabin không đủ sức chứa cho toàn bộ nhóm hành khách.");
        assertCategoryAllocation(cruise.getCruiseId(),cruise.getShip().getShipId(),List.of(),Set.of(cabin.getCabinId()),fulfilled);
        var actor=getUser(principal.getUserId()); Instant now=Instant.now();
        for(var passenger:group) {
            var previous=cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(passenger.getBookingPassengerId());
            if(previous.isPresent()&&previous.get().getCabin().getCabinId().equals(cabin.getCabinId()))continue;
            previous.ifPresent(a->{a.setValidTo(now);a.setChangeReason(request.getChangeReason());cabinAssignmentRepository.save(a);});
            var assignment=new CabinAssignment(); assignment.setBookingPassenger(passenger); assignment.setCabin(cabin);
            assignment.setValidFrom(now);assignment.setAssignedByUser(actor);
            assignment.setChangeReason(request.getChangeReason()==null?"Chọn cabin theo gói vé đã chọn":request.getChangeReason());
            cabinAssignmentRepository.save(assignment);
        }
        cabinAssignmentRepository.flush(); // Assignment must succeed before the category resource is released.
        for(var passenger:group)passenger.setCabinSelectionMode(com.casms.booking.enums.CabinSelectionMode.PHYSICAL_CABIN);
        bookingPassengerRepository.saveAll(group);
        return mapToDetailResponse(booking);
    }
    private boolean fareCabinActorAllowed(Booking booking,CasmsPrincipal principal) {
        if(principal==null)return false;
        boolean owner=principal.hasRole(com.casms.identity.enums.Role.PASSENGER)&&principal.hasAuthority("BOOKING_READ")
                &&booking.getBookedByUser().getUserId().equals(principal.getUserId());
        boolean staff=principal.hasRole(com.casms.identity.enums.Role.BOOKING_AGENT)
                &&(principal.hasAuthority("BOOKING_CABIN_ASSIGN")||principal.hasAuthority("BOOKING_UPDATE"));
        return owner||staff;
    }
    private boolean canSelectFareCabin(Booking booking,BookingPassenger passenger) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth!=null&&auth.getPrincipal() instanceof CasmsPrincipal principal&&fareCabinActorAllowed(booking,principal)
                &&passenger.getParticipationStatus()==BookingPassengerParticipationStatus.Active
                &&booking.getBookingStatus()!=BookingStatus.Cancelled
                &&booking.getCruise().getStatus()!=CruiseStatus.COMPLETED&&booking.getCruise().getStatus()!=CruiseStatus.CANCELLED
                &&(booking.getBookingStatus()!=BookingStatus.Draft||booking.getHoldExpiresAt()!=null&&booking.getHoldExpiresAt().isAfter(Instant.now()));
    }

    private String serializeFareSelection(com.casms.cruisepackage.dto.CruisePackagePurchaseSelection selection) {
        try { return objectMapper.writeValueAsString(selection); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("FARE_QUOTE_PAYLOAD_INVALID","Không thể ghi nhận nội dung gói vé từ báo giá."); }
    }
    private void requireCabinOwnership(UUID cabinId,UUID cruiseId,UUID ownBookingId,boolean exclusive) {
        for(var row:bookingJdbc.queryForList("""
                SELECT b.booking_id,p.fare_offer_id FROM "CabinAssignment" a JOIN "BookingPassenger" p USING(booking_passenger_id)
                JOIN "Booking" b USING(booking_id) WHERE a.cabin_id=? AND b.cruise_id=? AND a.valid_to IS NULL
                  AND p.participation_status='Active' AND b.booking_status<>'Cancelled'
                """,cabinId,cruiseId)) {
            if(ownBookingId!=null&&ownBookingId.equals(row.get("booking_id")))continue;
            if(exclusive||row.get("fare_offer_id")!=null)throw new BusinessException(HttpStatus.CONFLICT,"CABIN_RESERVED_FOR_OTHER_BOOKING",
                    "Cabin đã có phân bổ của đặt chỗ khác; không được tính hoặc phân bổ lại.",Map.of("cabinId",cabinId));
        }
    }

    private com.casms.booking.enums.CabinSelectionMode selectionMode(com.casms.booking.dto.request.PassengerCabinSelection item) {
        return item.getCabinSelectionMode()==null?com.casms.booking.enums.CabinSelectionMode.PHYSICAL_CABIN:item.getCabinSelectionMode();
    }
    private String cabinGroup(com.casms.booking.dto.request.PassengerCabinSelection item) {
        if(item.getCabinGroupKey()!=null&&!item.getCabinGroupKey().isBlank())return item.getCabinGroupKey().trim();
        return selectionMode(item)==com.casms.booking.enums.CabinSelectionMode.CATEGORY_HOLD?"BOOKING_GROUP":"CABIN:"+item.getCabinId();
    }
    private boolean validateSelectionShape(List<? extends com.casms.booking.dto.request.PassengerCabinSelection> items) {
        if(items.stream().anyMatch(Objects::isNull))throw new BusinessException("PASSENGER_REQUIRED","Thông tin hành khách không được để trống.");
        boolean fare=items.stream().anyMatch(p->p.getFarePackageOfferId()!=null||p.getFarePackagePriceRevisionId()!=null);
        for(var item:items) {
            boolean category=selectionMode(item)==com.casms.booking.enums.CabinSelectionMode.CATEGORY_HOLD;
            if(!category&&item.getCabinId()==null)throw new BusinessException(HttpStatus.BAD_REQUEST,"CABIN_ID_REQUIRED","Chế độ chọn cabin vật lý phải có cabinId.");
            if(category&&item.getCabinId()!=null)throw new BusinessException("CABIN_RESOURCE_CONFLICT","Giữ chỗ theo hạng không được đồng thời chọn một cabin vật lý.");
            if((fare||category)&&(item.getFarePackageOfferId()==null||item.getFarePackagePriceRevisionId()==null))
                throw new BusinessException("FARE_SELECTION_REQUIRED","Mỗi hành khách trong đặt chỗ gói vé phải chọn đúng Offer và phiên bản giá.");
            if(fare&&item.getTourPackageId()!=null)throw new BusinessException("FARE_LEGACY_PACKAGE_MIX_UNSUPPORTED","Không thay thế quyền lợi gói vé bằng TourPackage cũ; dịch vụ mua thêm cần đi qua luồng mua thêm đã được duyệt.");
        }
        return fare;
    }
    private Map<com.casms.booking.dto.request.PassengerCabinSelection,com.casms.cruisepackage.dto.CruisePackagePurchaseSelection> prepareFares(
            UUID cruiseId,List<? extends com.casms.booking.dto.request.PassengerCabinSelection> items) {
        var result=new IdentityHashMap<com.casms.booking.dto.request.PassengerCabinSelection,com.casms.cruisepackage.dto.CruisePackagePurchaseSelection>();
        boolean fare=validateSelectionShape(items);
        if(!fare&&farePackages.requiresFareSelection(cruiseId))throw new BusinessException("FARE_SELECTION_REQUIRED","Chuyến khởi hành này yêu cầu mỗi hành khách chọn gói vé đang mở bán.");
        if(fare)for(var item:items)result.put(item,farePackages.preparePurchase(cruiseId,item.getFarePackageOfferId(),item.getFarePackagePriceRevisionId()));
        Map<String,String> resourceByGroup=new HashMap<>();
        for(var item:items)if(result.containsKey(item)) {
            var selection=result.get(item);
            var mapped=cabinMappings.requirePurchasedCabins(selection);
            if(item.getCabinId()!=null&&!mapped.contains(item.getCabinId()))throw new BusinessException("CABIN_MAPPING_CABIN_MISMATCH","Cabin được chọn không nằm trong mapping xác nhận của hạng thương mại đã mua.");
            String resource=selectionMode(item)+":"+(item.getCabinId()!=null?item.getCabinId():selection.logicalCategoryId());
            String previous=resourceByGroup.putIfAbsent(cabinGroup(item),resource);
            if(previous!=null&&!previous.equals(resource))throw new BusinessException("CABIN_GROUP_RESOURCE_CONFLICT","Một nhóm phải giữ nguyên trong một loại tài nguyên/cabin; không tự chia nhóm hoặc giữ đồng thời hai chế độ.");
        }
        return result;
    }
    private List<CabinDemand> categoryDemand(List<? extends com.casms.booking.dto.request.PassengerCabinSelection> items,
            Map<com.casms.booking.dto.request.PassengerCabinSelection,com.casms.cruisepackage.dto.CruisePackagePurchaseSelection> fares) {
        Map<String,CabinDemand> groups=new LinkedHashMap<>();
        for(var item:items)if(selectionMode(item)==com.casms.booking.enums.CabinSelectionMode.CATEGORY_HOLD) {
            String key=cabinGroup(item); var fare=fares.get(item); var previous=groups.get(key);
            if(fare==null)throw new BusinessException("FARE_SELECTION_REQUIRED","Giữ chỗ theo hạng cần gói vé hợp lệ.");
            if(previous!=null&&!Objects.equals(previous.logicalCategoryId(),fare.logicalCategoryId()))throw new BusinessException("CATEGORY_GROUP_CATEGORY_MISMATCH","Một nhóm giữ chỗ phải có cùng ID hạng thương mại; không tự chia nhóm.");
            groups.put(key,new CabinDemand("NEW:"+key,fare.cabinType(),previous==null?1:previous.passengers()+1,fare.logicalCategoryId(),cabinMappings.requirePurchasedCabins(fare)));
        }
        return List.copyOf(groups.values());
    }
    private String selectionKey(com.casms.booking.dto.request.PassengerCabinSelection item) {
        if(item.getFarePackageOfferId()==null)return item.getCabinId()+":"+(item.getTourPackageId()==null?"NONE":item.getTourPackageId());
        return "FARE:"+item.getFarePackageOfferId()+":"+item.getFarePackagePriceRevisionId()+":"+selectionMode(item)+":"+cabinGroup(item)+":"+item.getCabinId();
    }
    private String selectionKey(QuoteLineItem item) {
        if(item.getFareSelection()==null)return item.getCabinId()+":"+(item.getTourPackageId()==null?"NONE":item.getTourPackageId());
        return "FARE:"+item.getFareSelection().offerId()+":"+item.getFareSelection().priceRevisionId()+":"+item.getCabinSelectionMode()+":"+item.getCabinGroupKey()+":"+item.getCabinId();
    }

    /** Existing inventory, serialized in stable Cabin ID order. No witness is persisted as an assignment. */
    private List<CabinResource> cabinResources(UUID shipId, boolean lock) {
        // Same Ship lock as model import: Cruise -> Ship -> sorted Cabins. Snapshot Ship FKs cannot invert this order.
        if(lock)bookingJdbc.queryForObject("SELECT ship_id FROM \"Ship\" WHERE ship_id=? FOR UPDATE",UUID.class,shipId);
        return bookingJdbc.query("SELECT cabin_id,cabin_type,capacity,status FROM \"Cabin\" WHERE ship_id=? ORDER BY cabin_id"+(lock?" FOR UPDATE":""),
                (row,index)->new CabinResource(row.getObject("cabin_id",UUID.class),
                        row.getString("cabin_type")==null?null:CabinType.valueOf(row.getString("cabin_type")),
                        row.getInt("capacity"),"AVAILABLE".equals(row.getString("status"))),shipId);
    }

    /** Caller holds Cruise, then all Ship Cabin rows before any individual assignment write. */
    private Map<CabinType,Long> assertCategoryAllocation(UUID cruiseId, UUID shipId, List<CabinDemand> additional,
            Set<UUID> proposedPhysicalCabins, Set<UUID> fulfilledPassengers) {
        var groups=new LinkedHashMap<String,CabinDemand>();
        var held=bookingJdbc.queryForList("""
                SELECT p.booking_passenger_id,p.booking_id,p.cabin_group_key,p.fare_cabin_category,b.hold_expires_at,b.booking_status,
                  EXISTS(SELECT 1 FROM "CabinAssignment" a WHERE a.booking_passenger_id=p.booking_passenger_id AND a.valid_to IS NULL) AS has_assignment
                FROM "BookingPassenger" p JOIN "Booking" b USING(booking_id)
                WHERE b.cruise_id=? AND p.participation_status='Active' AND p.cabin_selection_mode='CATEGORY_HOLD'
                  AND (b.booking_status='Confirmed' OR (b.booking_status='Draft' AND (b.hold_expires_at IS NULL OR b.hold_expires_at>now())))
                ORDER BY p.booking_id,p.cabin_group_key,p.booking_passenger_id
                """,cruiseId);
        for(var row:held) {
            UUID passengerId=(UUID)row.get("booking_passenger_id");
            if(fulfilledPassengers.contains(passengerId))continue;
            if(Boolean.TRUE.equals(row.get("has_assignment")))throw new BusinessException("CABIN_RESOURCE_CONFLICT","Một nhu cầu không được đồng thời giữ hạng phòng và có phân bổ cabin vật lý.");
            if(row.get("cabin_group_key")==null||row.get("fare_cabin_category")==null
                    ||("Draft".equals(row.get("booking_status"))&&row.get("hold_expires_at")==null))
                throw new BusinessException(HttpStatus.CONFLICT,"CATEGORY_HOLD_INVALID","Giữ chỗ theo hạng phòng thiếu dữ liệu hoặc thời hạn hợp lệ.");
            String key=row.get("booking_id")+":"+row.get("cabin_group_key");
            CabinType type;
            try { type=CabinType.valueOf((String)row.get("fare_cabin_category")); }
            catch(IllegalArgumentException e) { throw new BusinessException("CATEGORY_HOLD_INVALID","Hạng phòng đang giữ chưa xác định được trong dữ liệu tàu."); }
            var previous=groups.get(key);
            var passenger=bookingPassengerRepository.findById(passengerId).orElseThrow();
            var selection=farePurchases.intent(passenger);var mapped=cabinMappings.requirePurchasedCabins(selection);
            if(previous!=null&&!Objects.equals(previous.logicalCategoryId(),selection.logicalCategoryId()))throw new BusinessException("CATEGORY_GROUP_CATEGORY_MISMATCH","Một nhóm giữ chỗ không được tự chia sang nhiều hạng thương mại.");
            groups.put(key,new CabinDemand(key,type,previous==null?1:previous.passengers()+1,selection.logicalCategoryId(),mapped));
        }
        List<CabinDemand> demands=new ArrayList<>(groups.values()); demands.addAll(additional);
        if(demands.isEmpty())return Map.of();
        Set<UUID> occupied=new HashSet<>(proposedPhysicalCabins);
        for(var row:bookingJdbc.queryForList("""
                SELECT a.cabin_id,p.booking_passenger_id FROM "CabinAssignment" a
                JOIN "BookingPassenger" p USING(booking_passenger_id) JOIN "Booking" b USING(booking_id)
                WHERE b.cruise_id=? AND b.booking_status<>'Cancelled' AND p.participation_status='Active' AND a.valid_to IS NULL
                """,cruiseId)) {
            if(!fulfilledPassengers.contains((UUID)row.get("booking_passenger_id")))occupied.add((UUID)row.get("cabin_id"));
        }
        var remaining=new ArrayList<>(cabinResources(shipId,false).stream()
                .filter(c->c.available()&&c.type()!=null&&c.capacity()>0&&!occupied.contains(c.id()))
                .sorted(Comparator.comparingInt(CabinResource::capacity).thenComparing(CabinResource::id)).toList());
        // Exact matching for one-category, whole-cabin groups with capacity as the only fit dimension.
        demands.sort(Comparator.comparingInt(CabinDemand::passengers).reversed().thenComparing(CabinDemand::key));
        Map<CabinType,Long> reservedCapacity=new EnumMap<>(CabinType.class);
        for(var demand:demands) {
            var fit=remaining.stream().filter(c->demand.cabinIds().contains(c.id())&&c.capacity()>=demand.passengers()).findFirst().orElse(null);
            if(fit==null)throw new BusinessException(HttpStatus.CONFLICT,"CATEGORY_HOLD_ALLOCATION_BLOCKED",
                    "Chưa chứng minh được phương án phân cabin nguyên nhóm cho các giữ chỗ còn hiệu lực.",
                    Map.of("cabinCategory",demand.type().name(),"requiredGroupCapacity",demand.passengers(),
                            "blockingDependency","REAL_CABIN_ALLOCATION","automaticGroupSplit",false));
            remaining.remove(fit);
            reservedCapacity.merge(fit.type(),(long)fit.capacity(),Long::sum);
        }
        return Map.copyOf(reservedCapacity);
    }

    /** Existing inventory projection for Fare-managed sailings; proof is recalculated, never assigned to passengers. */
    @Transactional(readOnly=true)
    public boolean hasMappedCabinInventory(UUID cruiseId,Set<UUID> mappedIds) {
        var cruise=getCruise(cruiseId);if(cruise.getShip()==null)return false;
        for(var cabin:cabinResources(cruise.getShip().getShipId(),false)) {
            if(!mappedIds.contains(cabin.id())||!cabin.available()||cabin.capacity()<1
                    ||cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabin.id(),cruiseId)>0)continue;
            try {
                // A further group can use this cabin only if all existing category holds still fit elsewhere.
                assertCategoryAllocation(cruiseId,cruise.getShip().getShipId(),List.of(),Set.of(cabin.id()),Set.of());
                return true;
            } catch(BusinessException blocked) {
                if(!Set.of("CATEGORY_HOLD_ALLOCATION_BLOCKED","CATEGORY_HOLD_INVALID","CABIN_MAPPING_STALE","CABIN_MAPPING_PURCHASE_EVIDENCE_REQUIRED").contains(blocked.getCode()))throw blocked;
            }
        }
        return false;
    }

    /** Existing inventory projection for Fare-managed sailings; proof is recalculated, never assigned to passengers. */
    @Transactional(readOnly=true)
    public Map<CabinType,Long> fareCabinHeadroom(UUID cruiseId,UUID shipId) {
        var reserved=assertCategoryAllocation(cruiseId,shipId,List.of(),Set.of(),Set.of());
        var occupied=bookingJdbc.queryForList("""
                SELECT DISTINCT a.cabin_id FROM "CabinAssignment" a JOIN "BookingPassenger" p USING(booking_passenger_id)
                JOIN "Booking" b USING(booking_id) WHERE b.cruise_id=? AND b.booking_status<>'Cancelled'
                  AND p.participation_status='Active' AND a.valid_to IS NULL
                """,UUID.class,cruiseId);
        Map<CabinType,Long> headroom=new EnumMap<>(CabinType.class);
        var mapped=farePackages.saleScopeCabinIds(cruiseId);
        for(var cabin:cabinResources(shipId,false))if(mapped.contains(cabin.id())&&cabin.available()&&cabin.type()!=null&&cabin.capacity()>0&&!occupied.contains(cabin.id()))
            headroom.merge(cabin.type(),(long)cabin.capacity(),Long::sum);
        for(var type:CabinType.values())headroom.put(type,Math.max(0L,headroom.getOrDefault(type,0L)-reserved.getOrDefault(type,0L)));
        return Map.copyOf(headroom);
    }

    private BookingDetailResponse mapToDetailResponse(Booking booking) {
        BookingDetailResponse res = new BookingDetailResponse();
        res.setBookingId(booking.getBookingId());
        res.setBookingReference(booking.getBookingReference());
        res.setBookingStatus(booking.getBookingStatus());

        Cruise cruise = booking.getCruise();
        res.setCruiseId(cruise.getCruiseId());
        res.setCruiseCode(cruise.getCruiseCode());
        res.setCruiseName(cruise.getCruiseName());
        res.setShipName(cruise.getShip() != null ? cruise.getShip().getShipName() : null);
        res.setDepartureDatetime(cruise.getDepartureDatetime());
        res.setReturnDatetime(cruise.getReturnDatetime());

        res.setAgreedFareAmount(booking.getAgreedFareAmount());
        res.setCurrency(booking.getCurrency());
        res.setRequiredDepositAmount(booking.getRequiredDepositAmount());
        res.setFinalPaymentDueAt(booking.getFinalPaymentDueAt());

        res.setBookedByUserId(booking.getBookedByUser().getUserId());
        res.setBookedByUsername(booking.getBookedByUser().getUsername());
        res.setCreatedAt(booking.getCreatedAt());
        res.setConfirmedAt(booking.getConfirmedAt());
        res.setCancelledAt(booking.getCancelledAt());
        res.setCancellationReason(booking.getCancellationReason());

        bookingContractRepository.findByBookingBookingId(booking.getBookingId()).ifPresent(c -> {
            res.setContractReference(c.getContractReference());
            res.setContractStatus(c.getStatus());
            res.setContractDocumentReference(c.getDocumentReference());
            res.setContractAcceptedAt(c.getAcceptedAt());
        });

        List<BookingPassenger> bookingPassengers = bookingPassengerRepository.findByBookingBookingId(booking.getBookingId());
        List<BookingPassengerPackage> packages = bookingPassengerPackageRepository.findByBookingPassengerBookingBookingId(booking.getBookingId());
        Map<UUID, BookingPassengerPackage> packageMap = packages.stream()
                .collect(Collectors.toMap(BookingPassengerPackage::getBookingPassengerId, p -> p, (p1, p2) -> p1));

        List<BookingPassengerResponse> passengerResponses = new ArrayList<>();
        for (BookingPassenger bp : bookingPassengers) {
            BookingPassengerResponse pRes = new BookingPassengerResponse();
            pRes.setBookingPassengerId(bp.getBookingPassengerId());

            Passenger p = bp.getPassenger();
            if (p != null) {
                pRes.setPassengerId(p.getPassengerId());
                pRes.setFullName(p.getFullName());
                pRes.setDateOfBirth(p.getDateOfBirth());
                pRes.setNationality(p.getNationality());
                pRes.setDocumentType(p.getDocumentType());
                pRes.setDocumentNumber(p.getDocumentNumberProtected());
                pRes.setEmail(p.getEmail());
                pRes.setPhoneNumber(p.getPhoneNumber());
                UUID currentUserId = null;
                var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal) {
                    currentUserId = principal.getUserId();
                }
                boolean owned = (p.getUser() != null && currentUserId != null && p.getUser().getUserId().equals(currentUserId))
                        || (p.getUser() != null && currentUserId == null && booking.getBookedByUser() != null
                        && p.getUser().getUserId().equals(booking.getBookedByUser().getUserId()));
                pRes.setOwnedByCurrentUser(owned);
            } else {
                pRes.setOwnedByCurrentUser(false);
            }

            pRes.setParticipationStatus(bp.getParticipationStatus());
            pRes.setRequestedCabinCategory(bp.getRequestedCabinCategory());
            pRes.setCabinSelectionMode(bp.getCabinSelectionMode());pRes.setCabinGroupKey(bp.getCabinGroupKey());
            pRes.setFarePackageOfferId(bp.getFareOfferId());pRes.setFareCabinCategory(bp.getFareCabinCategory());
            if(bp.getFareOfferId()!=null) {
                pRes.setPurchasedFare(farePurchases.purchased(bp.getBookingPassengerId()));
                pRes.setSelectedFare(farePurchases.intent(bp));pRes.setCanSelectFareCabin(canSelectFareCabin(booking,bp));
            }

            // Thông tin phòng gán hiện hành
            cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(bp.getBookingPassengerId())
                    .ifPresent(ca -> {
                        Cabin c = ca.getCabin();
                        pRes.setCabinId(c.getCabinId());
                        pRes.setCabinNumber(c.getCabinNumber());
                        pRes.setCabinType(c.getCabinType());
                        if (c.getDeck() != null) {
                            pRes.setDeckNumber(c.getDeck().getDeckNumber());
                        }
                    });

            // Thông tin gói dịch vụ
            BookingPassengerPackage bpp = packageMap.get(bp.getBookingPassengerId());
            if (bpp != null && bpp.getTourPackage() != null) {
                TourPackage tp = bpp.getTourPackage();
                pRes.setTourPackageId(tp.getTourPackageId());
                pRes.setTourPackageName(tp.getPackageName());
                pRes.setTourPackageType(tp.getPackageType());
                pRes.setAgreedPackagePrice(bpp.getAgreedPrice());
            }

            passengerResponses.add(pRes);
        }
        res.setPassengers(passengerResponses);

        // Lộ trình các đợt thanh toán (Installments)
        List<Installment> installments = installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(booking.getBookingId());
        List<InstallmentResponse> installmentResponses = new ArrayList<>();
        for (Installment inst : installments) {
            InstallmentResponse iRes = new InstallmentResponse();
            iRes.setInstallmentId(inst.getInstallmentId());
            iRes.setSequenceNo(inst.getSequenceNo());
            iRes.setInstallmentType(inst.getInstallmentType());
            iRes.setRequiredAmount(inst.getRequiredAmount());
            iRes.setCurrency(inst.getCurrency());
            iRes.setDueAt(inst.getDueAt());
            iRes.setStatus(inst.getStatus());

            iRes.setDescription("Đợt " + inst.getSequenceNo() + ": "
                    + (inst.getInstallmentType() == InstallmentType.FinalFare ? "Thanh toán tiền vé" : "Đặt cọc"));
            installmentResponses.add(iRes);
        }
        res.setInstallments(installmentResponses);

        if (installments.size() == 1) {
            res.setPaymentPlan(PaymentPlan.FULL);
        } else if (installments.size() == 2) {
            res.setPaymentPlan(PaymentPlan.DEPOSIT);
        } else if (installments.size() == 3) {
            res.setPaymentPlan(PaymentPlan.STAGES);
        }

        String contractRef = res.getContractReference() != null ? res.getContractReference() : "CTR-" + booking.getBookingReference();
        res.setContractSigningInstructions(
                "Vì lý do bảo mật thông tin cá nhân, hợp đồng được ký bản cứng theo quy trình hiện hành. " +
                "Vui lòng sử dụng mã đối chiếu " + contractRef + " và liên hệ OceVie để xác nhận địa điểm, thời hạn ký và hướng dẫn thanh toán."
        );

        return res;
    }

    private void createInstallmentsForPlan(Booking booking, PaymentPlan plan, BigDecimal agreedFare,
                                          BigDecimal depositAmount, Instant finalDue, Instant departure,
                                          Instant scheduleCreatedAt) {
        // No approved configuration exists for initial-installment deadlines or staged ratios.
        // Keep historical installments intact; do not manufacture new payment obligations.
        throw missingPaymentSchedulePolicy();
    }

    void validatePaymentSchedule(PaymentPlan plan, Instant createdAt, Instant finalDue, Instant departure) {
        throw missingPaymentSchedulePolicy();
    }

    private BusinessException missingPaymentSchedulePolicy() {
        return new BusinessException(HttpStatus.CONFLICT, "BUSINESS_POLICY_REQUIRED",
                "Chưa có chính sách lịch thanh toán hợp lệ; không tự tạo hạn hoặc tỷ lệ các đợt thanh toán.",
                Map.of("scope", "REQUEST", "policyTypes", List.of("PAYMENT_SCHEDULE"),
                        "allowedActions", List.of("CONFIGURE_BUSINESS_POLICY"), "evaluatedAt", Instant.now()));
    }

    private boolean paymentHasSettlementEvidence(Payment payment) {
        if (payment.getPaymentMethod() == null) return false;
        return switch (payment.getPaymentMethod()) {
            case Cash -> payment.getInternalReceiptReference() != null && !payment.getInternalReceiptReference().isBlank();
            case GatewaySandbox, Sepay -> payment.getProcessorReference() != null && !payment.getProcessorReference().isBlank();
        };
    }

    private boolean paymentBelongsToBooking(Payment payment, UUID bookingId) {
        return (payment.getBooking() != null && bookingId.equals(payment.getBooking().getBookingId()))
                || (payment.getInstallment() != null
                    && payment.getInstallment().getBooking() != null
                    && bookingId.equals(payment.getInstallment().getBooking().getBookingId()));
    }

    private void createContractSigningNotification(Booking booking, BookingContract contract, BookingPassenger representative, PaymentPlan plan, Cruise cruise) {
        String repName = (representative != null && representative.getPassenger() != null)
                ? representative.getPassenger().getFullName()
                : "Quý khách";

        Notification notif = new Notification();
        notif.setRecipientUser(booking.getBookedByUser());
        notif.setBookingPassengerId(representative != null ? representative.getBookingPassengerId() : null);
        notif.setNotificationType("CONTRACT_SIGNING_INVITATION");
        notif.setTitle("Thư mời ký hợp đồng & Xác nhận đặt chỗ chuyến khởi hành " + booking.getBookingReference());
        notif.setContent(String.format(
                "Kính gửi %s,\n\n" +
                "Đơn đặt chỗ mã [%s] cho chuyến du thuyền '%s' đã được tạo thành công.\n" +
                "- Mã hợp đồng đối chiếu: %s\n" +
                "- Hình thức thanh toán đã chọn: %s\n" +
                "- Tổng giá trị: %s %s (Cọc tối thiểu: %s %s)\n\n" +
                "**LƯU Ý BẢO MẬT:** Để bảo mật tuyệt đối thông tin định danh cá nhân của quý khách, hợp đồng pháp lý KHÔNG lưu trữ văn bản thô trên hệ thống trực tuyến.\n" +
                "Vui lòng liên hệ OceVie để xác nhận địa điểm, thời hạn ký hợp đồng bản cứng và hướng dẫn thanh toán theo hình thức đã chọn.\n\n" +
                "Trân trọng cảm ơn quý khách đã đồng hành cùng CASMS!",
                repName,
                booking.getBookingReference(),
                cruise.getCruiseName(),
                contract.getContractReference(),
                plan,
                booking.getAgreedFareAmount(), booking.getCurrency(),
                booking.getRequiredDepositAmount(), booking.getCurrency()
        ));
        notificationRepository.save(notif);
    }

    private BookingSummaryResponse mapToSummaryResponse(Booking booking) {
        BookingSummaryResponse res = new BookingSummaryResponse();
        res.setBookingId(booking.getBookingId());
        res.setBookingReference(booking.getBookingReference());
        res.setCruiseId(booking.getCruise().getCruiseId());
        res.setCruiseCode(booking.getCruise().getCruiseCode());
        res.setCruiseName(booking.getCruise().getCruiseName());
        res.setBookingStatus(booking.getBookingStatus());
        res.setAgreedFareAmount(booking.getAgreedFareAmount());
        res.setCurrency(booking.getCurrency());
        res.setDepartureDatetime(booking.getCruise().getDepartureDatetime());
        res.setCreatedAt(booking.getCreatedAt());

        long count = bookingPassengerRepository.findByBookingBookingId(booking.getBookingId()).size();
        res.setPassengerCount((int) count);
        return res;
    }

    public BigDecimal getBaseCabinFare(CabinType cabinType) {
        return null;
    }

    public BigDecimal getBaseCabinFareForCruise(UUID cruiseId, CabinType cabinType) {
        if (cruiseId != null) {
            Optional<CruisePricingPolicy> policyOpt = cruisePricingPolicyRepository.findByCruise_CruiseId(cruiseId)
                    .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED);
            if (policyOpt.isPresent()) {
                return policyOpt.get().getBaseFareForCabinType(cabinType);
            }
        }
        return null;
    }

    @Transactional
    public BookingQuoteResponse createQuote(BookingQuoteRequest request, CasmsPrincipal principal) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_REQUIRED", "Yêu cầu báo giá không được để trống");
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank())
            com.casms.common.util.NewWriteCurrency.requireUsd(request.getCurrency().trim().toUpperCase(java.util.Locale.ROOT));
        if (request.getCruiseId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CRUISE_ID_REQUIRED", "Cruise ID không được để trống");
        }
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSENGERS_REQUIRED", "Danh sách hành khách không được để trống");
        }
        boolean fareQuote=validateSelectionShape(request.getPassengers());

        Cruise cruise = cruiseRepository.findByIdForUpdate(request.getCruiseId())
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", request.getCruiseId()));

        mainflow.requireBookable(cruise.getCruiseId());
        if (cruise.getOpenedForSaleAt() == null || cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CRUISE_STATUS",
                    "Chỉ có thể tạo báo giá cho chuyến khởi hành đã phát hành (PUBLISHED). Trạng thái hiện tại: " + cruise.getStatus());
        }
        if (cruise.getStoppedAt() != null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_SALE_STOPPED",
                    "Chuyến khởi hành đã bị dừng mở bán, không thể tạo báo giá.");
        }
        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_NOT_OPEN_FOR_SALE",
                        "Chuyến khởi hành chưa có lịch trình mở bán thương mại."));
        if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SALE_SUSPENDED",
                    "Chuyến khởi hành đang tạm dừng mở bán, không thể tạo báo giá.");
        }
        if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_NOT_OPEN_FOR_SALE",
                    "Chuyến khởi hành chưa mở bán thương mại (Trạng thái lịch trình: " + itinerary.getStatus() + ").");
        }
        if (cruise.getDepartureDatetime() != null && cruise.getDepartureDatetime().isBefore(Instant.now())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_ALREADY_DEPARTED",
                    "Chuyến khởi hành đã khởi hành, không thể tạo báo giá.");
        }
        if (cruise.getShip() == null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_NO_SHIP",
                    "Chuyến khởi hành chưa được gán tàu.");
        }
        cabinResources(cruise.getShip().getShipId(),true);
        var fareSelections=prepareFares(cruise.getCruiseId(),request.getPassengers());

        CruisePricingPolicy policy = cruisePricingPolicyRepository.findByCruise_CruiseId(cruise.getCruiseId())
                .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_MISSING",
                        "Chuyến khởi hành chưa có chính sách giá được phê duyệt. Không thể phát hành báo giá."));

        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            if (!request.getCurrency().trim().equalsIgnoreCase(policy.getCurrency())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH",
                        "Đơn vị tiền tệ yêu cầu (" + request.getCurrency() + ") không khớp với chính sách chuyến khởi hành (" + policy.getCurrency() + ").");
            }
        }
        String currency = policy.getCurrency();
        com.casms.common.util.NewWriteCurrency.requireUsd(currency);

        String saleScope = cruise.getSaleScope() != null ? cruise.getSaleScope().toUpperCase() : "WHOLE_CRUISE";
        if ("CABIN_ONLY".equals(saleScope)) {
            boolean hasPkg = request.getPassengers().stream().anyMatch(p -> p.getTourPackageId() != null);
            if (hasPkg) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PACKAGE_NOT_ALLOWED_IN_CABIN_ONLY_SCOPE",
                        "Chuyến khởi hành đang mở bán theo phạm vi CABIN_ONLY, không chấp nhận đính kèm gói tour/dịch vụ.");
            }
        }

        int totalNewPassengers = request.getPassengers().size();
        long currentBookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruise.getCruiseId());
        if (currentBookedPassengers + totalNewPassengers > cruise.getShip().getTotalCapacity()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "EXCEEDS_SHIP_CAPACITY",
                    String.format("Không đủ sức chứa trên tàu (Tổng: %d, Đã giữ/đặt: %d, Yêu cầu: %d)",
                            cruise.getShip().getTotalCapacity(), currentBookedPassengers, totalNewPassengers));
        }

        Map<UUID, List<PassengerQuoteItem>> cabinPassengersMap = request.getPassengers().stream()
                .filter(p->p.getCabinId()!=null)
                .collect(Collectors.groupingBy(PassengerQuoteItem::getCabinId));

        Map<UUID, Cabin> validatedCabins = new HashMap<>();
        for (Map.Entry<UUID, List<PassengerQuoteItem>> entry : cabinPassengersMap.entrySet()) {
            UUID cabinId = entry.getKey();
            List<PassengerQuoteItem> passengersInCabin = entry.getValue();

            Cabin cabin = cabinRepository.findById(cabinId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId));

            if (!cabin.getShip().getShipId().equals(cruise.getShip().getShipId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CABIN_SHIP_MISMATCH",
                        "Phòng " + cabin.getCabinNumber() + " không thuộc về tàu của chuyến khởi hành: " + cruise.getShip().getShipName());
            }

            if (cabin.getStatus() != CabinStatus.AVAILABLE) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CABIN_NOT_AVAILABLE",
                        "Phòng " + cabin.getCabinNumber() + " hiện không khả dụng (Trạng thái: " + cabin.getStatus() + ")");
            }

            long currentOccupied = cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabinId, cruise.getCruiseId());
            requireCabinOwnership(cabinId,cruise.getCruiseId(),null,fareQuote);
            for(var passenger:passengersInCabin)if(fareSelections.containsKey(passenger)&&!cabinMappings.requirePurchasedCabins(fareSelections.get(passenger)).contains(cabin.getCabinId()))
                throw new BusinessException("FARE_CABIN_CATEGORY_MISMATCH","Cabin vật lý không phù hợp với hạng phòng của gói vé đã chọn.");
            int remaining = cabin.getCapacity() - (int) currentOccupied;

            if (passengersInCabin.size() > remaining) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CABIN_OVERCAPACITY",
                        String.format("Phòng %s chỉ còn %d chỗ khả dụng, nhưng có %d khách được gán vào phòng này",
                                cabin.getCabinNumber(), remaining, passengersInCabin.size()));
            }

            validatedCabins.put(cabinId, cabin);
        }

        assertCategoryAllocation(cruise.getCruiseId(),cruise.getShip().getShipId(),categoryDemand(request.getPassengers(),fareSelections),cabinPassengersMap.keySet(),Set.of());
        Map<CabinType, Long> requestedPassengersByType = request.getPassengers().stream()
                .map(p -> fareSelections.containsKey(p)?fareSelections.get(p).cabinType():validatedCabins.get(p.getCabinId()).getCabinType())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        for (Map.Entry<CabinType, Long> entry : requestedPassengersByType.entrySet()) {
            CabinType type = entry.getKey();
            Long requestedSlots = entry.getValue();
            Long totalSlotsForType = cabinRepository.sumCapacityByShipIdAndCabinType(cruise.getShip().getShipId(), type);
            if (totalSlotsForType == null || totalSlotsForType == 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CABIN_TYPE_NOT_CONFIGURED",
                        "Hạng phòng " + type + " chưa có chỗ khả dụng trên tàu: " + cruise.getShip().getShipName());
            }
            long activeAssignmentsForType = cabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise(type, cruise.getCruiseId());
            long remainingSlotsForType = Math.max(0, totalSlotsForType - activeAssignmentsForType);
            if (requestedSlots > remainingSlotsForType) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CABIN_TYPE_EXHAUSTED",
                        String.format("Hạng phòng %s không còn đủ chỗ khả dụng (Còn lại: %d, Yêu cầu: %d). Hệ thống từ chối để chống oversell.",
                                type, remainingSlotsForType, requestedSlots));
            }
        }

        List<QuoteLineItem> lineItems = new ArrayList<>();
        BigDecimal subtotalCabin = BigDecimal.ZERO;
        BigDecimal subtotalPackage = BigDecimal.ZERO;
        BigDecimal subtotalFare=BigDecimal.ZERO;

        for (PassengerQuoteItem pReq : request.getPassengers()) {
            Cabin cabin = validatedCabins.get(pReq.getCabinId());
            var fare=fareSelections.get(pReq);
            if(fare!=null) {
                subtotalFare=subtotalFare.add(fare.amount());
                lineItems.add(QuoteLineItem.builder().cabinId(pReq.getCabinId()).cabinNumber(cabin==null?null:cabin.getCabinNumber())
                        .cabinType(fare.cabinType()).deckNumber(cabin==null||cabin.getDeck()==null?null:cabin.getDeck().getDeckNumber())
                        .fareSelection(fare).cabinSelectionMode(selectionMode(pReq)).cabinGroupKey(cabinGroup(pReq)).itemTotal(fare.amount()).build());
                continue;
            }
            BigDecimal baseFare = policy.getBaseFareForCabinType(cabin.getCabinType());
            if (baseFare == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CABIN_TYPE_UNPRICED",
                        "Hạng phòng " + cabin.getCabinType() + " chưa được định giá trong chính sách của chuyến khởi hành");
            }
            BigDecimal multiplier = (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                    ? cabin.getDeck().getPriceMultiplier()
                    : BigDecimal.valueOf(1.00);
            BigDecimal cabinPrice = baseFare.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            subtotalCabin = subtotalCabin.add(cabinPrice);

            UUID pkgId = null;
            String pkgName = null;
            BigDecimal pkgPrice = BigDecimal.ZERO;

            if (pReq.getTourPackageId() != null) {
                TourPackage pkg = tourPackageRepository.findById(pReq.getTourPackageId())
                        .orElseThrow(() -> new ResourceNotFoundException("TourPackage", pReq.getTourPackageId()));
                if (pkg.getStatus() != TourPackageStatus.ACTIVE) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_INACTIVE",
                            "Gói dịch vụ " + pkg.getPackageName() + " không ở trạng thái hoạt động");
                }
                boolean assigned = cruiseTourPackageRepository
                        .existsByCruise_CruiseIdAndTourPackage_TourPackageIdAndActiveTrue(cruise.getCruiseId(), pkg.getTourPackageId());
                if (!assigned) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PACKAGE_NOT_AVAILABLE_FOR_CRUISE",
                            "Gói dịch vụ " + pkg.getPackageName() + " chưa được mở bán cho chuyến khởi hành này");
                }
                pkgId = pkg.getTourPackageId();
                com.casms.common.util.NewWriteCurrency.requireUsd(pkg.getCurrencyCode());
                pkgName = pkg.getPackageName();
                pkgPrice = pkg.getPrice() != null ? pkg.getPrice() : BigDecimal.ZERO;
                subtotalPackage = subtotalPackage.add(pkgPrice);
            }

            BigDecimal itemTotal = cabinPrice.add(pkgPrice);
            lineItems.add(QuoteLineItem.builder()
                    .cabinId(cabin.getCabinId())
                    .cabinNumber(cabin.getCabinNumber())
                    .cabinType(cabin.getCabinType())
                    .deckNumber(cabin.getDeck() != null ? cabin.getDeck().getDeckNumber() : null)
                    .baseFare(baseFare)
                    .priceMultiplier(multiplier)
                    .cabinPrice(cabinPrice)
                    .tourPackageId(pkgId)
                    .packageName(pkgName)
                    .packagePrice(pkgPrice)
                    .itemTotal(itemTotal)
                    .build());
        }

        BigDecimal subtotalAmount = subtotalCabin.add(subtotalPackage).add(subtotalFare);
        BigDecimal portFeePerPassenger = policy.getPortFeePerPassenger() != null ? policy.getPortFeePerPassenger() : BigDecimal.ZERO;
        BigDecimal portFeesAmount = portFeePerPassenger.multiply(BigDecimal.valueOf(request.getPassengers().size()));
        BigDecimal taxRate = policy.getTaxRate() != null ? policy.getTaxRate() : BigDecimal.ZERO;
        BigDecimal taxesAmount = subtotalAmount.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal taxesAndFeesAmount = portFeesAmount.add(taxesAmount);
        BigDecimal totalAmount = subtotalAmount.add(taxesAndFeesAmount);

        // Payment plan and cutoff rules
        PaymentPlan plan = request.getPaymentPlan() != null ? request.getPaymentPlan() : PaymentPlan.DEPOSIT;
        Instant now = Instant.now();
        Instant departure = cruise.getDepartureDatetime();
        int cutoffDays = policy.getFullPaymentCutoffDays();
        if (departure != null && departure.isBefore(now.plus(Duration.ofDays(cutoffDays)))) {
            plan = PaymentPlan.FULL; // Within cutoff days, must pay in full
        }

        BigDecimal depositAmount;
        if (plan == PaymentPlan.FULL) {
            depositAmount = totalAmount;
        } else {
            BigDecimal depositRate = policy.getDepositPercentage();
            depositAmount = totalAmount.multiply(depositRate).setScale(4, RoundingMode.HALF_UP);
        }

        int dueDays = policy.getFinalPaymentDueDays();
        Instant finalPaymentDueAt = departure != null ? departure.minus(Duration.ofDays(dueDays)) : now;
        if (finalPaymentDueAt.isBefore(now)) {
            finalPaymentDueAt = now;
        }

        String fingerprint = generateQuoteFingerprint(cruise, policy, totalAmount);
        if(fareQuote) {
            try { fingerprint=com.casms.ship.service.CruiseMainflowService.hash(fingerprint+":"+objectMapper.writeValueAsString(lineItems)); }
            catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("FARE_QUOTE_PAYLOAD_INVALID","Không thể ghi nhận đầy đủ nội dung báo giá gói vé."); }
        }
        String quoteCode;
        try {
            quoteCode = businessCodeGenerator.next("QT");
        } catch (Exception ex) {
            quoteCode = "QT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        Instant expiresAt = now.plus(mainflow.requireTransactionDuration(cruise.getCruiseId(), "QUOTE_VALIDITY"));

        User bookedUser = null;
        String guestToken = null;
        if (principal != null && principal.getUserId() != null) {
            bookedUser = userRepository.findById(principal.getUserId()).orElse(null);
        } else {
            guestToken = UUID.randomUUID().toString();
        }

        String quotePayloadJson = null;
        try {
            quotePayloadJson = objectMapper.writeValueAsString(lineItems);
        } catch (Exception e) {
            log.warn("Failed to serialize quote line items: {}", e.getMessage());
        }

        BookingQuote quote = BookingQuote.builder()
                .quoteCode(quoteCode)
                .cruise(cruise)
                .user(bookedUser)
                .policyVersion(policy.getPolicyVersion())
                .currency(currency)
                .subtotalAmount(subtotalAmount)
                .taxesAndFeesAmount(taxesAndFeesAmount)
                .totalAmount(totalAmount)
                .depositAmount(depositAmount)
                .paymentPlan(plan)
                .finalPaymentDueAt(finalPaymentDueAt)
                .fingerprint(fingerprint)
                .quotePayloadJson(quotePayloadJson)
                .guestToken(guestToken)
                .status("ACTIVE")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        quote = bookingQuoteRepository.save(quote);

        return BookingQuoteResponse.builder()
                .quoteId(quote.getQuoteId())
                .quoteCode(quote.getQuoteCode())
                .cruiseId(cruise.getCruiseId())
                .cruiseCode(cruise.getCruiseCode())
                .departureDatetime(cruise.getDepartureDatetime())
                .policyVersion(policy.getPolicyVersion())
                .currency(currency)
                .lineItems(lineItems)
                .subtotalAmount(subtotalAmount)
                .portFeesAmount(portFeesAmount)
                .taxesAmount(taxesAmount)
                .taxesAndFeesAmount(taxesAndFeesAmount)
                .totalAmount(totalAmount)
                .paymentPlan(plan)
                .depositAmount(depositAmount)
                .remainingAmount(totalAmount.subtract(depositAmount))
                .finalPaymentDueAt(finalPaymentDueAt)
                .fingerprint(fingerprint)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .guestToken(guestToken)
                .build();
    }

    @Transactional(readOnly = true)
    public BookingQuoteResponse getQuote(UUID quoteId) {
        return getQuote(quoteId, null, null);
    }

    @Transactional(readOnly = true)
    public BookingQuoteResponse getQuote(UUID quoteId, CasmsPrincipal principal, String guestToken) {
        BookingQuote quote = bookingQuoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingQuote", quoteId));

        boolean isStaff = principal != null && principal.isStaff()
                && (principal.hasAuthority("BOOKING_READ") || principal.hasAuthority("ROLE_ADMIN"));

        if (quote.getUser() != null) {
            boolean isOwner = principal != null && quote.getUser().getUserId().equals(principal.getUserId());
            if (!isOwner && !isStaff) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_QUOTE_ACCESS",
                        "Bạn không có quyền truy cập vào báo giá của người dùng khác.");
            }
        } else {
            boolean tokenMatch = guestToken != null && !guestToken.isBlank() && guestToken.trim().equals(quote.getGuestToken());
            if (!tokenMatch && !isStaff) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_QUOTE_ACCESS",
                        "Khách vãng lai cần cung cấp X-Guest-Token hợp lệ để tra cứu chi tiết báo giá.");
            }
        }

        List<QuoteLineItem> lineItems = null;
        if (quote.getQuotePayloadJson() != null && !quote.getQuotePayloadJson().isBlank()) {
            try {
                lineItems = objectMapper.readValue(quote.getQuotePayloadJson(),
                        new TypeReference<List<QuoteLineItem>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize quote line items: {}", e.getMessage());
            }
        }

        return BookingQuoteResponse.builder()
                .quoteId(quote.getQuoteId())
                .quoteCode(quote.getQuoteCode())
                .cruiseId(quote.getCruise().getCruiseId())
                .cruiseCode(quote.getCruise().getCruiseCode())
                .departureDatetime(quote.getCruise().getDepartureDatetime())
                .policyVersion(quote.getPolicyVersion())
                .currency(quote.getCurrency())
                .lineItems(lineItems)
                .subtotalAmount(quote.getSubtotalAmount())
                .taxesAndFeesAmount(quote.getTaxesAndFeesAmount())
                .totalAmount(quote.getTotalAmount())
                .paymentPlan(quote.getPaymentPlan())
                .depositAmount(quote.getDepositAmount())
                .remainingAmount(quote.getTotalAmount().subtract(quote.getDepositAmount()))
                .finalPaymentDueAt(quote.getFinalPaymentDueAt())
                .fingerprint(quote.getFingerprint())
                .issuedAt(quote.getIssuedAt())
                .expiresAt(quote.getExpiresAt())
                .status(quote.getStatus())
                .guestToken(quote.getGuestToken())
                .build();
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse recoverBookingByIdempotencyKey(String idempotencyKey, CasmsPrincipal principal) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key không được để trống");
        }
        IdempotencyRecord record = idempotencyRecordRepository
                .findByOperationAndIdempotencyKey("CREATE_BOOKING", idempotencyKey.trim())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "RECOVERY_NOT_FOUND",
                        "Không tìm thấy giao dịch đặt chỗ tương ứng với Idempotency-Key: " + idempotencyKey));

        if (record.getResponsePayload() != null && !record.getResponsePayload().isBlank()) {
            try {
                BookingDetailResponse cached = objectMapper.readValue(record.getResponsePayload(), BookingDetailResponse.class);
                if (record.getActorId() != null && principal != null && !principal.hasAuthority("BOOKING_READ")
                        && !principal.hasAuthority("ROLE_ADMIN") && !record.getActorId().equals(principal.getUserId())) {
                    throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_RECOVERY",
                            "Bạn không có quyền truy cập kết quả giao dịch của người dùng khác");
                }
                return cached;
            } catch (Exception e) {
                log.warn("Failed to parse cached response payload for recovery: {}", e.getMessage());
            }
        }

        if (record.getResultingId() != null) {
            Booking booking = bookingRepository.findById(record.getResultingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", record.getResultingId()));
            if (record.getActorId() != null && principal != null && !principal.hasAuthority("BOOKING_READ")
                    && !principal.hasAuthority("ROLE_ADMIN") && !record.getActorId().equals(principal.getUserId())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_RECOVERY",
                        "Bạn không có quyền truy cập kết quả giao dịch của người dùng khác");
            }
            return mapToDetailResponse(booking);
        }

        throw new BusinessException(HttpStatus.CONFLICT, "RECOVERY_IN_PROGRESS",
                "Giao dịch với Idempotency-Key này đang trong quá trình xử lý, vui lòng thử lại sau giây lát");
    }

    private String generateQuoteFingerprint(Cruise cruise, CruisePricingPolicy policy, BigDecimal total) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = cruise.getCruiseId() + ":" + cruise.getRevision() + ":" + policy.getPolicyVersion() + ":" + total;
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private Cruise getCruise(UUID cruiseId) {
        return cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
    }

    private Booking getBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Tra cứu đơn đặt chỗ dành cho khách vãng lai (không cần đăng nhập, dùng bookingReference + email/số điện thoại).
     */
    @Transactional(readOnly = true)
    public BookingDetailResponse lookupBooking(String reference, String email, String phoneNumber) {
        if (reference == null || reference.isBlank()) {
            throw new BusinessException("REFERENCE_REQUIRED", "Booking reference is required for lookup");
        }
        if ((email == null || email.isBlank()) && (phoneNumber == null || phoneNumber.isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LOOKUP_CREDENTIAL_REQUIRED",
                    "Email or phone number is required for booking lookup");
        }
        Booking booking = bookingRepository.findByBookingReference(reference.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Booking reference not found: " + reference));

        boolean matched = false;
        if (email != null && !email.isBlank()) {
            String normEmail = email.trim().toLowerCase();
            if (booking.getBookedByUser() != null && normEmail.equalsIgnoreCase(booking.getBookedByUser().getEmail())) {
                matched = true;
            }
        }
        if (!matched && (email != null || phoneNumber != null)) {
            List<BookingPassenger> passengers = bookingPassengerRepository.findByBookingBookingId(booking.getBookingId());
            for (BookingPassenger bp : passengers) {
                if (bp.getPassenger() != null) {
                    if (email != null && !email.isBlank() && email.trim().equalsIgnoreCase(bp.getPassenger().getEmail())) {
                        matched = true;
                        break;
                    }
                    if (phoneNumber != null && !phoneNumber.isBlank() && phoneNumber.trim().equalsIgnoreCase(bp.getPassenger().getPhoneNumber())) {
                        matched = true;
                        break;
                    }
                }
            }
        }

        if (!matched) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LOOKUP_MISMATCH", "Email or phone number does not match this booking reference");
        }

        return mapToDetailResponse(booking);
    }

    private User resolveOrCreateGuestUser(PassengerBookingItemRequest primaryPassenger) {
        String email = primaryPassenger.getEmail();
        if (email != null && !email.isBlank()) {
            String normEmail = email.trim().toLowerCase();
            Optional<User> existingUser = userRepository.findByEmail(normEmail);
            if (existingUser.isPresent()) {
                return existingUser.get();
            }

            // Tạo tài khoản mới gắn với email này
            User guestUser = new User();
            String baseUsername = normEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
            String username = baseUsername;
            if (userRepository.existsByUsername(username)) {
                username = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 4);
            }
            guestUser.setUsername(username);
            guestUser.setEmail(normEmail);
            guestUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            guestUser.setStatus(UserStatus.ACTIVE);
            guestUser.setRoles(EnumSet.of(Role.PASSENGER));
            return userRepository.save(guestUser);
        }

        // Trường hợp không có email: tạo tài khoản khách vãng lai
        User anonymousGuest = new User();
        String anonUsername = "guest_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4);
        anonymousGuest.setUsername(anonUsername);
        anonymousGuest.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        anonymousGuest.setStatus(UserStatus.ACTIVE);
        anonymousGuest.setRoles(EnumSet.of(Role.PASSENGER));
        return userRepository.save(anonymousGuest);
    }

    private void validateAccess(Booking booking, CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication required");
        }
        boolean isStaff = principal.isAdmin() || principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                               a.getAuthority().equals("ROLE_BOOKING_AGENT") ||
                               a.getAuthority().equals("ROLE_FINANCE_OFFICER") ||
                               a.getAuthority().equals("ROLE_OPERATIONS_MANAGER") ||
                               (principal.isStaff() && a.getAuthority().equals("BOOKING_READ")));

        if (!isStaff && !booking.getBookedByUser().getUserId().equals(principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You can only view or manage your own bookings");
        }
    }
}
