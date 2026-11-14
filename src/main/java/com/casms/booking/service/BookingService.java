package com.casms.booking.service;

import com.casms.booking.dto.request.BookingCancelRequest;
import com.casms.booking.dto.request.BookingContractAcceptRequest;
import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.BookingQuoteRequest;
import com.casms.booking.dto.request.CabinChangeRequest;
import com.casms.booking.dto.request.PassengerBookingItemRequest;
import com.casms.booking.dto.request.PassengerQuoteItem;
import com.casms.booking.dto.response.AvailableCabinResponse;
import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.booking.dto.response.BookingPassengerResponse;
import com.casms.booking.dto.response.BookingQuoteResponse;
import com.casms.booking.dto.response.BookingSummaryResponse;
import com.casms.booking.dto.response.InstallmentResponse;
import com.casms.booking.dto.response.QuoteLineItem;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.entity.BookingQuote;
import com.casms.booking.entity.CabinAssignment;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.CabinSelectionMode;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingQuoteRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.cruisepackage.dto.CruisePackagePurchaseSelection;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.ship.repository.BookingAgentCruiseScope;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.notification.service.BookingEmailService;
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.entity.Refund;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.InstallmentType;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.RefundStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.RefundRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.entity.BookingPassengerPackage;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.repository.BookingPassengerPackageRepository;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.ship.dto.response.Ship3dConfigurationResponse.Vector3;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.entity.ShipModelObject;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.PricingPolicyStatus;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruisePricingPolicyRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseTourPackageRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.casms.ship.repository.ShipModelRepository;
import com.casms.ship.service.CruiseMainflowService;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final BookingEmailService bookingEmailService;

    private final CruiseMainflowService mainflow;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.casms.cruisepackage.service.CruisePackageService cruisePackageService;
    private final com.casms.cruisepackage.service.CruisePackageCabinMappingService cruisePackageCabinMappings;
    private final com.casms.cruisepackage.service.CruisePackagePurchaseSnapshotService cruisePackagePurchaseSnapshotService;
    private final com.casms.cruisepackage.service.CruisePackageEntitlementService cruisePackageEntitlementService;
    private final com.casms.ship.service.DepartureSaleTermsService departureSaleTermsService;
    private final FareHoldInventory fareHoldInventory;

    /** Tra cứu danh sách các phòng còn trống trên tàu của chuyến đi để khách hàng tự chọn. */
    @Transactional(readOnly = true)
    public List<AvailableCabinResponse> getAvailableCabins(
            UUID cruiseId, UUID deckId, CabinType cabinType, Integer minCapacity) {
        return findAvailableCabins(cruiseId, deckId, cabinType, minCapacity, false);
    }

    @Transactional(readOnly = true)
    public List<AvailableCabinResponse> getThreeDimensionalAvailableCabins(
            UUID cruiseId, UUID deckId, CabinType cabinType, Integer minCapacity) {
        return findAvailableCabins(cruiseId, deckId, cabinType, minCapacity, true);
    }

    @Transactional(readOnly = true)
    public boolean hasMappedCabinInventory(UUID cruiseId, Set<UUID> cabinIds) {
        if (cruiseId == null || cabinIds == null || cabinIds.isEmpty()) {
            return false;
        }
        return cabinRepository.findAllById(cabinIds).stream()
                .anyMatch(
                        cabin ->
                                cabin.getCapacity()
                                        > cabinAssignmentRepository
                                                .countActiveAssignmentsByCabinAndCruise(
                                                        cabin.getCabinId(), cruiseId));
    }

    private List<AvailableCabinResponse> findAvailableCabins(
            UUID cruiseId,
            UUID deckId,
            CabinType cabinType,
            Integer minCapacity,
            boolean includeModelPlacement) {
        Cruise cruise = getCruise(cruiseId);
        mainflow.requireBookable(cruiseId);
        if (cruise.getOpenedForSaleAt() == null || cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CRUISE_NOT_OPEN_FOR_SALE",
                    "Chuyến khởi hành chưa được công bố mở bán (Trạng thái: "
                            + cruise.getStatus()
                            + ")");
        }
        if (cruise.getStoppedAt() != null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CRUISE_SALE_STOPPED",
                    "Chuyến khởi hành đang tạm dừng mở bán, không thể tra cứu hoặc đặt phòng");
        }
        Itinerary itinerary =
                itineraryRepository
                        .findByCruiseCruiseId(cruise.getCruiseId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                HttpStatus.CONFLICT,
                                                "CRUISE_NOT_OPEN_FOR_SALE",
                                                "Chuyến khởi hành chưa có lịch trình mở bán"));
        if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SALE_SUSPENDED",
                    "Chuyến khởi hành đang tạm dừng mở bán, không thể tra cứu hoặc đặt phòng");
        }
        if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CRUISE_NOT_OPEN_FOR_SALE",
                    "Lịch trình chuyến khởi hành chưa mở bán thương mại");
        }
        if (cruise.getDepartureDatetime() != null
                && cruise.getDepartureDatetime().isBefore(Instant.now())) {
            throw new BusinessException(
                    "CRUISE_ALREADY_DEPARTED",
                    "Chuyến khởi hành đã khởi hành, không thể đặt phòng");
        }
        if (cruise.getShip() == null) {
            throw new BusinessException(
                    "CRUISE_NO_SHIP", "Cruise does not have an assigned ship yet");
        }

        if (!mainflow.usesUnifiedPackages(cruiseId)) {
            Optional<CruisePricingPolicy> policyOpt =
                    cruisePricingPolicyRepository
                            .findByCruise_CruiseId(cruiseId)
                            .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED);
            if (policyOpt.isEmpty()) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "PRICING_POLICY_NOT_APPROVED",
                        "Chính sách giá của chuyến khởi hành chưa được phê duyệt, không thể tra cứu giá"
                            + " hoặc phòng mở bán");
            }
        }

        UUID shipId = cruise.getShip().getShipId();

        ShipModel model =
                includeModelPlacement
                        ? shipModelRepository
                                .findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(
                                        shipId, ShipModelStatus.READY)
                                .stream()
                                .findFirst()
                                .orElse(null)
                        : null;
        Map<UUID, ShipModelObject> modelObjectsByCabinId =
                model == null
                        ? Map.of()
                        : shipModelObjectRepository
                                .findByShipModelShipModelId(model.getShipModelId())
                                .stream()
                                .filter(object -> object.getCabin() != null)
                                .collect(
                                        Collectors.toMap(
                                                object -> object.getCabin().getCabinId(),
                                                object -> object,
                                                (first, ignored) -> first));

        List<Cabin> cabins =
                cabinRepository.searchAvailable(shipId, deckId, cabinType, minCapacity, null);
        List<AvailableCabinResponse> results = new ArrayList<>();

        for (Cabin cabin : cabins) {
            long currentOccupied =
                    cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(
                            cabin.getCabinId(), cruiseId);
            int remaining = cabin.getCapacity() - (int) currentOccupied;

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
                BigDecimal multiplier =
                        (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                                ? cabin.getDeck().getPriceMultiplier()
                                : BigDecimal.valueOf(1.00);
                res.setPriceMultiplier(multiplier);
                BigDecimal baseFare = getBaseCabinFareForCruise(cruiseId, cabin.getCabinType());
                res.setEstimatedPrice(
                        baseFare != null
                                ? baseFare.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
                                : null);
                if (includeModelPlacement) {
                    ShipModelObject mapping = modelObjectsByCabinId.get(cabin.getCabinId());
                    if (model != null) res.setModelId(model.getShipModelId());
                    if (mapping != null) {
                        res.setModelNodeKey(mapping.getNodeKey());
                    }
                    Double x =
                            mapping != null && mapping.getPositionX() != null
                                    ? mapping.getPositionX()
                                    : cabin.getModelPositionX();
                    Double y =
                            mapping != null && mapping.getPositionY() != null
                                    ? mapping.getPositionY()
                                    : cabin.getModelPositionY();
                    Double z =
                            mapping != null && mapping.getPositionZ() != null
                                    ? mapping.getPositionZ()
                                    : cabin.getModelPositionZ();
                    Double rx =
                            mapping != null && mapping.getRotationX() != null
                                    ? mapping.getRotationX()
                                    : cabin.getModelRotationX();
                    Double ry =
                            mapping != null && mapping.getRotationY() != null
                                    ? mapping.getRotationY()
                                    : cabin.getModelRotationY();
                    Double rz =
                            mapping != null && mapping.getRotationZ() != null
                                    ? mapping.getRotationZ()
                                    : cabin.getModelRotationZ();
                    if (x != null || y != null || z != null) {
                        res.setModelPosition(new Vector3(x, y, z));
                    }
                    if (rx != null || ry != null || rz != null) {
                        res.setModelRotation(new Vector3(rx, ry, rz));
                    }
                    if (cabin.getModelWidth() != null
                            || cabin.getModelHeight() != null
                            || cabin.getModelDepth() != null) {
                        res.setModelDimensions(
                                new Vector3(
                                        cabin.getModelWidth(),
                                        cabin.getModelHeight(),
                                        cabin.getModelDepth()));
                    }
                }
                results.add(res);
            }
        }

        return results;
    }

    /** Tự động giải phóng các đơn đặt chỗ tạm (Draft/Hold) đã quá hạn giữ chỗ (Hold TTL). */
    @Transactional
    public int releaseExpiredHolds() {
        Instant now = Instant.now();
        List<Booking> expired = bookingRepository.findExpiredHoldBookings(now);
        int count = 0;
        for (Booking b : expired) {
            // Không giải phóng nếu đơn đã có tiền thanh toán thực tế (tránh hủy nhầm khi khách đang đợi ký hợp đồng)
            boolean hasConfirmedPayments =
                    paymentRepository.findForBooking(b.getBookingId()).stream()
                            .anyMatch(
                                    p ->
                                            p.getStatus() == PaymentStatus.Confirmed
                                                    && p.getOperationType()
                                                            == PaymentOperationType.Collect);
            if (hasConfirmedPayments) {
                log.warn(
                        "Skipping hold cancellation for booking {} because it has confirmed payments",
                        b.getBookingId());
                continue;
            }

            b.setBookingStatus(BookingStatus.Cancelled);
            b.setCancelledAt(now);
            b.setCancellationReason("HOLD_EXPIRED");
            bookingRepository.save(b);

            List<BookingPassenger> passengers =
                    bookingPassengerRepository.findByBookingBookingId(b.getBookingId());
            for (BookingPassenger bp : passengers) {
                bp.setParticipationStatus(BookingPassengerParticipationStatus.Cancelled);
                bookingPassengerRepository.save(bp);

                cabinAssignmentRepository
                        .findByBookingPassengerBookingPassengerIdAndValidToIsNull(
                                bp.getBookingPassengerId())
                        .ifPresent(
                                ca -> {
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

    /** Đặt chỗ chuyến đi mới - Hành khách tự chọn phòng cụ thể và gói dịch vụ. */
    @Transactional
    public BookingDetailResponse createBooking(
            BookingCreateRequest request, CasmsPrincipal principal) {
        requireAssignedCruise(request.getCruiseId(), principal);
        log.info("Creating new booking for cruise: {}", request.getCruiseId());
        if (request.getCurrency() != null && !request.getCurrency().isBlank())
            com.casms.common.util.NewWriteCurrency.requireUsd(
                    request.getCurrency().trim().toUpperCase(java.util.Locale.ROOT));
        releaseExpiredHolds();

        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new BusinessException(
                    "PASSENGERS_REQUIRED",
                    "At least one passenger must be included in the booking");
        }

        if (request.getPassengers().stream().anyMatch(Objects::isNull))
            throw new BusinessException("PASSENGERS_REQUIRED", "Passenger details cannot be null");

        // Validate primary passenger contact information for guest booking
        PassengerBookingItemRequest primaryPassenger = request.getPassengers().get(0);
        if ((principal == null || principal.isStaff() || principal.isAdmin())
                && (primaryPassenger.getEmail() == null || primaryPassenger.getEmail().isBlank())) {
            throw new BusinessException(
                    "PRIMARY_EMAIL_REQUIRED",
                    "Email of the primary passenger is required for booking confirmation and"
                        + " contract signing");
        }

        // Validate document numbers, cabin assignment presence, and consistency within the booking
        // request
        Set<String> documentNumbers = new HashSet<>();
        for (PassengerBookingItemRequest pReq : request.getPassengers()) {
            if (pReq.getDocumentNumber() != null && !pReq.getDocumentNumber().isBlank()) {
                String doc = pReq.getDocumentNumber().trim().toUpperCase();
                if (!documentNumbers.add(doc)) {
                    throw new BusinessException(
                            "DUPLICATE_PASSENGER_DOCUMENT",
                            "Duplicate passenger document number in the same booking: "
                                    + pReq.getDocumentNumber());
                }
            }
            if (pReq.getDocumentType() != null
                    && (pReq.getDocumentNumber() == null || pReq.getDocumentNumber().isBlank())) {
                throw new BusinessException(
                        "DOCUMENT_NUMBER_REQUIRED",
                        "Document number is required when document type is specified for passenger:"
                            + " "
                                + pReq.getFullName());
            }
        }

        User bookedByUser;
        if (principal != null && principal.getUserId() != null && !principal.isStaff() && !principal.isAdmin()) {
            bookedByUser = getUser(principal.getUserId());
            if (!bookedByUser.hasRole(Role.PASSENGER)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "PASSENGER_ACCOUNT_REQUIRED",
                        "Booking requires a passenger account or authorized booking staff");
            }
            if (primaryPassenger.getEmail() != null && !primaryPassenger.getEmail().isBlank()
                    && !primaryPassenger.getEmail().trim().equalsIgnoreCase(bookedByUser.getEmail())) {
                throw new BusinessException(HttpStatus.CONFLICT, "PASSENGER_IDENTITY_MISMATCH",
                        "Primary passenger contact must match the authenticated passenger account");
            }
        } else {
            // Guest booking: Hành khách đặt tour khi chưa đăng nhập
            bookedByUser = resolveOrCreateGuestUser(primaryPassenger);
        }

        // 1. Kiểm tra Chuyến đi (Cruise)
        Cruise cruise =
                cruiseRepository
                        .findByIdForUpdate(request.getCruiseId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Cruise", request.getCruiseId()));
        mainflow.requireBookable(cruise.getCruiseId());
        if (cruise.getOpenedForSaleAt() == null || cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException(
                    "INVALID_CRUISE_STATUS",
                    "Bookings can only be created for PUBLISHED cruises (Current: "
                            + cruise.getStatus()
                            + ")");
        }
        // BE-08B / BE-R00: Gating - Check if itinerary exists and is OPEN_FOR_SALE
        Itinerary itinerary =
                itineraryRepository
                        .findByCruiseCruiseId(cruise.getCruiseId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "CRUISE_NOT_OPEN_FOR_SALE",
                                                "Chuyến khởi hành chưa có lịch trình mở bán thương"
                                                    + " mại"));
        if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
            throw new BusinessException(
                    "SALE_SUSPENDED",
                    "Chuyến khởi hành đang tạm dừng mở bán, không thể tiếp nhận đặt chỗ mới");
        }
        if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
            throw new BusinessException(
                    "CRUISE_NOT_OPEN_FOR_SALE",
                    "Chuyến khởi hành chưa mở bán thương mại (Trạng thái lịch trình: "
                            + itinerary.getStatus()
                            + ")");
        }
        if (cruise.getDepartureDatetime().isBefore(Instant.now())) {
            throw new BusinessException(
                    "CRUISE_ALREADY_DEPARTED", "Cannot book a cruise that has already departed");
        }
        if (cruise.getShip() == null) {
            throw new BusinessException(
                    "CRUISE_NO_SHIP", "Cruise does not have an assigned ship yet");
        }

        boolean unified = mainflow.usesUnifiedPackages(cruise.getCruiseId());
        List<BookingResourceSelection> requested = request.getPassengers().stream()
                .map(item -> BookingResourceSelection.from(item).validate(unified))
                .toList();
        if (unified) cruisePackageCabinMappings.lockShip(cruise.getShip().getShipId());

        // 2. Kiểm tra tổng sức chứa tàu
        int totalNewPassengers = request.getPassengers().size();
        long currentBookedPassengers =
                bookingPassengerRepository.countActivePassengersByCruiseId(cruise.getCruiseId());
        if (currentBookedPassengers + totalNewPassengers > cruise.getShip().getTotalCapacity()) {
            throw new BusinessException(
                    "EXCEEDS_SHIP_CAPACITY",
                    String.format(
                            "Not enough capacity on ship (Total: %d, Booked: %d, Requested: %d)",
                            cruise.getShip().getTotalCapacity(),
                            currentBookedPassengers,
                            totalNewPassengers));
        }

        // 3. Kiểm tra các cabin khách hàng tự chọn
        // Gom nhóm hành khách theo cabinId để kiểm tra sức chứa phòng
        Map<UUID, List<PassengerBookingItemRequest>> cabinPassengersMap =
                request.getPassengers().stream()
                        .filter(passenger -> passenger.getCabinId() != null)
                        .collect(Collectors.groupingBy(PassengerBookingItemRequest::getCabinId));

        List<UUID> sortedCabinIds = cabinPassengersMap.keySet().stream().sorted().toList();

        Map<UUID, Cabin> validatedCabins = new HashMap<>();
        for (UUID cabinId : sortedCabinIds) {
            List<PassengerBookingItemRequest> passengersInCabin = cabinPassengersMap.get(cabinId);

            Cabin cabin =
                    cabinRepository
                            .findByIdForUpdate(cabinId)
                            .orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId));

            if (!cabin.getShip().getShipId().equals(cruise.getShip().getShipId())) {
                throw new BusinessException(
                        "CABIN_SHIP_MISMATCH",
                        "Cabin "
                                + cabin.getCabinNumber()
                                + " does not belong to the cruise ship: "
                                + cruise.getShip().getShipName());
            }

            if (cabin.getStatus() != CabinStatus.AVAILABLE) {
                throw new BusinessException(
                        "CABIN_NOT_AVAILABLE",
                        "Cabin "
                                + cabin.getCabinNumber()
                                + " is currently in status: "
                                + cabin.getStatus());
            }

            long currentOccupied =
                    cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(
                            cabinId, cruise.getCruiseId());
            int remaining = cabin.getCapacity() - (int) currentOccupied;

            if (passengersInCabin.size() > remaining) {
                throw new BusinessException(
                        "CABIN_OVERCAPACITY",
                        String.format(
                                "Cabin %s has only %d available slot(s), but %d passengers were"
                                    + " assigned to it",
                                cabin.getCabinNumber(), remaining, passengersInCabin.size()));
            }

            validatedCabins.put(cabinId, cabin);
        }

        // Legacy category limits. Unified inventory is checked with mapped cabins and unassigned holds below.
        if (!unified) {
            Map<CabinType, Long> requestedPassengersByType =
                    request.getPassengers().stream()
                            .map(p -> validatedCabins.get(p.getCabinId()).getCabinType())
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            for (Map.Entry<CabinType, Long> entry : requestedPassengersByType.entrySet()) {
                CabinType type = entry.getKey();
                Long requestedSlots = entry.getValue();
                Long totalSlotsForType =
                        cabinRepository.sumCapacityByShipIdAndCabinType(
                                cruise.getShip().getShipId(), type);
                if (totalSlotsForType == null || totalSlotsForType == 0) {
                    throw new BusinessException(
                            "CABIN_TYPE_NOT_CONFIGURED",
                            "Hạng phòng "
                                    + type
                                    + " chưa có chỗ khả dụng trên tàu: "
                                    + cruise.getShip().getShipName());
                }
                long activeAssignmentsForType =
                        cabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise(
                                type, cruise.getCruiseId());
                long remainingSlotsForType = Math.max(0, totalSlotsForType - activeAssignmentsForType);
                if (requestedSlots > remainingSlotsForType) {
                    throw new BusinessException(
                            "CABIN_TYPE_EXHAUSTED",
                            String.format(
                                    "Hạng phòng %s không còn đủ chỗ khả dụng (Còn lại: %d, Yêu cầu:"
                                        + " %d). Hệ thống từ chối bán để chống oversell.",
                                    type, remainingSlotsForType, requestedSlots));
                }
        }

        }

        // 4. Kiểm tra các gói tour (TourPackage) và tính tổng tiền vé
        Map<UUID, TourPackage> validatedPackages = new HashMap<>();
        BigDecimal totalPackageAmount = BigDecimal.ZERO;
        BigDecimal totalCabinBaseAmount = BigDecimal.ZERO;

        BookingQuote validatedQuote = null;
        List<QuoteLineItem> quoteLineItems = null;
        List<QuoteLineItem> matchedQuoteLines = List.of();
        boolean isStaff =
                principal != null
                        && (principal.hasAuthority("BOOKING_MANAGE")
                                || principal.hasAuthority("ROLE_ADMIN"));
        if (request.getQuoteId() == null && (unified || !isStaff)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "QUOTE_REQUIRED",
                    "Báo giá (quoteId) là bắt buộc đối với khách hàng khi tạo đơn đặt chỗ.");
        }

        if (request.getQuoteId() != null) {
            validatedQuote =
                    bookingQuoteRepository
                            .findById(request.getQuoteId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "BookingQuote", request.getQuoteId()));

            if ("COMMITTED".equalsIgnoreCase(validatedQuote.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "QUOTE_ALREADY_COMMITTED",
                        "Báo giá này đã được sử dụng để tạo đơn đặt chỗ trước đó. Không thể tái sử"
                            + " dụng.");
            }
            if (!validatedQuote.getCruise().getCruiseId().equals(cruise.getCruiseId())) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "QUOTE_CRUISE_MISMATCH",
                        "Báo giá không thuộc về chuyến khởi hành này");
            }
            if ("INVALIDATED".equalsIgnoreCase(validatedQuote.getStatus())
                    || "CANCELLED".equalsIgnoreCase(validatedQuote.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "QUOTE_INVALIDATED",
                        "Báo giá không còn hiệu lực do chính sách giá đã thay đổi hoặc đã bị hủy");
            }
            if (validatedQuote.isExpired()) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "QUOTE_EXPIRED",
                        "Báo giá đã hết hạn, vui lòng yêu cầu báo giá mới trước khi hoàn tất đặt"
                            + " vé");
            }
            if (validatedQuote.getUser() != null) {
                boolean isOwner =
                        principal != null
                                && validatedQuote
                                        .getUser()
                                        .getUserId()
                                        .equals(principal.getUserId());
                if (!isOwner && !isStaff) {
                    throw new BusinessException(
                            HttpStatus.FORBIDDEN,
                            "QUOTE_OWNERSHIP_MISMATCH",
                            "Không thể sử dụng báo giá của tài khoản người dùng khác");
                }
            } else if (!isStaff
                    && (validatedQuote.getGuestToken() == null
                            || validatedQuote.getGuestToken().isBlank()
                            || request.getQuoteGuestToken() == null
                            || !java.security.MessageDigest.isEqual(
                                    request.getQuoteGuestToken()
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                    java.util.Objects.toString(validatedQuote.getGuestToken(), "")
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "QUOTE_OWNERSHIP_MISMATCH",
                        "Không thể sử dụng báo giá của khách vãng lai khác");
            }

            quoteLineItems = readQuoteLines(validatedQuote);
            matchedQuoteLines = matchQuoteLines(requested, quoteLineItems);
            if (unified) {
                verifyUnifiedQuote(cruise, validatedQuote, quoteLineItems);
                for (QuoteLineItem line : matchedQuoteLines)
                    verifyFareCabin(cruise, validatedCabins.get(line.getCabinId()), line.getFareSelection());
                fareHoldInventory.verify(cruise, matchedQuoteLines);
            }
        }

        CruisePricingPolicy policy = null;
        BigDecimal portFeePerPassenger = BigDecimal.ZERO;
        BigDecimal taxRate = BigDecimal.ZERO;
        int cutoffDays = 7;
        int dueDays = 7;
        BigDecimal depositRate = BigDecimal.valueOf(0.30);
        String initialCurrency = "USD";

        if (!unified) {
            policy =
                    cruisePricingPolicyRepository
                            .findByCruise_CruiseId(cruise.getCruiseId())
                            .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED)
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    HttpStatus.UNPROCESSABLE_ENTITY,
                                                    "PRICING_POLICY_MISSING",
                                                    "Chuyến khởi hành chưa có chính sách giá được phê"
                                                        + " duyệt. Không thể hoàn tất đặt chỗ."));

            com.casms.common.util.NewWriteCurrency.requireUsd(policy.getCurrency());
            initialCurrency = policy.getCurrency();
            if (policy.getPortFeePerPassenger() != null) portFeePerPassenger = policy.getPortFeePerPassenger();
            if (policy.getTaxRate() != null) taxRate = policy.getTaxRate();
            if (policy.getFullPaymentCutoffDays() > 0) cutoffDays = policy.getFullPaymentCutoffDays();
            if (policy.getFinalPaymentDueDays() > 0) dueDays = policy.getFinalPaymentDueDays();
            if (policy.getDepositPercentage() != null) depositRate = policy.getDepositPercentage();

            if (validatedQuote != null) {
                com.casms.common.util.NewWriteCurrency.requireUsd(validatedQuote.getCurrency());
                if (!policy.getPolicyVersion().equals(validatedQuote.getPolicyVersion())) {
                    throw new BusinessException(
                            HttpStatus.CONFLICT,
                            "QUOTE_POLICY_MISMATCH",
                            "Báo giá thuộc phiên bản chính sách giá cũ ("
                                    + validatedQuote.getPolicyVersion()
                                    + "), chính sách hiện tại là ("
                                    + policy.getPolicyVersion()
                                    + "). Vui lòng yêu cầu báo giá mới.");
                }
            }
        }

        if (!unified) {
            for (PassengerBookingItemRequest pReq : request.getPassengers()) {
                Cabin cabin = validatedCabins.get(pReq.getCabinId());
                BigDecimal baseFare = policy.getBaseFareForCabinType(cabin.getCabinType());
                if (baseFare == null) {
                    throw new BusinessException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "CABIN_TYPE_UNPRICED",
                            "Hạng phòng "
                                    + cabin.getCabinType()
                                    + " chưa được định giá trong chính sách hoặc gói của chuyến khởi hành");
                }
                BigDecimal multiplier =
                        (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                                ? cabin.getDeck().getPriceMultiplier()
                                : BigDecimal.valueOf(1.00);
                BigDecimal cabinPrice = baseFare.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                totalCabinBaseAmount = totalCabinBaseAmount.add(cabinPrice);

                if (pReq.getTourPackageId() != null) {
                    TourPackage pkg =
                            tourPackageRepository
                                    .findById(pReq.getTourPackageId())
                                    .orElseThrow(
                                            () ->
                                                    new ResourceNotFoundException(
                                                            "TourPackage", pReq.getTourPackageId()));
                    if (pkg.getStatus() != TourPackageStatus.ACTIVE) {
                        throw new BusinessException(
                                "PACKAGE_NOT_ACTIVE",
                                "Tour package is not currently ACTIVE: " + pkg.getPackageName());
                    }
                    // BE-07 / BE-08B / BE-12: Product gate - Package must be assigned to this Cruise
                    // and active for sale
                    boolean assignedToCruise =
                            cruiseTourPackageRepository
                                    .existsByCruise_CruiseIdAndTourPackage_TourPackageIdAndActiveTrue(
                                            cruise.getCruiseId(), pkg.getTourPackageId());
                    if (!assignedToCruise) {
                        throw new BusinessException(
                                "PACKAGE_NOT_AVAILABLE_FOR_CRUISE",
                                "Gói dịch vụ "
                                        + pkg.getPackageName()
                                        + " chưa được mở bán cho chuyến khởi hành này");
                    }
                    validatedPackages.put(pReq.getTourPackageId(), pkg);
                    com.casms.common.util.NewWriteCurrency.requireUsd(pkg.getCurrencyCode());
                    totalPackageAmount =
                            totalPackageAmount.add(
                                    pkg.getPrice() != null ? pkg.getPrice() : BigDecimal.ZERO);
                }
            }
        }

        // 5. Xác định hình thức thanh toán & mức cọc (hỗ trợ quote authoritative hoặc dynamic
        // policy)
        PaymentPlan paymentPlan =
                request.getPaymentPlan() != null ? request.getPaymentPlan() : PaymentPlan.DEPOSIT;
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
            BigDecimal subtotalAmount = totalCabinBaseAmount.add(totalPackageAmount);
            BigDecimal portFeesAmount =
                    portFeePerPassenger.multiply(
                            BigDecimal.valueOf(request.getPassengers().size()));
            BigDecimal taxesAmount =
                    subtotalAmount.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal taxesAndFeesAmount = portFeesAmount.add(taxesAmount);
            agreedFareAmount = subtotalAmount.add(taxesAndFeesAmount);

            if (cruise.getDepartureDatetime()
                    .isBefore(scheduleCreatedAt.plus(Duration.ofDays(cutoffDays)))) {
                paymentPlan =
                        PaymentPlan
                                .FULL; // BE-11: booking vượt hạn cutoff phải áp đúng FULL theo
                                       // policy
            }

            if (paymentPlan == PaymentPlan.FULL) {
                requiredDepositAmount = agreedFareAmount;
            } else {
                requiredDepositAmount =
                        agreedFareAmount.multiply(depositRate).setScale(4, RoundingMode.HALF_UP);
            }

            currency =
                    (request.getCurrency() != null && !request.getCurrency().isBlank())
                            ? request.getCurrency().toUpperCase()
                            : (policy != null ? policy.getCurrency() : initialCurrency);

            finalPaymentDueAt = cruise.getDepartureDatetime().minus(Duration.ofDays(dueDays));
            if (finalPaymentDueAt.isBefore(scheduleCreatedAt)) {
                finalPaymentDueAt = scheduleCreatedAt;
            }
        }

        validatePaymentSchedule(
                paymentPlan, scheduleCreatedAt, finalPaymentDueAt, cruise.getDepartureDatetime());

        // Tạo đơn Booking
        Booking booking = new Booking();
        booking.setCruise(cruise);
        booking.setBookedByUser(bookedByUser);
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setAgreedFareAmount(agreedFareAmount);
        booking.setCurrency(currency);
        booking.setRequiredDepositAmount(requiredDepositAmount);
        booking.setFinalPaymentDueAt(finalPaymentDueAt);
        booking.setHoldExpiresAt(scheduleCreatedAt.plus(Duration.ofMinutes(30)));
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

        for (int i = 0; i < request.getPassengers().size(); i++) {
            PassengerBookingItemRequest pReq = request.getPassengers().get(i);
            Cabin cabin = validatedCabins.get(pReq.getCabinId());

            // Lưu hồ sơ Passenger (nếu là hành khách đại diện đầu tiên và có bookedByUser thì liên
            // kết user)
            Passenger passenger;
            if (representative == null && bookedByUser != null) {
                passenger =
                        passengerRepository
                                .findByUser_UserId(bookedByUser.getUserId())
                                .orElseGet(
                                        () -> {
                                            Passenger p = new Passenger();
                                            p.setUser(bookedByUser);
                                            return p;
                                        });
            } else {
                passenger = new Passenger();
            }
            if (passenger.getPassengerId() != null) {
                if (!Objects.equals(passenger.getFullName(), pReq.getFullName().trim())
                        || !Objects.equals(passenger.getDateOfBirth(), pReq.getDateOfBirth())
                        || !Objects.equals(passenger.getDocumentType(), pReq.getDocumentType())
                        || !Objects.equals(passenger.getDocumentNumberProtected(),
                                pReq.getDocumentNumber() == null ? null : pReq.getDocumentNumber().trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(HttpStatus.CONFLICT, "PASSENGER_IDENTITY_MISMATCH",
                            "Existing passenger identity must be verified and updated separately");
                }
            } else {
                passenger.setFullName(pReq.getFullName().trim());
                passenger.setDateOfBirth(pReq.getDateOfBirth());
                passenger.setNationality(
                        pReq.getNationality() != null
                                ? pReq.getNationality().trim().toUpperCase()
                                : null);
                passenger.setDocumentType(pReq.getDocumentType());
                passenger.setDocumentNumberProtected(
                        pReq.getDocumentNumber() != null
                                ? pReq.getDocumentNumber().trim().toUpperCase()
                                : null);
                passenger.setEmail(
                        pReq.getEmail() != null ? pReq.getEmail().trim().toLowerCase() : null);
                passenger.setPhoneNumber(
                        pReq.getPhoneNumber() != null ? pReq.getPhoneNumber().trim() : null);
            }
            passenger = passengerRepository.save(passenger);

            // Lưu BookingPassenger
            BookingPassenger bp = new BookingPassenger();
            bp.setBooking(booking);
            bp.setPassenger(passenger);
            bp.setParticipationStatus(BookingPassengerParticipationStatus.Active);
            if (unified) {
                QuoteLineItem line = matchedQuoteLines.get(i);
                var selection = line.getFareSelection();
                bp.setRequestedCabinCategory(selection.cabinType().name());
                bp.setCabinSelectionMode(line.getCabinSelectionMode());
                bp.setCabinGroupKey(line.getCabinGroupKey());
                bp.setFareOfferId(selection.offerId());
                bp.setFareTemplateVersionId(selection.templateVersionId());
                bp.setFarePriceRevisionId(selection.priceRevisionId());
                bp.setFareCabinCategory(selection.cabinType().name());
                bp.setFareSelectionPayload(writeQuoteData(selection));
            } else {
                bp.setRequestedCabinCategory(cabin.getCabinType().name());
            }
            bp = bookingPassengerRepository.save(bp);
            createdBookingPassengers.add(bp);

            if (representative == null) {
                representative = bp;
            }

            if (cabin != null) {
                // Gán phòng tự chọn (CabinAssignment)
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
        // Lưu ý bảo mật: Hợp đồng chỉ lưu mã tham chiếu đối chiếu, KHÔNG lưu file/nội dung thô vào
        // Database
        BookingContract contract = new BookingContract();
        contract.setBooking(booking);
        contract.setContractReference("CTR-" + booking.getBookingReference());
        contract.setDocumentReference("DOC-REF-SECURE-" + booking.getBookingReference());
        contract.setStatus(BookingContractStatus.Draft);
        bookingContractRepository.save(contract);

        // 8. Khởi tạo các đợt thanh toán (Installments) dựa theo hình thức khách chọn
        createInstallmentsForPlan(
                booking,
                paymentPlan,
                agreedFareAmount,
                requiredDepositAmount,
                finalPaymentDueAt,
                cruise.getDepartureDatetime(),
                scheduleCreatedAt);

        // 9. Tạo thông báo liên lạc mời hành khách tới văn phòng ký hợp đồng bản cứng
        createContractSigningNotification(booking, contract, representative, paymentPlan, cruise);

        log.info(
                "Booking created successfully with reference: {}, PaymentPlan: {}",
                booking.getBookingReference(),
                paymentPlan);
        return mapToDetailResponse(booking);
    }

    /** Lấy chi tiết đơn đặt chỗ theo ID. */
    @Transactional(readOnly = true)
    public BookingDetailResponse detail(UUID bookingId, CasmsPrincipal principal) {
        Booking booking = getBooking(bookingId);
        validateAccess(booking, principal);
        return mapToDetailResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<com.casms.cruisepackage.service.CruisePackageEntitlementService.EntitlementView> entitlements(
            UUID bookingId, CasmsPrincipal principal) {
        validateAccess(getBooking(bookingId), principal);
        return cruisePackageEntitlementService.list(bookingId);
    }

    /** Tra cứu danh sách đơn đặt chỗ (có phân quyền khách hàng xem của mình, agent xem toàn bộ). */
    @Transactional(readOnly = true)
    public Page<BookingSummaryResponse> list(
            UUID cruiseId, BookingStatus status, Pageable pageable, CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication required");
        }
        UUID agentId = BookingAgentCruiseScope.assignee(principal);
        if (agentId != null) {
            return bookingRepository.searchAssignedBookings(cruiseId, status, agentId, pageable)
                    .map(this::mapToSummaryResponse);
        }
        boolean isStaffOrAgent =
                principal != null
                        && (principal.isAdmin()
                                || principal.getAuthorities().stream()
                                        .anyMatch(
                                                a ->
                                                        a.getAuthority().equals("ROLE_ADMIN")
                                                                || a.getAuthority()
                                                                        .equals(
                                                                                "ROLE_BOOKING_AGENT")
                                                                || a.getAuthority()
                                                                        .equals(
                                                                                "ROLE_FINANCE_OFFICER")
                                                                || a.getAuthority()
                                                                        .equals(
                                                                                "ROLE_OPERATIONS_MANAGER")
                                                                || (principal.isStaff()
                                                                        && a.getAuthority()
                                                                                .equals(
                                                                                        "BOOKING_READ"))));

        UUID filterUserId = null;
        if (!isStaffOrAgent && principal != null) {
            filterUserId = principal.getUserId();
        }

        Page<Booking> page =
                bookingRepository.searchBookings(cruiseId, status, filterUserId, pageable);
        return page.map(this::mapToSummaryResponse);
    }

    /** Xác nhận hợp đồng đã ký bản cứng. */
    @Transactional
    public BookingDetailResponse acceptContract(
            UUID bookingId, BookingContractAcceptRequest request, CasmsPrincipal principal) {
        if (principal == null
                || principal.getUserId() == null
                || principal.getAuthorities().stream()
                        .noneMatch(
                                a ->
                                        a.getAuthority().equals("BOOKING_CONTRACT_MANAGE")
                                                || a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "BOOKING_CONTRACT_FORBIDDEN",
                    "Accepting physical contract requires BOOKING_CONTRACT_MANAGE permission");
        }
        Booking booking =
                bookingRepository
                        .findByIdForUpdate(bookingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        requireAssignedCruise(booking.getCruise().getCruiseId(), principal);

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException(
                    "BOOKING_CANCELLED", "Cannot accept contract for a cancelled booking");
        }

        BookingContract contract =
                bookingContractRepository
                        .findByBookingBookingId(bookingId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "BOOKING_CONTRACT_REQUIRED",
                                                "Booking contract is required"));

        if (contract.getStatus() == BookingContractStatus.Accepted) {
            return mapToDetailResponse(booking);
        }

        List<BookingPassenger> passengers =
                bookingPassengerRepository.findByBookingBookingId(bookingId);
        final UUID requestedSignerId = request.getAcceptedByBookingPassengerId();
        UUID signerId = requestedSignerId;
        if (requestedSignerId != null) {
            boolean belongs =
                    passengers.stream()
                            .anyMatch(bp -> bp.getBookingPassengerId().equals(requestedSignerId));
            if (!belongs) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PASSENGER_MISMATCH",
                        "The signer must be an active passenger in this booking");
            }
        } else {
            signerId = booking.getRepresentativeBookingPassengerId();
            if (signerId == null && !passengers.isEmpty()) {
                signerId =
                        passengers.stream()
                                .filter(
                                        bp ->
                                                bp.getParticipationStatus()
                                                        == BookingPassengerParticipationStatus.Active)
                                .map(BookingPassenger::getBookingPassengerId)
                                .findFirst()
                                .orElse(null);
            }
        }
        final UUID effectiveSignerId = signerId;
        if (passengers.stream().noneMatch(bp -> Objects.equals(bp.getBookingPassengerId(), effectiveSignerId)
                && bp.getParticipationStatus() == BookingPassengerParticipationStatus.Active)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSENGER_MISMATCH",
                    "The signer must be an active passenger in this booking");
        }
        if (request.getAcceptedAt() != null && request.getAcceptedAt().isAfter(Instant.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CONTRACT_SIGNED_AT_INVALID",
                    "Physical contract signing cannot be in the future");
        }

        contract.setStatus(BookingContractStatus.Accepted);
        contract.setDocumentReference(request.getDocumentReference().trim());
        contract.setAcceptedAt(
                request.getAcceptedAt() != null ? request.getAcceptedAt() : Instant.now());
        contract.setAcceptedByBookingPassengerId(signerId);
        bookingContractRepository.save(contract);

        log.info(
                "Contract {} accepted for booking {} by staff {} with document ref {}",
                contract.getContractReference(),
                bookingId,
                principal.getUsername(),
                request.getDocumentReference());

        // Check if deposit is already paid; if so, auto-confirm booking!
        tryAutoConfirm(booking);

        return mapToDetailResponse(booking);
    }

    /** Xác nhận đơn đặt chỗ (sau khi cọc hoặc thanh toán thành công). */
    @Transactional
    public BookingDetailResponse confirm(UUID bookingId, CasmsPrincipal principal) {
        if (principal == null
                || principal.getUserId() == null
                || principal.getAuthorities().stream()
                        .noneMatch(
                                authority -> authority.getAuthority().equals("BOOKING_CONFIRM"))) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "BOOKING_CONFIRM_FORBIDDEN",
                    "Booking confirmation requires the BOOKING_CONFIRM permission");
        }
        if (BookingAgentCruiseScope.assignee(principal) != null) {
            requireAssignedCruise(getBooking(bookingId).getCruise().getCruiseId(), principal);
        }
        return confirmInternal(bookingId);
    }

    /** Thực hiện logic xác nhận đơn đặt chỗ bên trong hệ thống. */
    @Transactional
    public BookingDetailResponse confirmInternal(UUID bookingId) {
        Booking booking =
                bookingRepository
                        .findByIdForUpdate(bookingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getBookingStatus() == BookingStatus.Confirmed) {
            return mapToDetailResponse(booking);
        }
        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Cannot confirm a cancelled booking");
        }

        BookingContract contract =
                bookingContractRepository
                        .findByBookingBookingId(bookingId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "BOOKING_CONTRACT_REQUIRED",
                                                "Booking contract is required"));
        if (contract.getStatus() != BookingContractStatus.Accepted) {
            throw new BusinessException(
                    "BOOKING_CONTRACT_NOT_ACCEPTED",
                    "Booking contract must be accepted before confirmation");
        }

        UUID capacityCruiseId = booking.getCruise().getCruiseId();
        Cruise capacityLockedCruise =
                cruiseRepository
                        .findByIdForUpdate(capacityCruiseId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Cruise", capacityCruiseId));
        if (capacityLockedCruise.getStatus() == CruiseStatus.CANCELLED) {
            throw new BusinessException(
                    "CRUISE_CANCELLED", "Cannot confirm booking on a cancelled cruise");
        }
        if (capacityLockedCruise.getStoppedAt() != null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CRUISE_SALE_STOPPED",
                    "Cannot confirm booking when cruise sale is stopped");
        }
        itineraryRepository
                .findByCruiseCruiseId(capacityCruiseId)
                .ifPresent(
                        itinerary -> {
                            if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
                                throw new BusinessException(
                                        HttpStatus.CONFLICT,
                                        "SALE_SUSPENDED",
                                        "Cannot confirm booking when cruise sale is suspended");
                            }
                            if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
                                throw new BusinessException(
                                        HttpStatus.CONFLICT,
                                        "CRUISE_NOT_OPEN_FOR_SALE",
                                        "Cruise is not currently open for sale");
                            }
                        });
        final String bookingCurrency =
                booking.getCurrency() != null ? booking.getCurrency().trim() : null;
        BigDecimal collectedAmount =
                paymentRepository.findForBooking(bookingId).stream()
                        .filter(
                                payment ->
                                        payment.getStatus() == PaymentStatus.Confirmed
                                                && payment.getConfirmedAt() != null
                                                && payment.getConfirmedAmount() != null
                                                && payment.getConfirmedAmount().signum() > 0
                                                && paymentBelongsToBooking(payment, bookingId)
                                                && paymentHasSettlementEvidence(payment)
                                                && payment.getOperationType()
                                                        == PaymentOperationType.Collect
                                                && payment.getCurrency() != null
                                                && (bookingCurrency == null
                                                        || bookingCurrency.equalsIgnoreCase(
                                                                payment.getCurrency().trim())))
                        .map(Payment::getConfirmedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedRefunds =
                refundRepository.findForBooking(bookingId).stream()
                        .filter(
                                refund ->
                                        refund.getStatus() == RefundStatus.Completed
                                                && refund.getCompletedAt() != null
                                                && refund.getApprovedAmount() != null
                                                && refund.getApprovedAmount().signum() > 0
                                                && refund.getCurrency() != null
                                                && (bookingCurrency == null
                                                        || bookingCurrency.equalsIgnoreCase(
                                                                refund.getCurrency().trim()))
                                                && refund.getProcessorOrReceiptReference() != null
                                                && !refund.getProcessorOrReceiptReference()
                                                        .isBlank())
                        .map(Refund::getApprovedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        collectedAmount = collectedAmount.subtract(completedRefunds);
        if (collectedAmount.compareTo(booking.getRequiredDepositAmount()) < 0) {
            throw new BusinessException(
                    "BOOKING_DEPOSIT_NOT_PAID",
                    "Verified collected payments do not cover the required booking deposit");
        }

        if (capacityLockedCruise.getShip() == null
                || bookingPassengerRepository.countActivePassengersByCruiseId(
                                capacityLockedCruise.getCruiseId())
                        > capacityLockedCruise.getShip().getTotalCapacity()) {
            throw new BusinessException(
                    "BOOKING_CAPACITY_INVALID", "Booking capacity is no longer valid");
        }
        List<BookingPassenger> capacityPassengers =
                bookingPassengerRepository.findByBookingBookingId(bookingId);
        boolean unifiedPurchase = capacityPassengers.stream().anyMatch(p -> p.getFareOfferId() != null)
                || (booking.getQuote() != null && booking.getQuote().getPolicyVersion() != null
                        && booking.getQuote().getPolicyVersion().startsWith("TERMS-"));
        if (unifiedPurchase) {
            if (booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(Instant.now()))
                throw new BusinessException(HttpStatus.CONFLICT, "BOOKING_HOLD_EXPIRED",
                        "Giữ chỗ theo báo giá đã hết hạn. Cần đối soát trước khi xác nhận.");
            fareHoldInventory.verify(capacityLockedCruise, List.of());
        }
        List<UUID> cabinIds =
                capacityPassengers.stream()
                        .filter(
                                passenger ->
                                        passenger.getParticipationStatus()
                                                == BookingPassengerParticipationStatus.Active)
                        .filter(passenger -> !isCategoryHold(passenger))
                        .map(
                                passenger ->
                                        cabinAssignmentRepository
                                                .findByBookingPassengerBookingPassengerIdAndValidToIsNull(
                                                        passenger.getBookingPassengerId())
                                                .map(
                                                        assignment ->
                                                                assignment.getCabin().getCabinId())
                                                .orElseThrow(
                                                        () ->
                                                                new BusinessException(
                                                                        "BOOKING_CABIN_ASSIGNMENT_MISSING",
                                                                        "An active booking"
                                                                            + " passenger no longer"
                                                                            + " has a cabin"
                                                                            + " assignment")))
                        .distinct()
                        .sorted()
                        .toList();
        Map<UUID, Cabin> capacityLockedCabins = new HashMap<>();
        for (UUID cabinId : cabinIds) {
            capacityLockedCabins.put(
                    cabinId,
                    cabinRepository
                            .findByIdForUpdate(cabinId)
                            .orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId)));
        }
        for (BookingPassenger passenger : capacityPassengers) {
            if (passenger.getParticipationStatus() != BookingPassengerParticipationStatus.Active)
                continue;
            if (isCategoryHold(passenger)) continue;
            CabinAssignment assignment =
                    cabinAssignmentRepository
                            .findByBookingPassengerBookingPassengerIdAndValidToIsNull(
                                    passenger.getBookingPassengerId())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    "BOOKING_CABIN_ASSIGNMENT_MISSING",
                                                    "An active booking passenger no longer has a"
                                                        + " cabin assignment"));
            Cabin lockedCabin =
                    assignment.getCabin() == null
                            ? null
                            : capacityLockedCabins.get(assignment.getCabin().getCabinId());
            if (lockedCabin == null
                    || cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(
                                    lockedCabin.getCabinId(), capacityLockedCruise.getCruiseId())
                            > lockedCabin.getCapacity()) {
                throw new BusinessException(
                        "BOOKING_CAPACITY_INVALID", "A cabin no longer has sufficient capacity");
            }
            if (passenger.getFareOfferId() != null)
                verifyFareCabin(capacityLockedCruise, lockedCabin, cruisePackagePurchaseSnapshotService.intent(passenger));
        }

        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setConfirmedAt(Instant.now());
        booking.setHoldExpiresAt(null);
        booking = bookingRepository.save(booking);

        if (unifiedPurchase)
            cruisePackagePurchaseSnapshotService.captureConfirmed(booking.getBookingId());

        log.info("Confirmed booking reference: {}", booking.getBookingReference());
        bookingEmailService.enqueueConfirmation(booking, verifiedNetPaid(booking));
        return mapToDetailResponse(booking);
    }

    /** Tự động xác nhận đơn nếu hợp đồng đã chấp thuận và số tiền cọc đã thu đủ. */
    public boolean tryAutoConfirm(Booking booking) {
        if (booking.getBookingStatus() != BookingStatus.Draft) {
            return false;
        }
        BookingContract contract =
                bookingContractRepository
                        .findByBookingBookingId(booking.getBookingId())
                        .orElse(null);
        if (contract == null || contract.getStatus() != BookingContractStatus.Accepted) {
            return false;
        }
        final String bookingCurrency =
                booking.getCurrency() != null ? booking.getCurrency().trim() : null;
        BigDecimal collectedAmount =
                paymentRepository.findForBooking(booking.getBookingId()).stream()
                        .filter(
                                payment ->
                                        payment.getStatus() == PaymentStatus.Confirmed
                                                && payment.getConfirmedAt() != null
                                                && payment.getConfirmedAmount() != null
                                                && payment.getConfirmedAmount().signum() > 0
                                                && paymentBelongsToBooking(
                                                        payment, booking.getBookingId())
                                                && paymentHasSettlementEvidence(payment)
                                                && payment.getOperationType()
                                                        == PaymentOperationType.Collect
                                                && payment.getCurrency() != null
                                                && (bookingCurrency == null
                                                        || bookingCurrency.equalsIgnoreCase(
                                                                payment.getCurrency().trim())))
                        .map(Payment::getConfirmedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedRefunds =
                refundRepository.findForBooking(booking.getBookingId()).stream()
                        .filter(
                                refund ->
                                        refund.getStatus() == RefundStatus.Completed
                                                && refund.getCompletedAt() != null
                                                && refund.getApprovedAmount() != null
                                                && refund.getApprovedAmount().signum() > 0
                                                && refund.getCurrency() != null
                                                && (bookingCurrency == null
                                                        || bookingCurrency.equalsIgnoreCase(
                                                                refund.getCurrency().trim()))
                                                && refund.getProcessorOrReceiptReference() != null
                                                && !refund.getProcessorOrReceiptReference()
                                                        .isBlank())
                        .map(Refund::getApprovedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        collectedAmount = collectedAmount.subtract(completedRefunds);

        BigDecimal requiredDeposit =
                booking.getRequiredDepositAmount() != null
                        ? booking.getRequiredDepositAmount()
                        : booking.getAgreedFareAmount();
        if (requiredDeposit != null && collectedAmount.compareTo(requiredDeposit) >= 0) {
            // Do not swallow a failure after changing booking state or writing the outbox.
            // The caller must roll back and retry/reconcile the entire transaction.
            confirmInternal(booking.getBookingId());
            return true;
        }
        return false;
    }

    private boolean isCategoryHold(BookingPassenger passenger) {
        return passenger.getFareOfferId() != null && passenger.getCabinSelectionMode() == CabinSelectionMode.CATEGORY_HOLD;
    }

    /** Hủy đơn đặt chỗ. */
    @Transactional
    public BookingDetailResponse cancel(
            UUID bookingId, BookingCancelRequest request, CasmsPrincipal principal) {
        Booking booking =
                bookingRepository
                        .findByIdForUpdate(bookingId)
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
        List<BookingPassenger> passengers =
                bookingPassengerRepository.findByBookingBookingId(bookingId);
        for (BookingPassenger bp : passengers) {
            bp.setParticipationStatus(BookingPassengerParticipationStatus.Cancelled);
            bookingPassengerRepository.save(bp);

            // Đóng hiệu lực gán phòng hiện hành
            cabinAssignmentRepository
                    .findByBookingPassengerBookingPassengerIdAndValidToIsNull(
                            bp.getBookingPassengerId())
                    .ifPresent(
                            ca -> {
                                ca.setValidTo(Instant.now());
                                ca.setChangeReason("Đơn đặt chỗ đã bị hủy: " + request.getReason());
                                cabinAssignmentRepository.save(ca);
                            });
        }

        // Hủy hợp đồng nếu có
        bookingContractRepository
                .findByBookingBookingId(bookingId)
                .ifPresent(
                        c -> {
                            c.setStatus(BookingContractStatus.Cancelled);
                            bookingContractRepository.save(c);
                        });

        log.info(
                "Cancelled booking reference: {} with reason: {}",
                booking.getBookingReference(),
                request.getReason());
        return mapToDetailResponse(booking);
    }

    /** Đổi phòng cho hành khách đã đặt. */
    @Transactional
    public BookingDetailResponse changeCabin(CabinChangeRequest request, CasmsPrincipal principal) {
        BookingPassenger passengerSnapshot =
                bookingPassengerRepository
                        .findById(request.getBookingPassengerId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "BookingPassenger",
                                                request.getBookingPassengerId()));

        UUID bookingId = passengerSnapshot.getBooking().getBookingId();
        Booking booking =
                bookingRepository
                        .findByIdForUpdate(bookingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        BookingPassenger bp =
                bookingPassengerRepository
                        .lockById(request.getBookingPassengerId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "BookingPassenger",
                                                request.getBookingPassengerId()));
        validateAccess(booking, principal);

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException(
                    "BOOKING_CANCELLED", "Cannot change cabin for a cancelled booking");
        }
        if (bp.getFareOfferId() != null)
            throw new BusinessException(HttpStatus.CONFLICT, "FARE_CABIN_CHANGE_NOT_SUPPORTED",
                    "Đổi cabin của gói du thuyền cần kiểm tra lại nhóm và mapping đã mua.");

        Cruise cruise =
                cruiseRepository
                        .findByIdForUpdate(booking.getCruise().getCruiseId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Cruise", booking.getCruise().getCruiseId()));
        Cabin newCabin =
                cabinRepository
                        .findByIdForUpdate(request.getNewCabinId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Cabin", request.getNewCabinId()));

        if (!newCabin.getShip().getShipId().equals(cruise.getShip().getShipId())) {
            throw new BusinessException(
                    "CABIN_SHIP_MISMATCH", "New cabin does not belong to the cruise's ship");
        }
        if (newCabin.getStatus() != CabinStatus.AVAILABLE) {
            throw new BusinessException("CABIN_NOT_AVAILABLE", "New cabin is not AVAILABLE");
        }

        long currentOccupied =
                cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(
                        newCabin.getCabinId(), cruise.getCruiseId());
        if (currentOccupied >= newCabin.getCapacity()) {
            throw new BusinessException("CABIN_FULL", "New cabin is already at full capacity");
        }

        // Kết thúc gán phòng cũ
        cabinAssignmentRepository
                .findByBookingPassengerBookingPassengerIdAndValidToIsNull(
                        bp.getBookingPassengerId())
                .ifPresent(
                        oldAssignment -> {
                            oldAssignment.setValidTo(Instant.now());
                            oldAssignment.setChangeReason(
                                    request.getChangeReason() != null
                                            ? request.getChangeReason()
                                            : "Chuyển sang phòng " + newCabin.getCabinNumber());
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
        newAssignment.setChangeReason(
                request.getChangeReason() != null
                        ? request.getChangeReason()
                        : "Đổi phòng theo yêu cầu");
        cabinAssignmentRepository.save(newAssignment);

        bp.setRequestedCabinCategory(newCabin.getCabinType().name());
        bookingPassengerRepository.save(bp);
        if (mainflow.usesUnifiedPackages(cruise.getCruiseId())) {
            cabinAssignmentRepository.flush();
            fareHoldInventory.verify(cruise, List.of());
        }

        log.info(
                "Changed cabin for passenger: {} to cabin: {}",
                bp.getBookingPassengerId(),
                newCabin.getCabinNumber());
        return mapToDetailResponse(booking);
    }

    // ==========================================
    // Helper Methods & Mappings
    // ==========================================

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
        String scope = cruise.getPlannedScope() != null ? cruise.getPlannedScope() : "DOMESTIC";
        boolean isInter = "INTERNATIONAL".equalsIgnoreCase(scope);
        res.setPlannedScope(scope);
        res.setIsInternational(isInter);

        res.setAgreedFareAmount(booking.getAgreedFareAmount());
        res.setCurrency(booking.getCurrency());
        res.setRequiredDepositAmount(booking.getRequiredDepositAmount());
        res.setFinalPaymentDueAt(booking.getFinalPaymentDueAt());
        BigDecimal netPaid = verifiedNetPaid(booking);
        res.setTotalPaidAmount(netPaid);
        res.setOutstandingBalance(booking.getAgreedFareAmount().subtract(netPaid).max(BigDecimal.ZERO));
        res.setFullyPaid(res.getOutstandingBalance().signum() == 0);

        res.setBookedByUserId(booking.getBookedByUser().getUserId());
        res.setBookedByUsername(booking.getBookedByUser().getUsername());
        res.setCreatedAt(booking.getCreatedAt());
        res.setConfirmedAt(booking.getConfirmedAt());
        res.setCancelledAt(booking.getCancelledAt());
        res.setCancellationReason(booking.getCancellationReason());

        bookingContractRepository
                .findByBookingBookingId(booking.getBookingId())
                .ifPresent(
                        c -> {
                            res.setContractReference(c.getContractReference());
                            res.setContractStatus(c.getStatus());
                            res.setContractDocumentReference(c.getDocumentReference());
                            res.setContractAcceptedAt(c.getAcceptedAt());
                        });

        List<BookingPassenger> bookingPassengers =
                bookingPassengerRepository.findByBookingBookingId(booking.getBookingId());
        List<BookingPassengerPackage> packages =
                bookingPassengerPackageRepository.findByBookingPassengerBookingBookingId(
                        booking.getBookingId());
        Map<UUID, BookingPassengerPackage> packageMap =
                packages.stream()
                        .collect(
                                Collectors.toMap(
                                        BookingPassengerPackage::getBookingPassengerId,
                                        p -> p,
                                        (p1, p2) -> p1));

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
                var auth =
                        org.springframework.security.core.context.SecurityContextHolder.getContext()
                                .getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal) {
                    currentUserId = principal.getUserId();
                }
                boolean owned =
                        (p.getUser() != null
                                        && currentUserId != null
                                        && p.getUser().getUserId().equals(currentUserId))
                                || (p.getUser() != null
                                        && currentUserId == null
                                        && booking.getBookedByUser() != null
                                        && p.getUser()
                                                .getUserId()
                                                .equals(booking.getBookedByUser().getUserId()));
                pRes.setOwnedByCurrentUser(owned);
            } else {
                pRes.setOwnedByCurrentUser(false);
            }

            pRes.setParticipationStatus(bp.getParticipationStatus());
            pRes.setRequestedCabinCategory(bp.getRequestedCabinCategory());
            if (bp.getFareOfferId() != null) {
                pRes.setCabinSelectionMode(bp.getCabinSelectionMode());
                pRes.setCabinGroupKey(bp.getCabinGroupKey());
                pRes.setFarePackageOfferId(bp.getFareOfferId());
                pRes.setFareCabinCategory(bp.getFareCabinCategory());
                pRes.setSelectedFare(cruisePackagePurchaseSnapshotService.intent(bp));
                pRes.setCanSelectFareCabin(false);
                if (booking.getBookingStatus() == BookingStatus.Confirmed)
                    pRes.setPurchasedFare(cruisePackagePurchaseSnapshotService.purchased(bp.getBookingPassengerId()));
            }

            // Thông tin phòng gán hiện hành
            cabinAssignmentRepository
                    .findByBookingPassengerBookingPassengerIdAndValidToIsNull(
                            bp.getBookingPassengerId())
                    .ifPresent(
                            ca -> {
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
        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                        booking.getBookingId());
        List<InstallmentResponse> installmentResponses = new ArrayList<>();
        BigDecimal unallocatedPaid = netPaid;
        for (Installment inst : installments) {
            InstallmentResponse iRes = new InstallmentResponse();
            iRes.setInstallmentId(inst.getInstallmentId());
            iRes.setSequenceNo(inst.getSequenceNo());
            iRes.setInstallmentType(inst.getInstallmentType());
            iRes.setRequiredAmount(inst.getRequiredAmount());
            iRes.setCurrency(inst.getCurrency());
            iRes.setDueAt(inst.getDueAt());
            iRes.setStatus(inst.getStatus());
            BigDecimal allocated = inst.getStatus() == InstallmentStatus.Cancelled ? BigDecimal.ZERO
                    : unallocatedPaid.min(inst.getRequiredAmount());
            iRes.setPaidAmount(allocated);
            iRes.setOutstandingAmount(inst.getRequiredAmount().subtract(allocated).max(BigDecimal.ZERO));
            iRes.setPaid(iRes.getOutstandingAmount().signum() == 0);
            unallocatedPaid = unallocatedPaid.subtract(allocated);

            if (installments.size() == 1) {
                iRes.setDescription("Thanh toán toàn bộ 100% tiền vé");
            } else if (installments.size() == 2) {
                iRes.setDescription(
                        inst.getSequenceNo() == 1
                                ? "Đợt 1: Đặt cọc 30% khi ký hợp đồng"
                                : "Đợt 2: Thanh toán 70% còn lại trước ngày khởi hành");
            } else {
                if (inst.getSequenceNo() == 1)
                    iRes.setDescription("Đợt 1: Đặt cọc giữ chỗ 30% khi ký hợp đồng");
                else if (inst.getSequenceNo() == 2)
                    iRes.setDescription("Đợt 2: Thanh toán giữa kỳ 40%");
                else iRes.setDescription("Đợt 3: Thanh toán 30% còn lại trước ngày khởi hành");
            }
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

        String contractRef =
                res.getContractReference() != null
                        ? res.getContractReference()
                        : "CTR-" + booking.getBookingReference();
        res.setContractSigningInstructions(
                "Vì lý do bảo mật thông tin cá nhân, hợp đồng được ký bản cứng theo quy trình hiện"
                    + " hành. Vui lòng sử dụng mã đối chiếu "
                        + contractRef
                        + " và liên hệ OceVie để xác nhận địa điểm, thời hạn ký và hướng dẫn thanh"
                        + " toán.");

        return res;
    }

    private void createInstallmentsForPlan(
            Booking booking,
            PaymentPlan plan,
            BigDecimal agreedFare,
            BigDecimal depositAmount,
            Instant finalDue,
            Instant departure,
            Instant scheduleCreatedAt) {
        String currency = booking.getCurrency();

        switch (plan) {
            case FULL -> {
                Installment inst = new Installment();
                inst.setBooking(booking);
                inst.setSequenceNo(1);
                inst.setInstallmentType(InstallmentType.FinalFare);
                inst.setRequiredAmount(agreedFare);
                inst.setCurrency(currency);
                inst.setDueAt(scheduleCreatedAt.plus(Duration.ofDays(2)));
                inst.setStatus(InstallmentStatus.Scheduled);
                installmentRepository.save(inst);
            }
            case DEPOSIT -> {
                // Đợt 1: Cọc 30%
                Installment inst1 = new Installment();
                inst1.setBooking(booking);
                inst1.setSequenceNo(1);
                inst1.setInstallmentType(InstallmentType.Deposit);
                inst1.setRequiredAmount(depositAmount);
                inst1.setCurrency(currency);
                inst1.setDueAt(scheduleCreatedAt.plus(Duration.ofDays(2)));
                inst1.setStatus(InstallmentStatus.Scheduled);
                installmentRepository.save(inst1);

                // Đợt 2: Còn lại 70%
                Installment inst2 = new Installment();
                inst2.setBooking(booking);
                inst2.setSequenceNo(2);
                inst2.setInstallmentType(InstallmentType.FinalFare);
                inst2.setRequiredAmount(agreedFare.subtract(depositAmount));
                inst2.setCurrency(currency);
                inst2.setDueAt(finalDue);
                inst2.setStatus(InstallmentStatus.Scheduled);
                installmentRepository.save(inst2);
            }
            case STAGES -> {
                BigDecimal s1 =
                        agreedFare
                                .multiply(BigDecimal.valueOf(0.30))
                                .setScale(4, RoundingMode.HALF_UP);
                BigDecimal s2 =
                        agreedFare
                                .multiply(BigDecimal.valueOf(0.40))
                                .setScale(4, RoundingMode.HALF_UP);
                BigDecimal s3 = agreedFare.subtract(s1).subtract(s2);

                // Đợt 1: 30% cọc
                Installment inst1 = new Installment();
                inst1.setBooking(booking);
                inst1.setSequenceNo(1);
                inst1.setInstallmentType(InstallmentType.Deposit);
                inst1.setRequiredAmount(s1);
                inst1.setCurrency(currency);
                inst1.setDueAt(scheduleCreatedAt.plus(Duration.ofDays(2)));
                inst1.setStatus(InstallmentStatus.Scheduled);
                installmentRepository.save(inst1);

                // Đợt 2: 40% giữa kỳ
                Instant s2Due = resolveSecondStageDue(scheduleCreatedAt, departure);
                Installment inst2 = new Installment();
                inst2.setBooking(booking);
                inst2.setSequenceNo(2);
                inst2.setInstallmentType(InstallmentType.Deposit);
                inst2.setRequiredAmount(s2);
                inst2.setCurrency(currency);
                inst2.setDueAt(s2Due);
                inst2.setStatus(InstallmentStatus.Scheduled);
                installmentRepository.save(inst2);

                // Đợt 3: 30% còn lại
                Installment inst3 = new Installment();
                inst3.setBooking(booking);
                inst3.setSequenceNo(3);
                inst3.setInstallmentType(InstallmentType.FinalFare);
                inst3.setRequiredAmount(s3);
                inst3.setCurrency(currency);
                inst3.setDueAt(finalDue);
                inst3.setStatus(InstallmentStatus.Scheduled);
                installmentRepository.save(inst3);
            }
        }
    }

    void validatePaymentSchedule(
            PaymentPlan plan, Instant createdAt, Instant finalDue, Instant departure) {
        Instant firstDue = createdAt.plus(Duration.ofDays(2));
        if (!firstDue.isBefore(departure)) {
            throw new BusinessException(
                    "PAYMENT_SCHEDULE_UNAVAILABLE",
                    "The selected payment plan cannot be completed before cruise departure");
        }
        if (plan == PaymentPlan.FULL) {
            return;
        }
        if (firstDue.isAfter(finalDue)) {
            throw new BusinessException(
                    "PAYMENT_SCHEDULE_UNAVAILABLE",
                    "The final installment would be due before the initial installment");
        }
        if (plan == PaymentPlan.STAGES) {
            Instant secondDue = resolveSecondStageDue(createdAt, departure);
            if (secondDue.isAfter(finalDue) || !secondDue.isBefore(departure)) {
                throw new BusinessException(
                        "PAYMENT_SCHEDULE_UNAVAILABLE",
                        "The staged payment deadlines are not in chronological order for this"
                            + " departure date");
            }
        }
    }

    private Instant resolveSecondStageDue(Instant createdAt, Instant departure) {
        Instant secondDue = departure.minus(Duration.ofDays(30));
        if (secondDue.isBefore(createdAt.plus(Duration.ofDays(3)))) {
            secondDue = createdAt.plus(Duration.ofDays(7));
        }
        return secondDue;
    }

    private boolean paymentHasSettlementEvidence(Payment payment) {
        if (payment.getPaymentMethod() == null) return false;
        return switch (payment.getPaymentMethod()) {
            case Cash ->
                    payment.getInternalReceiptReference() != null
                            && !payment.getInternalReceiptReference().isBlank();
            case GatewaySandbox, Sepay, CreditCard ->
                    payment.getProcessorReference() != null
                            && !payment.getProcessorReference().isBlank();
        };
    }

    private boolean paymentBelongsToBooking(Payment payment, UUID bookingId) {
        return (payment.getBooking() != null
                        && bookingId.equals(payment.getBooking().getBookingId()))
                || (payment.getInstallment() != null
                        && payment.getInstallment().getBooking() != null
                        && bookingId.equals(payment.getInstallment().getBooking().getBookingId()));
    }

    private void createContractSigningNotification(
            Booking booking,
            BookingContract contract,
            BookingPassenger representative,
            PaymentPlan plan,
            Cruise cruise) {
        String repName =
                (representative != null && representative.getPassenger() != null)
                        ? representative.getPassenger().getFullName()
                        : "Quý khách";

        Notification notif = new Notification();
        notif.setRecipientUser(booking.getBookedByUser());
        notif.setBookingPassengerId(
                representative != null ? representative.getBookingPassengerId() : null);
        notif.setNotificationType("CONTRACT_SIGNING_INVITATION");
        notif.setTitle(
                "Thư mời ký hợp đồng & Xác nhận đặt chỗ chuyến khởi hành "
                        + booking.getBookingReference());
        notif.setContent(
                String.format(
                        "Kính gửi %s,\n\n"
                            + "Đơn đặt chỗ mã [%s] cho chuyến du thuyền '%s' đã được tạo thành"
                            + " công.\n"
                            + "- Mã hợp đồng đối chiếu: %s\n"
                            + "- Hình thức thanh toán đã chọn: %s\n"
                            + "- Tổng giá trị: %s %s (Cọc tối thiểu: %s %s)\n\n"
                            + "**LƯU Ý BẢO MẬT:** Để bảo mật tuyệt đối thông tin định danh cá nhân"
                            + " của quý khách, hợp đồng pháp lý KHÔNG lưu trữ văn bản thô trên hệ"
                            + " thống trực tuyến.\n"
                            + "Vui lòng liên hệ OceVie để xác nhận địa điểm, thời hạn ký hợp đồng"
                            + " bản cứng và hướng dẫn thanh toán theo hình thức đã chọn.\n\n"
                            + "Trân trọng cảm ơn quý khách đã đồng hành cùng CASMS!",
                        repName,
                        booking.getBookingReference(),
                        cruise.getCruiseName(),
                        contract.getContractReference(),
                        plan,
                        booking.getAgreedFareAmount(),
                        booking.getCurrency(),
                        booking.getRequiredDepositAmount(),
                        booking.getCurrency()));
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

        long count =
                bookingPassengerRepository.findByBookingBookingId(booking.getBookingId()).size();
        res.setPassengerCount((int) count);
        return res;
    }

    public BigDecimal getBaseCabinFare(CabinType cabinType) {
        return null;
    }

    public BigDecimal getBaseCabinFareForCruise(UUID cruiseId, CabinType cabinType) {
        if (cruiseId != null && cabinType != null) {
            if (mainflow.usesUnifiedPackages(cruiseId)) return null;
            Optional<CruisePricingPolicy> policyOpt =
                    cruisePricingPolicyRepository
                            .findByCruise_CruiseId(cruiseId)
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
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "REQUEST_REQUIRED",
                    "Yêu cầu báo giá không được để trống");
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank())
            com.casms.common.util.NewWriteCurrency.requireUsd(
                    request.getCurrency().trim().toUpperCase(java.util.Locale.ROOT));
        if (request.getCruiseId() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "CRUISE_ID_REQUIRED", "Cruise ID không được để trống");
        }
        requireAssignedCruise(request.getCruiseId(), principal);
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "PASSENGERS_REQUIRED",
                    "Danh sách hành khách không được để trống");
        }
        if (request.getPassengers().stream().anyMatch(Objects::isNull))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSENGER_REQUIRED", "Thiếu thông tin hành khách.");

        boolean unified = mainflow.usesUnifiedPackages(request.getCruiseId());
        List<BookingResourceSelection> requested = request.getPassengers().stream()
                .map(item -> BookingResourceSelection.from(item).validate(unified))
                .toList();

        Cruise cruise = (unified ? cruiseRepository.findByIdForUpdate(request.getCruiseId())
                : cruiseRepository.findById(request.getCruiseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", request.getCruiseId()));

        mainflow.requireBookable(cruise.getCruiseId());
        if (cruise.getOpenedForSaleAt() == null || cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CRUISE_STATUS",
                    "Chỉ có thể tạo báo giá cho chuyến khởi hành đã phát hành (PUBLISHED). Trạng"
                        + " thái hiện tại: "
                            + cruise.getStatus());
        }
        if (cruise.getStoppedAt() != null) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CRUISE_SALE_STOPPED",
                    "Chuyến khởi hành đã bị dừng mở bán, không thể tạo báo giá.");
        }
        Itinerary itinerary =
                itineraryRepository
                        .findByCruiseCruiseId(cruise.getCruiseId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                HttpStatus.UNPROCESSABLE_ENTITY,
                                                "CRUISE_NOT_OPEN_FOR_SALE",
                                                "Chuyến khởi hành chưa có lịch trình mở bán thương"
                                                    + " mại."));
        if (itinerary.getStatus() == ItineraryStatus.SUSPENDED) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SALE_SUSPENDED",
                    "Chuyến khởi hành đang tạm dừng mở bán, không thể tạo báo giá.");
        }
        if (itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CRUISE_NOT_OPEN_FOR_SALE",
                    "Chuyến khởi hành chưa mở bán thương mại (Trạng thái lịch trình: "
                            + itinerary.getStatus()
                            + ").");
        }
        if (cruise.getDepartureDatetime() != null
                && cruise.getDepartureDatetime().isBefore(Instant.now())) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CRUISE_ALREADY_DEPARTED",
                    "Chuyến khởi hành đã khởi hành, không thể tạo báo giá.");
        }
        if (cruise.getShip() == null) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CRUISE_NO_SHIP",
                    "Chuyến khởi hành chưa được gán tàu.");
        }

        CruisePricingPolicy policy = null;
        String currency = "USD";
        BigDecimal portFeePerPassenger = BigDecimal.ZERO;
        BigDecimal taxRate = BigDecimal.ZERO;

        UnifiedTerms unifiedTerms = unified ? approvedUnifiedTerms(cruise.getCruiseId()) : null;
        if (unified) {
            portFeePerPassenger = unifiedTerms.portFee();
            taxRate = unifiedTerms.taxRate();
        } else {
            policy =
                    cruisePricingPolicyRepository
                            .findByCruise_CruiseId(cruise.getCruiseId())
                            .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED)
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    HttpStatus.UNPROCESSABLE_ENTITY,
                                                    "PRICING_POLICY_MISSING",
                                                    "Chuyến khởi hành chưa có chính sách giá được phê"
                                                        + " duyệt. Không thể phát hành báo giá."));
            currency = policy.getCurrency();
            if (policy.getPortFeePerPassenger() != null) portFeePerPassenger = policy.getPortFeePerPassenger();
            if (policy.getTaxRate() != null) taxRate = policy.getTaxRate();
        }

        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            if (!request.getCurrency().trim().equalsIgnoreCase(currency)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "CURRENCY_MISMATCH",
                        "Đơn vị tiền tệ yêu cầu ("
                                + request.getCurrency()
                                + ") không khớp với chính sách chuyến khởi hành ("
                                + currency
                                + ").");
            }
        }
        com.casms.common.util.NewWriteCurrency.requireUsd(currency);

        String saleScope =
                cruise.getSaleScope() != null
                        ? cruise.getSaleScope().toUpperCase()
                        : "WHOLE_CRUISE";
        if ("CABIN_ONLY".equals(saleScope)) {
            boolean hasPkg =
                    request.getPassengers().stream().anyMatch(p -> p.getTourPackageId() != null);
            if (hasPkg) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PACKAGE_NOT_ALLOWED_IN_CABIN_ONLY_SCOPE",
                        "Chuyến khởi hành đang mở bán theo phạm vi CABIN_ONLY, không chấp nhận đính"
                            + " kèm gói tour/dịch vụ.");
            }
        }

        int totalNewPassengers = request.getPassengers().size();
        long currentBookedPassengers =
                bookingPassengerRepository.countActivePassengersByCruiseId(cruise.getCruiseId());
        if (currentBookedPassengers + totalNewPassengers > cruise.getShip().getTotalCapacity()) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EXCEEDS_SHIP_CAPACITY",
                    String.format(
                            "Không đủ sức chứa trên tàu (Tổng: %d, Đã giữ/đặt: %d, Yêu cầu: %d)",
                            cruise.getShip().getTotalCapacity(),
                            currentBookedPassengers,
                            totalNewPassengers));
        }

        Map<UUID, List<PassengerQuoteItem>> cabinPassengersMap =
                request.getPassengers().stream()
                        .filter(passenger -> passenger.getCabinId() != null)
                        .collect(Collectors.groupingBy(PassengerQuoteItem::getCabinId));

        Map<UUID, Cabin> validatedCabins = new HashMap<>();
        for (Map.Entry<UUID, List<PassengerQuoteItem>> entry : cabinPassengersMap.entrySet()) {
            UUID cabinId = entry.getKey();
            List<PassengerQuoteItem> passengersInCabin = entry.getValue();

            Cabin cabin =
                    cabinRepository
                            .findById(cabinId)
                            .orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId));

            if (!cabin.getShip().getShipId().equals(cruise.getShip().getShipId())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "CABIN_SHIP_MISMATCH",
                        "Phòng "
                                + cabin.getCabinNumber()
                                + " không thuộc về tàu của chuyến khởi hành: "
                                + cruise.getShip().getShipName());
            }

            if (cabin.getStatus() != CabinStatus.AVAILABLE) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "CABIN_NOT_AVAILABLE",
                        "Phòng "
                                + cabin.getCabinNumber()
                                + " hiện không khả dụng (Trạng thái: "
                                + cabin.getStatus()
                                + ")");
            }

            long currentOccupied =
                    cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(
                            cabinId, cruise.getCruiseId());
            int remaining = cabin.getCapacity() - (int) currentOccupied;

            if (passengersInCabin.size() > remaining) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "CABIN_OVERCAPACITY",
                        String.format(
                                "Phòng %s chỉ còn %d chỗ khả dụng, nhưng có %d khách được gán vào"
                                    + " phòng này",
                                cabin.getCabinNumber(), remaining, passengersInCabin.size()));
            }

            validatedCabins.put(cabinId, cabin);
        }

        if (!unified) {
            Map<CabinType, Long> requestedPassengersByType =
                    request.getPassengers().stream()
                            .map(p -> validatedCabins.get(p.getCabinId()).getCabinType())
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            for (Map.Entry<CabinType, Long> entry : requestedPassengersByType.entrySet()) {
                CabinType type = entry.getKey();
                Long requestedSlots = entry.getValue();
                Long totalSlotsForType =
                        cabinRepository.sumCapacityByShipIdAndCabinType(
                                cruise.getShip().getShipId(), type);
                if (totalSlotsForType == null || totalSlotsForType == 0) {
                    throw new BusinessException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "CABIN_TYPE_NOT_CONFIGURED",
                            "Hạng phòng "
                                    + type
                                    + " chưa có chỗ khả dụng trên tàu: "
                                    + cruise.getShip().getShipName());
                }
                long activeAssignmentsForType =
                        cabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise(
                                type, cruise.getCruiseId());
                long remainingSlotsForType = Math.max(0, totalSlotsForType - activeAssignmentsForType);
                if (requestedSlots > remainingSlotsForType) {
                    throw new BusinessException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "CABIN_TYPE_EXHAUSTED",
                            String.format(
                                    "Hạng phòng %s không còn đủ chỗ khả dụng (Còn lại: %d, Yêu cầu:"
                                        + " %d). Hệ thống từ chối để chống oversell.",
                                    type, remainingSlotsForType, requestedSlots));
                }
            }
        }

        List<QuoteLineItem> lineItems = unified
                ? prepareFareLines(cruise, requested, validatedCabins)
                : new ArrayList<>();
        if (unified) fareHoldInventory.verify(cruise, lineItems);
        BigDecimal subtotalCabin = BigDecimal.ZERO;
        BigDecimal subtotalPackage = BigDecimal.ZERO;

        if (unified) {
            subtotalCabin = lineItems.stream().map(QuoteLineItem::getItemTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            for (PassengerQuoteItem pReq : request.getPassengers()) {
                Cabin cabin = validatedCabins.get(pReq.getCabinId());
                BigDecimal baseFare = policy.getBaseFareForCabinType(cabin.getCabinType());
                if (baseFare == null) {
                    throw new BusinessException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "CABIN_TYPE_UNPRICED",
                            "Hạng phòng "
                                    + cabin.getCabinType()
                                    + " chưa được định giá trong chính sách hoặc gói của chuyến khởi hành");
                }
                BigDecimal multiplier =
                        (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                                ? cabin.getDeck().getPriceMultiplier()
                                : BigDecimal.valueOf(1.00);
                BigDecimal cabinPrice = baseFare.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                subtotalCabin = subtotalCabin.add(cabinPrice);

                UUID pkgId = null;
                String pkgName = null;
                BigDecimal pkgPrice = BigDecimal.ZERO;

                if (pReq.getTourPackageId() != null) {
                    TourPackage pkg =
                            tourPackageRepository
                                    .findById(pReq.getTourPackageId())
                                    .orElseThrow(
                                            () ->
                                                    new ResourceNotFoundException(
                                                            "TourPackage", pReq.getTourPackageId()));
                    if (pkg.getStatus() != TourPackageStatus.ACTIVE) {
                        throw new BusinessException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "PACKAGE_INACTIVE",
                                "Gói dịch vụ "
                                        + pkg.getPackageName()
                                        + " không ở trạng thái hoạt động");
                    }
                    boolean assigned =
                            cruiseTourPackageRepository
                                    .existsByCruise_CruiseIdAndTourPackage_TourPackageIdAndActiveTrue(
                                            cruise.getCruiseId(), pkg.getTourPackageId());
                    if (!assigned) {
                        throw new BusinessException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "PACKAGE_NOT_AVAILABLE_FOR_CRUISE",
                                "Gói dịch vụ "
                                        + pkg.getPackageName()
                                        + " chưa được mở bán cho chuyến khởi hành này");
                    }
                    pkgId = pkg.getTourPackageId();
                    com.casms.common.util.NewWriteCurrency.requireUsd(pkg.getCurrencyCode());
                    pkgName = pkg.getPackageName();
                    pkgPrice = pkg.getPrice() != null ? pkg.getPrice() : BigDecimal.ZERO;
                    subtotalPackage = subtotalPackage.add(pkgPrice);
                }

                BigDecimal itemTotal = cabinPrice.add(pkgPrice);
                lineItems.add(
                        QuoteLineItem.builder()
                                .cabinId(cabin.getCabinId())
                                .cabinNumber(cabin.getCabinNumber())
                                .cabinType(cabin.getCabinType())
                                .deckNumber(
                                        cabin.getDeck() != null
                                                ? cabin.getDeck().getDeckNumber()
                                                : null)
                                .baseFare(baseFare)
                                .priceMultiplier(multiplier)
                                .cabinPrice(cabinPrice)
                                .tourPackageId(pkgId)
                                .packageName(pkgName)
                                .packagePrice(pkgPrice)
                                .itemTotal(itemTotal)
                                .build());
            }
        }

        BigDecimal subtotalAmount = subtotalCabin.add(subtotalPackage);
        BigDecimal portFeesAmount =
                portFeePerPassenger.multiply(BigDecimal.valueOf(request.getPassengers().size()));
        BigDecimal taxesAmount = subtotalAmount.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal taxesAndFeesAmount = portFeesAmount.add(taxesAmount);
        BigDecimal totalAmount = subtotalAmount.add(taxesAndFeesAmount);

        // Payment plan and cutoff rules
        PaymentPlan plan =
                request.getPaymentPlan() != null ? request.getPaymentPlan() : PaymentPlan.DEPOSIT;
        Instant now = Instant.now();
        Instant departure = cruise.getDepartureDatetime();
        int cutoffDays =
                (policy != null && policy.getFullPaymentCutoffDays() > 0) ? policy.getFullPaymentCutoffDays() : 7;
        if (departure != null && departure.isBefore(now.plus(Duration.ofDays(cutoffDays)))) {
            plan = PaymentPlan.FULL; // Within cutoff days, must pay in full
        }

        BigDecimal depositAmount;
        if (plan == PaymentPlan.FULL) {
            depositAmount = totalAmount;
        } else {
            BigDecimal depositRate =
                    (policy != null && policy.getDepositPercentage() != null)
                            ? policy.getDepositPercentage()
                            : BigDecimal.valueOf(0.30);
            depositAmount = totalAmount.multiply(depositRate).setScale(4, RoundingMode.HALF_UP);
        }

        int dueDays = (policy != null && policy.getFinalPaymentDueDays() > 0) ? policy.getFinalPaymentDueDays() : 7;
        Instant finalPaymentDueAt =
                departure != null ? departure.minus(Duration.ofDays(dueDays)) : now;
        if (finalPaymentDueAt.isBefore(now)) {
            finalPaymentDueAt = now;
        }

        String policyVersion = unified ? unifiedTerms.version() : policy.getPolicyVersion();
        String quotePayloadJson = writeQuoteData(lineItems);
        String fingerprint = unified
                ? unifiedQuoteFingerprint(cruise, unifiedTerms, totalAmount, quotePayloadJson)
                : generateQuoteFingerprint(cruise, policy, totalAmount);
        String quoteCode;
        try {
            quoteCode = businessCodeGenerator.next("QT");
        } catch (Exception ex) {
            quoteCode = "QT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        User bookedUser = null;
        String guestToken = null;
        if (principal != null && principal.getUserId() != null) {
            bookedUser = userRepository.findById(principal.getUserId()).orElse(null);
        } else {
            guestToken = UUID.randomUUID().toString();
        }

        BookingQuote quote =
                BookingQuote.builder()
                        .quoteCode(quoteCode)
                        .cruise(cruise)
                        .user(bookedUser)
                        .policyVersion(policyVersion)
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
                .policyVersion(quote.getPolicyVersion())
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
    public BookingQuoteResponse getQuote(
            UUID quoteId, CasmsPrincipal principal, String guestToken) {
        BookingQuote quote =
                bookingQuoteRepository
                        .findById(quoteId)
                        .orElseThrow(() -> new ResourceNotFoundException("BookingQuote", quoteId));
        if (BookingAgentCruiseScope.assignee(principal) != null) {
            requireAssignedCruise(quote.getCruise().getCruiseId(), principal);
        }

        boolean isStaff =
                principal != null
                        && principal.isStaff()
                        && (principal.hasAuthority("BOOKING_READ")
                                || principal.hasAuthority("ROLE_ADMIN"));

        if (quote.getUser() != null) {
            boolean isOwner =
                    principal != null && quote.getUser().getUserId().equals(principal.getUserId());
            if (!isOwner && !isStaff) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN_QUOTE_ACCESS",
                        "Bạn không có quyền truy cập vào báo giá của người dùng khác.");
            }
        } else {
            boolean tokenMatch =
                    guestToken != null
                            && !guestToken.isBlank()
                            && guestToken.trim().equals(quote.getGuestToken());
            if (!tokenMatch && !isStaff) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN_QUOTE_ACCESS",
                        "Khách vãng lai cần cung cấp X-Guest-Token hợp lệ để tra cứu chi tiết báo"
                            + " giá.");
            }
        }

        List<QuoteLineItem> lineItems = null;
        if (quote.getQuotePayloadJson() != null && !quote.getQuotePayloadJson().isBlank()) {
            try {
                lineItems =
                        objectMapper.readValue(
                                quote.getQuotePayloadJson(),
                                new TypeReference<List<QuoteLineItem>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize quote line items: {}", e.getMessage());
            }
        }

        String responseStatus = quote.getStatus();
        if ("ACTIVE".equals(responseStatus) && quote.getExpiresAt() != null && quote.isExpired()) {
            responseStatus = "EXPIRED";
        } else if ("ACTIVE".equals(responseStatus) && quote.getPolicyVersion() != null
                && quote.getPolicyVersion().startsWith("TERMS-")) {
            try {
                mainflow.requireBookable(quote.getCruise().getCruiseId());
                verifyUnifiedQuote(quote.getCruise(), quote, readQuoteLines(quote));
            } catch (BusinessException stale) {
                responseStatus = "INVALIDATED";
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
                .status(responseStatus)
                .guestToken(quote.getGuestToken())
                .build();
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse recoverBookingByIdempotencyKey(
            String idempotencyKey, CasmsPrincipal principal) {
        return recoverBookingByIdempotencyKey(idempotencyKey, principal, null);
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse recoverBookingByIdempotencyKey(
            String idempotencyKey, CasmsPrincipal principal, String guestToken) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key is required");
        }
        UUID actorId = principal != null ? principal.getUserId() : null;
        if (actorId == null && (guestToken == null || guestToken.isBlank())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "RECOVERY_OWNERSHIP_REQUIRED",
                    "Sign in or provide the original quote X-Guest-Token");
        }
        Optional<IdempotencyRecord> scopedRecord = idempotencyRecordRepository
                .findByActorIdAndOperationAndIdempotencyKey(actorId, "CREATE_BOOKING", idempotencyKey.trim());
        if (scopedRecord.isEmpty() && actorId != null && guestToken != null && !guestToken.isBlank()) {
            scopedRecord = idempotencyRecordRepository.findByActorIdAndOperationAndIdempotencyKey(
                    null, "CREATE_BOOKING", idempotencyKey.trim());
        }
        IdempotencyRecord record = scopedRecord
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RECOVERY_NOT_FOUND", "Booking recovery not found"));
        UUID bookingId = record.getResultingId();
        if (bookingId == null && record.getResponsePayload() != null) {
            try {
                bookingId = objectMapper.readValue(record.getResponsePayload(), BookingDetailResponse.class)
                        .getBookingId();
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new BusinessException(HttpStatus.CONFLICT, "RECOVERY_UNAVAILABLE",
                        "Stored booking recovery result is invalid");
            }
        }
        if (bookingId == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "RECOVERY_IN_PROGRESS",
                    "Booking outcome is not yet available; do not create another booking");
        }
        Booking booking = getBooking(bookingId);
        if (record.getActorId() != null) {
            validateAccess(booking, principal);
        } else {
            BookingQuote quote = booking.getQuote();
            if (quote == null || quote.getUser() != null || quote.getGuestToken() == null
                    || !MessageDigest.isEqual(quote.getGuestToken().getBytes(StandardCharsets.UTF_8),
                            guestToken.getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_RECOVERY",
                        "Guest ownership proof does not match");
            }
        }
        return mapToDetailResponse(booking);
    }

    private record UnifiedTerms(String version, String hash, BigDecimal portFee, BigDecimal taxRate) {}

    private UnifiedTerms approvedUnifiedTerms(UUID cruiseId) {
        var approved = departureSaleTermsService.approvedForBooking(cruiseId);
        return new UnifiedTerms("TERMS-" + approved.revision(), approved.submittedHash(),
                Objects.requireNonNullElse(approved.terms().portFee().value(), BigDecimal.ZERO),
                Objects.requireNonNullElse(approved.terms().tax().value(), BigDecimal.ZERO));
    }

    private String unifiedQuoteFingerprint(Cruise cruise, UnifiedTerms terms, BigDecimal total, String payload) {
        return CruiseMainflowService.hash(cruise.getCruiseId() + ":" + cruise.getRevision() + ":"
                + cruise.getOpenedForSaleAt() + ":" + terms.version() + ":" + terms.hash() + ":"
                + total.stripTrailingZeros().toPlainString() + ":" + payload);
    }

    private String writeQuoteData(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot persist booking commercial evidence", e);
        }
    }

    private List<QuoteLineItem> readQuoteLines(BookingQuote quote) {
        try {
            List<QuoteLineItem> lines = objectMapper.readValue(quote.getQuotePayloadJson(), new TypeReference<>() {});
            if (lines != null && !lines.isEmpty() && lines.stream().noneMatch(Objects::isNull)) return lines;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Cannot read quote commercial evidence for quote {}", quote.getQuoteId());
        }
        throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_PAYLOAD_MISSING",
                "Dữ liệu chi tiết báo giá không tồn tại hoặc bị lỗi.");
    }

    static List<QuoteLineItem> matchQuoteLines(List<BookingResourceSelection> requested, List<QuoteLineItem> lines) {
        if (requested.size() != lines.size()) throw quoteSelectionMismatch();
        Map<BookingResourceSelection, ArrayDeque<QuoteLineItem>> bySelection = new HashMap<>();
        for (QuoteLineItem line : lines)
            bySelection.computeIfAbsent(BookingResourceSelection.from(line), ignored -> new ArrayDeque<>()).add(line);
        List<QuoteLineItem> matched = new ArrayList<>();
        for (var selection : requested) {
            var candidates = bySelection.get(selection);
            if (candidates == null || candidates.isEmpty()) throw quoteSelectionMismatch();
            matched.add(candidates.removeFirst());
        }
        return matched;
    }

    private static BusinessException quoteSelectionMismatch() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "QUOTE_SELECTION_MISMATCH",
                "Lựa chọn cabin, nhóm hoặc phiên bản giá không khớp báo giá.");
    }

    private record OfferPrice(UUID offerId, UUID priceId) {}

    private List<QuoteLineItem> prepareFareLines(Cruise cruise, List<BookingResourceSelection> requested,
            Map<UUID, Cabin> cabins) {
        Map<OfferPrice, CruisePackagePurchaseSelection> selections = new HashMap<>();
        Map<String, String> groupResources = new HashMap<>();
        List<QuoteLineItem> lines = new ArrayList<>();
        for (var choice : requested) {
            var fare = selections.computeIfAbsent(new OfferPrice(choice.offerId(), choice.priceRevisionId()),
                    key -> cruisePackageService.preparePurchase(cruise.getCruiseId(), key.offerId(), key.priceId()));
            Cabin cabin = cabins.get(choice.cabinId());
            verifyFareCabin(cruise, cabin, fare);
            String resource = choice.mode() + ":" + (cabin != null ? cabin.getCabinId() : fare.cabinType());
            String previous = groupResources.putIfAbsent(choice.groupKey(), resource);
            if (previous != null && !previous.equals(resource)) throw quoteSelectionMismatch();
            lines.add(QuoteLineItem.builder()
                    .cabinId(choice.cabinId())
                    .cabinNumber(cabin == null ? null : cabin.getCabinNumber())
                    .deckNumber(cabin == null || cabin.getDeck() == null ? null : cabin.getDeck().getDeckNumber())
                    .cabinType(fare.cabinType())
                    .cabinSelectionMode(choice.mode())
                    .cabinGroupKey(choice.groupKey())
                    .fareSelection(fare)
                    .itemTotal(fare.amount())
                    .build());
        }
        return lines;
    }

    private void verifyFareCabin(Cruise cruise, Cabin cabin, CruisePackagePurchaseSelection selection) {
        if (selection == null || !selection.shipId().equals(cruise.getShip().getShipId())
                || selection.logicalCategoryId() == null || selection.cabinMappingId() == null
                || selection.physicalCabinIds().isEmpty()
                || (cabin != null && (cabin.getCabinType() != selection.cabinType()
                        || !selection.physicalCabinIds().contains(cabin.getCabinId())))) {
            throw new BusinessException(HttpStatus.CONFLICT, "FARE_CABIN_MAPPING_MISMATCH",
                    "Cabin hoặc hạng giữ chỗ không thuộc gói du thuyền đã chọn.");
        }
    }

    private void verifyUnifiedQuote(Cruise cruise, BookingQuote quote, List<QuoteLineItem> lines) {
        UnifiedTerms terms = approvedUnifiedTerms(cruise.getCruiseId());
        if (!terms.version().equals(quote.getPolicyVersion())
                || !unifiedQuoteFingerprint(cruise, terms, quote.getTotalAmount(), quote.getQuotePayloadJson())
                        .equals(quote.getFingerprint())) {
            throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_POLICY_MISMATCH",
                    "Cấu hình thương mại đã thay đổi. Vui lòng lấy báo giá mới.");
        }
        Map<OfferPrice, CruisePackagePurchaseSelection> current = new HashMap<>();
        for (var line : lines) {
            var captured = line.getFareSelection();
            if (captured == null) throw quoteSelectionMismatch();
            var live = current.computeIfAbsent(new OfferPrice(captured.offerId(), captured.priceRevisionId()),
                    key -> cruisePackageService.preparePurchase(cruise.getCruiseId(), key.offerId(), key.priceId()));
            if (!captured.equals(live))
                throw new BusinessException(HttpStatus.CONFLICT, "FARE_QUOTED_PRICE_STALE",
                        "Gói hoặc quyền lợi trong báo giá đã thay đổi. Vui lòng lấy báo giá mới.");
        }
    }

    private String generateQuoteFingerprint(
            Cruise cruise, CruisePricingPolicy policy, BigDecimal total) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw =
                    cruise.getCruiseId()
                            + ":"
                            + cruise.getRevision()
                            + ":"
                            + (policy != null ? policy.getPolicyVersion() : "PKG")
                            + ":"
                            + total;
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
        return cruiseRepository
                .findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
    }

    private Booking getBooking(UUID bookingId) {
        return bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    private User getUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Tra cứu đơn đặt chỗ dành cho khách vãng lai (không cần đăng nhập, dùng bookingReference +
     * email/số điện thoại).
     */
    @Transactional(readOnly = true)
    public BookingDetailResponse lookupBooking(String reference, String email, String phoneNumber) {
        if (reference == null || reference.isBlank()) {
            throw new BusinessException(
                    "REFERENCE_REQUIRED", "Booking reference is required for lookup");
        }
        if ((email == null || email.isBlank()) && (phoneNumber == null || phoneNumber.isBlank())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "LOOKUP_CREDENTIAL_REQUIRED",
                    "Email or phone number is required for booking lookup");
        }
        Booking booking =
                bookingRepository
                        .findByBookingReference(reference.trim())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Booking reference not found: " + reference));

        boolean matched = false;
        if (email != null && !email.isBlank()) {
            String normEmail = email.trim().toLowerCase();
            if (booking.getBookedByUser() != null
                    && normEmail.equalsIgnoreCase(booking.getBookedByUser().getEmail())) {
                matched = true;
            }
        }
        if (!matched && (email != null || phoneNumber != null)) {
            List<BookingPassenger> passengers =
                    bookingPassengerRepository.findByBookingBookingId(booking.getBookingId());
            for (BookingPassenger bp : passengers) {
                if (bp.getPassenger() != null) {
                    if (email != null
                            && !email.isBlank()
                            && email.trim().equalsIgnoreCase(bp.getPassenger().getEmail())) {
                        matched = true;
                        break;
                    }
                    if (phoneNumber != null
                            && !phoneNumber.isBlank()
                            && phoneNumber
                                    .trim()
                                    .equalsIgnoreCase(bp.getPassenger().getPhoneNumber())) {
                        matched = true;
                        break;
                    }
                }
            }
        }

        if (!matched) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "LOOKUP_MISMATCH",
                    "Email or phone number does not match this booking reference");
        }

        return mapToDetailResponse(booking);
    }

    private User resolveOrCreateGuestUser(PassengerBookingItemRequest primaryPassenger) {
        String email = primaryPassenger.getEmail();
        if (email != null && !email.isBlank()) {
            String normEmail = email.trim().toLowerCase(Locale.ROOT);
            bookingEmailService.lockContact(normEmail);
            Optional<User> existingUser = userRepository.findByNormalizedEmail(normEmail);
            if (existingUser.isPresent()) {
                if (!existingUser.get().hasRole(Role.PASSENGER) || existingUser.get().isStaff()
                        || existingUser.get().isAdmin() || existingUser.get().getStatus() != UserStatus.ACTIVE) {
                    throw new BusinessException(HttpStatus.CONFLICT, "PASSENGER_ACCOUNT_REQUIRED",
                            "Contact email belongs to a non-passenger account");
                }
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
            User saved = userRepository.save(guestUser);
            userRepository.flush();
            bookingEmailService.markProvisioned(saved);
            return saved;
        }

        // Trường hợp không có email: tạo tài khoản khách vãng lai
        User anonymousGuest = new User();
        String anonUsername =
                "guest_"
                        + System.currentTimeMillis()
                        + "_"
                        + UUID.randomUUID().toString().substring(0, 4);
        anonymousGuest.setUsername(anonUsername);
        anonymousGuest.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        anonymousGuest.setStatus(UserStatus.ACTIVE);
        anonymousGuest.setRoles(EnumSet.of(Role.PASSENGER));
        return userRepository.save(anonymousGuest);
    }

    private BigDecimal verifiedNetPaid(Booking booking) {
        BigDecimal paid = paymentRepository.findForBookingReadOnly(booking.getBookingId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed && p.getConfirmedAt() != null
                        && p.getConfirmedAmount() != null && p.getConfirmedAmount().signum() > 0
                        && p.getOperationType() == PaymentOperationType.Collect
                        && paymentBelongsToBooking(p, booking.getBookingId()) && paymentHasSettlementEvidence(p)
                        && p.getCurrency() != null && p.getCurrency().trim().equalsIgnoreCase(booking.getCurrency()))
                .map(Payment::getConfirmedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = refundRepository.findForBooking(booking.getBookingId()).stream()
                .filter(r -> r.getStatus() == RefundStatus.Completed && r.getCompletedAt() != null
                        && r.getApprovedAmount() != null && r.getApprovedAmount().signum() > 0
                        && r.getCurrency() != null && r.getCurrency().trim().equalsIgnoreCase(booking.getCurrency())
                        && r.getProcessorOrReceiptReference() != null && !r.getProcessorOrReceiptReference().isBlank())
                .map(Refund::getApprovedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return paid.subtract(refunds).max(BigDecimal.ZERO);
    }

    public void requireAssignedCruise(UUID cruiseId, CasmsPrincipal principal) {
        BookingAgentCruiseScope.requireAssigned(cruiseRepository, cruiseId, BookingAgentCruiseScope.assignee(principal));
    }

    private void validateAccess(Booking booking, CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication required");
        }
        if (BookingAgentCruiseScope.assignee(principal) != null) {
            requireAssignedCruise(booking.getCruise().getCruiseId(), principal);
        }
        boolean isStaff =
                principal.isAdmin()
                        || principal.getAuthorities().stream()
                                .anyMatch(
                                        a ->
                                                a.getAuthority().equals("ROLE_ADMIN")
                                                        || a.getAuthority()
                                                                .equals("ROLE_BOOKING_AGENT")
                                                        || a.getAuthority()
                                                                .equals("ROLE_FINANCE_OFFICER")
                                                        || a.getAuthority()
                                                                .equals("ROLE_OPERATIONS_MANAGER")
                                                        || (principal.isStaff()
                                                                && a.getAuthority()
                                                                        .equals("BOOKING_READ")));

        if (!isStaff && !booking.getBookedByUser().getUserId().equals(principal.getUserId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "You can only view or manage your own bookings");
        }
    }

    /**
     * Xuất bảng hợp đồng chuyến đi định dạng HTML in A4 cho nhân viên hoặc hành khách đã đăng nhập.
     * Hợp đồng không lưu file/blob trên database mà trích xuất động từ thông tin hành khách đã đăng ký.
     */
    @Transactional(readOnly = true)
    public String generateContractHtml(UUID bookingId, CasmsPrincipal principal) {
        Booking booking = getBooking(bookingId);
        validateAccess(booking, principal);
        BookingDetailResponse detail = mapToDetailResponse(booking);
        return buildContractHtml(detail);
    }

    /**
     * Xuất bảng hợp đồng chuyến đi định dạng HTML in A4 cho khách vãng lai (không cần đăng nhập).
     * Xác thực bằng mã đặt chỗ + email hoặc số điện thoại người đặt.
     */
    @Transactional(readOnly = true)
    public String generateContractHtmlByLookup(String reference, String email, String phoneNumber) {
        BookingDetailResponse detail = lookupBooking(reference, email, phoneNumber);
        return buildContractHtml(detail);
    }

    private String buildContractHtml(BookingDetailResponse detail) {
        String bookingRef = escapeHtml(detail.getBookingReference());
        String contractRef =
                escapeHtml(
                        detail.getContractReference() != null
                                ? detail.getContractReference()
                                : "CTR-" + bookingRef);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"vi\">\n");
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Hợp Đồng Vận Chuyển Hải Trình - ").append(bookingRef).append("</title>\n");
        html.append("<style>\n").append(buildContractStyles()).append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        html.append("<div class=\"no-print\">\n")
                .append("  <div class=\"toolbar-info\">\n")
                .append("    <strong>HỆ THỐNG ĐẶT VÉ HẢI TRÌNH CASMS CRUISE</strong>\n")
                .append("    <span style=\"opacity: 0.85; margin-left: 12px;\">Bản hợp đồng được tổng hợp tự động từ hồ sơ hành khách đã đăng ký (Không lưu trữ file dư thừa trên CSDL).</span>\n")
                .append("  </div>\n")
                .append("  <div class=\"toolbar-actions\">\n")
                .append("    <button class=\"btn-print\" onclick=\"window.print()\">🖨️ In Hợp Đồng / Xuất PDF</button>\n")
                .append("  </div>\n")
                .append("</div>\n");

        boolean isInternational = Boolean.TRUE.equals(detail.getIsInternational())
                || "INTERNATIONAL".equalsIgnoreCase(detail.getPlannedScope());

        html.append("<div class=\"contract-paper\">\n");
        html.append(buildContractHeader(detail, contractRef, bookingRef, isInternational));
        html.append(buildPartiesSection(detail));
        html.append(buildCruiseSection(detail));
        html.append(buildPassengerTable(detail.getPassengers()));
        html.append(buildFinancialAndInstallmentSection(detail));
        html.append(buildCommitmentsSection(isInternational));
        html.append(buildNotesAndAdvisoriesSection(isInternational));
        html.append(buildTermsAndSignatures(detail, contractRef));
        html.append("</div>\n");

        html.append("</body>\n</html>");
        return html.toString();
    }

    private String buildContractHeader(
            BookingDetailResponse detail, String contractRef, String bookingRef, boolean isInternational) {
        boolean isAccepted = detail.getContractStatus() == BookingContractStatus.Accepted;
        String statusLabel = isAccepted ? "ĐÃ KÝ KẾT &amp; XÁC NHẬN" : "BẢN THẢO CHỜ XÁC NHẬN";
        String statusClass = isAccepted ? "badge-success" : "badge-warning";

        String contractTitle = isInternational
                ? "HỢP ĐỒNG DỊCH VỤ VẬN CHUYỂN HẢI TRÌNH DU THUYỀN QUỐC TẾ"
                : "HỢP ĐỒNG DỊCH VỤ VẬN CHUYỂN HÀNH KHÁCH ĐƯỜNG BIỂN NỘI ĐỊA";
        String contractSubTitle = isInternational
                ? "(V/v Cung cấp dịch vụ vận chuyển hành khách &amp; lưu trú hải trình quốc tế rời lãnh hải Việt Nam)"
                : "(V/v Cung cấp dịch vụ vận chuyển hành khách &amp; lưu trú hải trình du thuyền trong lãnh hải Việt Nam)";
        String scopeBadge = isInternational
                ? "<span class=\"badge badge-scope-international\">🌐 HẢI TRÌNH QUỐC TẾ (INTERNATIONAL)</span>\n"
                : "<span class=\"badge badge-scope-domestic\">🇻🇳 HẢI TRÌNH NỘI ĐỊA (DOMESTIC)</span>\n";

        return "<div class=\"contract-header\">\n"
                + "  <div class=\"header-left\">\n"
                + "    <strong>HÃNG TÀU DU LỊCH CAO CẤP CASMS</strong><br>\n"
                + "    <span>Hệ thống Quản lý Hải trình &amp; Lưu trú Du thuyền</span><br>\n"
                + "    <span>Hotline: 1900-CASMS (22767) | Email: support@casms.vn</span><br>\n"
                + "    <span>Địa chỉ: Cảng Khách Quốc Tế Tuần Châu, TP. Hạ Long</span>\n"
                + "  </div>\n"
                + "  <div class=\"header-right\">\n"
                + "    <strong>CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</strong><br>\n"
                + "    <span style=\"font-weight: 600;\">Độc lập - Tự do - Hạnh phúc</span><br>\n"
                + "    <span>-------o0o-------</span>\n"
                + "  </div>\n"
                + "</div>\n"
                + "<div class=\"contract-title\">\n"
                + "  <h1>" + contractTitle + "</h1>\n"
                + "  <p>" + contractSubTitle + "</p>\n"
                + "  <div class=\"meta-badges\">\n"
                + "    <span class=\"badge\">Mã Hợp Đồng: <strong>"
                + contractRef
                + "</strong></span>\n"
                + "    <span class=\"badge\">Mã Đặt Chỗ: <strong>"
                + bookingRef
                + "</strong></span>\n"
                + scopeBadge
                + "    <span class=\"badge "
                + statusClass
                + "\">"
                + statusLabel
                + "</span>\n"
                + "  </div>\n"
                + "</div>\n";
    }

    private String buildPartiesSection(BookingDetailResponse detail) {
        List<BookingPassengerResponse> passengers = detail.getPassengers();
        BookingPassengerResponse primary =
                (passengers != null && !passengers.isEmpty()) ? passengers.get(0) : null;

        String repName =
                primary != null && primary.getFullName() != null && !primary.getFullName().isBlank()
                        ? escapeHtml(primary.getFullName())
                        : (detail.getBookedByUsername() != null && !detail.getBookedByUsername().isBlank()
                                ? escapeHtml(detail.getBookedByUsername())
                                : "Đại diện hành khách");
        String repDoc =
                (primary != null && primary.getDocumentNumber() != null)
                        ? escapeHtml(primary.getDocumentNumber())
                        : "Theo hồ sơ lưu";
        String repDob =
                (primary != null && primary.getDateOfBirth() != null)
                        ? formatDate(primary.getDateOfBirth())
                        : "Chưa cập nhật";
        String repNationality =
                (primary != null && primary.getNationality() != null)
                        ? escapeHtml(primary.getNationality())
                        : "Việt Nam";
        String repPhone =
                (primary != null && primary.getPhoneNumber() != null)
                        ? escapeHtml(primary.getPhoneNumber())
                        : "Chưa cập nhật";
        String repEmail =
                (primary != null && primary.getEmail() != null)
                        ? escapeHtml(primary.getEmail())
                        : "Chưa cập nhật";

        return "<div class=\"section\">\n"
                + "  <div class=\"section-title\">THÔNG TIN CÁC BÊN THAM GIA KÝ KẾT</div>\n"
                + "  <div class=\"two-col\">\n"
                + "    <div class=\"party-box\">\n"
                + "      <h4>BÊN A: ĐƠN VỊ VẬN CHUYỂN (CASMS CRUISE)</h4>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Doanh nghiệp:</span>"
                + " CÔNG TY CỔ PHẦN HẢI TRÌNH DU THUYỀN CASMS</div>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Đại diện bởi:</span>"
                + " Ban Quản lý Khai thác Hải trình CASMS</div>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Du thuyền:</span> "
                + escapeHtml(
                        detail.getShipName() != null ? detail.getShipName() : "Đội tàu CASMS")
                + "</div>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Tổng đài"
                + " CSKH:</span> 1900-CASMS / booking@casms.vn</div>\n"
                + "    </div>\n"
                + "    <div class=\"party-box\">\n"
                + "      <h4>BÊN B: ĐẠI DIỆN HÀNH KHÁCH / BÊN ĐẶT CHỖ</h4>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Người đại diện:</span>"
                + " <strong>"
                + repName
                + "</strong></div>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Số CCCD/Hộ"
                + " chiếu:</span> "
                + repDoc
                + "</div>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Ngày sinh / QT:</span>"
                + " "
                + repDob
                + " | "
                + repNationality
                + "</div>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Số điện thoại:</span> "
                + repPhone
                + "</div>\n"
                + "      <div class=\"info-row\"><span class=\"info-label\">Email liên hệ:</span> "
                + repEmail
                + "</div>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</div>\n";
    }

    private String buildCruiseSection(BookingDetailResponse detail) {
        String cruiseName =
                escapeHtml(
                        detail.getCruiseName() != null
                                ? detail.getCruiseName()
                                : "Hải trình Du thuyền CASMS");
        String cruiseCode = escapeHtml(detail.getCruiseCode() != null ? detail.getCruiseCode() : "-");
        String shipName = escapeHtml(detail.getShipName() != null ? detail.getShipName() : "-");
        String departure = formatInstant(detail.getDepartureDatetime());
        String returnTime = formatInstant(detail.getReturnDatetime());

        return "<div class=\"section\">\n"
                + "  <div class=\"section-title\">ĐIỀU 1: THÔNG TIN HẢI TRÌNH &amp; DU THUYỀN VẬN"
                + " HÀNH</div>\n"
                + "  <table class=\"data-table\">\n"
                + "    <tr>\n"
                + "      <th style=\"width: 25%;\">Tên Chuyến Hải Trình</th>\n"
                + "      <td><strong>"
                + cruiseName
                + "</strong> (Mã chuyến: "
                + cruiseCode
                + ")</td>\n"
                + "      <th style=\"width: 20%;\">Du Thuyền Vận Hành</th>\n"
                + "      <td><strong>"
                + shipName
                + "</strong></td>\n"
                + "    </tr>\n"
                + "    <tr>\n"
                + "      <th>Thời Gian Khởi Hành</th>\n"
                + "      <td>"
                + departure
                + "</td>\n"
                + "      <th>Thời Gian Kết Thúc</th>\n"
                + "      <td>"
                + returnTime
                + "</td>\n"
                + "    </tr>\n"
                + "  </table>\n"
                + "</div>\n";
    }

    private String buildPassengerTable(List<BookingPassengerResponse> passengers) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section\">\n");
        sb.append(
                "  <div class=\"section-title\">ĐIỀU 2: DANH SÁCH HÀNH KHÁCH &amp; PHÂN BỔ BUỒNG"
                    + " PHÒNG (CABIN)</div>\n");
        sb.append(
                        "  <p style=\"font-size: 12px; color: #475569; margin: 4px 0 8px 0;"
                            + " font-style: italic;\">")
                .append(
                        "* Bảng thông tin được kết xuất trực tiếp từ dữ liệu hành khách đã đăng ký"
                            + " trước đó của đơn đặt chỗ:")
                .append("</p>\n");
        sb.append("  <table class=\"data-table\">\n");
        sb.append("    <thead>\n");
        sb.append("      <tr>\n");
        sb.append("        <th style=\"width: 35px;\">STT</th>\n");
        sb.append("        <th>Họ và Tên Hành Khách</th>\n");
        sb.append("        <th style=\"width: 90px;\">Ngày Sinh</th>\n");
        sb.append("        <th style=\"width: 70px;\">Quốc Tịch</th>\n");
        sb.append("        <th>CCCD / Hộ Chiếu</th>\n");
        sb.append("        <th>Số Phòng (Cabin)</th>\n");
        sb.append("        <th>Hạng Phòng</th>\n");
        sb.append("        <th>Gói Dịch Vụ</th>\n");
        sb.append("      </tr>\n");
        sb.append("    </thead>\n");
        sb.append("    <tbody>\n");

        if (passengers == null || passengers.isEmpty()) {
            sb.append(
                    "      <tr><td colspan=\"8\" class=\"center\">Chưa có thông tin hành"
                        + " khách</td></tr>\n");
        } else {
            int stt = 1;
            for (BookingPassengerResponse p : passengers) {
                String cabinStr =
                        p.getCabinNumber() != null
                                ? ("Phòng "
                                        + escapeHtml(p.getCabinNumber())
                                        + (p.getDeckNumber() != null
                                                ? " (Tầng " + p.getDeckNumber() + ")"
                                                : ""))
                                : "Chờ gán phòng";
                String cabinTypeStr =
                        p.getCabinType() != null
                                ? formatCabinType(p.getCabinType())
                                : (p.getRequestedCabinCategory() != null
                                        ? escapeHtml(p.getRequestedCabinCategory())
                                        : "Tiêu chuẩn");
                String pkgStr =
                        p.getTourPackageName() != null
                                ? escapeHtml(p.getTourPackageName())
                                : "Cơ bản";

                String passName =
                        p.getFullName() != null && !p.getFullName().isBlank()
                                ? escapeHtml(p.getFullName())
                                : ("Hành khách " + stt);
                sb.append("      <tr>\n");
                sb.append("        <td class=\"center\">").append(stt++).append("</td>\n");
                sb.append("        <td><strong>")
                        .append(passName)
                        .append("</strong></td>\n");
                sb.append("        <td class=\"center\">")
                        .append(formatDate(p.getDateOfBirth()))
                        .append("</td>\n");
                sb.append("        <td class=\"center\">")
                        .append(escapeHtml(p.getNationality() != null ? p.getNationality() : "VNM"))
                        .append("</td>\n");
                sb.append("        <td>")
                        .append(
                                escapeHtml(
                                        p.getDocumentNumber() != null
                                                ? p.getDocumentNumber()
                                                : "-"))
                        .append("</td>\n");
                sb.append("        <td>").append(cabinStr).append("</td>\n");
                sb.append("        <td>").append(cabinTypeStr).append("</td>\n");
                sb.append("        <td>").append(pkgStr).append("</td>\n");
                sb.append("      </tr>\n");
            }
        }

        sb.append("    </tbody>\n");
        sb.append("  </table>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private String buildFinancialAndInstallmentSection(BookingDetailResponse detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section\">\n");
        sb.append(
                "  <div class=\"section-title\">ĐIỀU 3: GIÁ TRỊ HỢP ĐỒNG &amp; LỊCH TRÌNH THANH"
                    + " TOÁN</div>\n");
        sb.append("  <table class=\"data-table\">\n");
        sb.append("    <tr>\n");
        sb.append("      <th style=\"width: 25%;\">Tổng Giá Trị Hợp Đồng</th>\n");
        sb.append("      <td><strong style=\"color: #1e3a8a; font-size: 14px;\">")
                .append(formatMoney(detail.getAgreedFareAmount(), detail.getCurrency()))
                .append("</strong></td>\n");
        sb.append("      <th style=\"width: 20%;\">Tiền Cọc Yêu Cầu</th>\n");
        sb.append("      <td>")
                .append(formatMoney(detail.getRequiredDepositAmount(), detail.getCurrency()))
                .append("</td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <th>Hạn Chót Thanh Toán</th>\n");
        sb.append("      <td colspan=\"3\">")
                .append(formatInstant(detail.getFinalPaymentDueAt()))
                .append("</td>\n");
        sb.append("    </tr>\n");
        sb.append("  </table>\n");

        List<InstallmentResponse> installments = detail.getInstallments();
        if (installments != null && !installments.isEmpty()) {
            sb.append("  <table class=\"data-table\" style=\"margin-top: 8px;\">\n");
            sb.append("    <thead>\n");
            sb.append("      <tr>\n");
            sb.append("        <th style=\"width: 45px;\">Đợt</th>\n");
            sb.append("        <th>Nội Dung Thanh Toán</th>\n");
            sb.append("        <th style=\"width: 140px;\">Số Tiền</th>\n");
            sb.append("        <th style=\"width: 130px;\">Hạn Thanh Toán</th>\n");
            sb.append("        <th style=\"width: 110px;\">Trạng Thái</th>\n");
            sb.append("      </tr>\n");
            sb.append("    </thead>\n");
            sb.append("    <tbody>\n");
            for (InstallmentResponse inst : installments) {
                String statusStr;
                if (inst.getStatus() == InstallmentStatus.Cancelled) {
                    statusStr = "<span style=\"color:#dc2626;\">Đã hủy</span>";
                } else if (detail.getBookingStatus() == BookingStatus.Confirmed
                        && (inst.getSequenceNo() != null && inst.getSequenceNo() == 1)) {
                    statusStr =
                            "<span style=\"color:#166534; font-weight:bold;\">✓ Đã thanh toán"
                                    + " (Cọc)</span>";
                } else {
                    statusStr = "<span style=\"color:#854d0e;\">Đã lên lịch</span>";
                }
                sb.append("      <tr>\n");
                sb.append("        <td class=\"center\">")
                        .append(inst.getSequenceNo())
                        .append("</td>\n");
                sb.append("        <td>").append(escapeHtml(inst.getDescription())).append("</td>\n");
                sb.append("        <td class=\"right\"><strong>")
                        .append(formatMoney(inst.getRequiredAmount(), inst.getCurrency()))
                        .append("</strong></td>\n");
                sb.append("        <td class=\"center\">")
                        .append(formatInstant(inst.getDueAt()))
                        .append("</td>\n");
                sb.append("        <td class=\"center\">").append(statusStr).append("</td>\n");
                sb.append("      </tr>\n");
            }
            sb.append("    </tbody>\n");
            sb.append("  </table>\n");
        }

        sb.append("</div>\n");
        return sb.toString();
    }

    private String buildCommitmentsSection(boolean isInternational) {
        if (isInternational) {
            return "<div class=\"section\">\n"
                    + "  <div class=\"section-title\">ĐIỀU 4: CAM KẾT CHUNG GIỮA HAI BÊN (HẢI TRÌNH QUỐC TẾ)</div>\n"
                    + "  <div class=\"commit-grid\">\n"
                    + "    <div class=\"commit-box\">\n"
                    + "      <h5>1. CAM KẾT CỦA BÊN A (HÃNG TÀU CASMS CRUISE)</h5>\n"
                    + "      <ul class=\"commit-list\">\n"
                    + "        <li><strong>Chuẩn mực hàng hải quốc tế:</strong> Du thuyền vận hành đạt quy chuẩn đăng kiểm quốc tế, tuân thủ tuyệt đối các Công ước SOLAS (An toàn sinh mạng), MARPOL (Bảo vệ môi trường biển), MLC 2006 và luật hàng hải của các quốc gia sở tại ghé cảng.</li>\n"
                    + "        <li><strong>Đúng tiện nghi &amp; hải trình:</strong> Bố trí đúng buồng phòng (cabin) đã ký kết, thực hiện hải trình quốc tế theo đúng lịch trình đã được phê duyệt.</li>\n"
                    + "        <li><strong>Bảo hiểm du lịch quốc tế:</strong> Cung cấp bảo hiểm du lịch quốc tế toàn diện cho hành khách (bao gồm bảo hiểm tai nạn, ốm đau ở nước ngoài và cứu trợ y tế khẩn cấp/hồi hương).</li>\n"
                    + "        <li><strong>Hỗ trợ thủ tục xuất nhập cảnh:</strong> Phối hợp với đại lý cảng và cơ quan biên phòng hỗ trợ hành khách làm thủ tục xuất nhập cảnh nhanh chóng, an toàn.</li>\n"
                    + "        <li><strong>Bảo mật &amp; Bất khả kháng:</strong> Bảo mật thông tin cá nhân; trong trường hợp thời tiết giông bão, dịch bệnh hoặc lệnh cấm cảng quốc tế vì an toàn, Bên A cam kết hỗ trợ đổi cảng dự phòng, bảo lưu hoặc hoàn trả chi phí thỏa đáng.</li>\n"
                    + "      </ul>\n"
                    + "    </div>\n"
                    + "    <div class=\"commit-box\">\n"
                    + "      <h5>2. CAM KẾT CỦA BÊN B (ĐẠI DIỆN HÀNH KHÁCH / ĐOÀN)</h5>\n"
                    + "      <ul class=\"commit-list\">\n"
                    + "        <li><strong>Hồ sơ xuất nhập cảnh hợp lệ:</strong> Chịu trách nhiệm hoàn toàn về tính hợp pháp của Hộ chiếu (còn hạn trên 6 tháng) và Thị thực (Visa) hợp lệ vào các quốc gia trong hải trình.</li>\n"
                    + "        <li><strong>Nghĩa vụ tài chính:</strong> Thanh toán đầy đủ cước phí và các phụ phí hải trình quốc tế (thuế cảng nước ngoài, phụ phí nhiên liệu nếu có) đúng thời hạn.</li>\n"
                    + "        <li><strong>Tuân thủ luật pháp quốc tế:</strong> Chấp hành nghiêm túc pháp luật của các quốc gia du thuyền cập cảng, nội quy du thuyền và mệnh lệnh an toàn của Thuyền trưởng.</li>\n"
                    + "        <li><strong>Tinh thần hợp tác:</strong> Cam kết hợp tác thiện chí, tôn trọng văn hóa nước sở tại và giải quyết mọi tranh chấp qua thương lượng văn minh.</li>\n"
                    + "      </ul>\n"
                    + "    </div>\n"
                    + "  </div>\n"
                    + "</div>\n";
        } else {
            return "<div class=\"section\">\n"
                    + "  <div class=\"section-title\">ĐIỀU 4: CAM KẾT CHUNG GIỮA HAI BÊN (HẢI TRÌNH NỘI ĐỊA)</div>\n"
                    + "  <div class=\"commit-grid\">\n"
                    + "    <div class=\"commit-box\">\n"
                    + "      <h5>1. CAM KẾT CỦA BÊN A (HÃNG TÀU CASMS CRUISE)</h5>\n"
                    + "      <ul class=\"commit-list\">\n"
                    + "        <li><strong>An toàn đăng kiểm quốc gia:</strong> Du thuyền vận hành đạt chuẩn kỹ thuật đăng kiểm thủy nội địa và hàng hải Việt Nam, tuân thủ quy định PCCC và SOLAS.</li>\n"
                    + "        <li><strong>Đúng chất lượng dịch vụ:</strong> Bố trí đúng buồng phòng (cabin), thực đơn và các tiện ích trên du thuyền theo thỏa thuận tại Điều 1 và Điều 2.</li>\n"
                    + "        <li><strong>Bảo hiểm hành khách nội địa:</strong> 100% hành khách được thụ hưởng quyền lợi bảo hiểm du lịch đường biển theo quy định của pháp luật Việt Nam.</li>\n"
                    + "        <li><strong>Bảo mật thông tin:</strong> Cam kết bảo mật dữ liệu hành khách, chỉ cung cấp cho Cảng vụ Hàng hải và Bộ đội Biên phòng theo danh sách khai báo rời bến.</li>\n"
                    + "        <li><strong>Hỗ trợ thiên tai, bão gió:</strong> Khi Cảng vụ cấm biển vì thời tiết xấu, cam kết thông báo ngay và hỗ trợ bảo lưu lịch trình hoặc hoàn tiền thỏa đáng.</li>\n"
                    + "      </ul>\n"
                    + "    </div>\n"
                    + "    <div class=\"commit-box\">\n"
                    + "      <h5>2. CAM KẾT CỦA BÊN B (ĐẠI DIỆN HÀNH KHÁCH / ĐOÀN)</h5>\n"
                    + "      <ul class=\"commit-list\">\n"
                    + "        <li><strong>Thông tin nhân thân chuẩn xác:</strong> Cung cấp danh sách hành khách chính xác (Họ tên, ngày sinh, số CCCD/Hộ chiếu) để khai báo xuất bến theo quy định an ninh cảng.</li>\n"
                    + "        <li><strong>Nghĩa vụ thanh toán:</strong> Thực hiện thanh toán đúng hạn và đủ số tiền theo tiến độ thỏa thuận tại Điều 3.</li>\n"
                    + "        <li><strong>Tuân thủ quy chế du thuyền:</strong> Chấp hành nội quy trên tàu, hướng dẫn của thủy thủ đoàn, giữ gìn vệ sinh và bảo vệ môi trường biển Việt Nam.</li>\n"
                    + "        <li><strong>Hợp tác hòa nhã:</strong> Giải quyết các phát sinh trên tinh thần tôn trọng, không để tranh chấp làm ảnh hưởng tới không khí nghỉ dưỡng của chuyến đi.</li>\n"
                    + "      </ul>\n"
                    + "    </div>\n"
                    + "  </div>\n"
                    + "</div>\n";
        }
    }

    private String buildNotesAndAdvisoriesSection(boolean isInternational) {
        if (isInternational) {
            return "<div class=\"section\">\n"
                    + "  <div class=\"section-title\">ĐIỀU 5: CÁC LƯU Ý QUAN TRỌNG KHI THAM GIA HẢI TRÌNH QUỐC TẾ</div>\n"
                    + "  <div class=\"advisory-card international\">\n"
                    + "    <div class=\"advisory-item\">🛂 <strong>1. Hộ chiếu &amp; Thị thực (Visa) bắt buộc:</strong> Bắt buộc mang theo bản gốc <strong>Hộ chiếu (Passport) còn hạn trên 06 tháng</strong> tính từ ngày kết thúc chuyến đi và còn tối thiểu 2-4 trang trống. <em>Hành khách không được dùng CCCD để xuất cảnh</em>. Quý khách tự chịu trách nhiệm về Visa nhập cảnh các nước trong hải trình và Visa tái nhập cảnh Việt Nam (đối với khách mang hộ chiếu nước ngoài).</div>\n"
                    + "    <div class=\"advisory-item\">⏰ <strong>2. Thời gian làm thủ tục xuất cảnh:</strong> Quý khách phải có mặt tại Nhà ga Cảng biển Quốc tế trước giờ khởi hành tối thiểu <strong>180 phút (03 tiếng)</strong> để hoàn tất thủ tục xuất cảnh và kiểm tra an ninh quốc tế (ISPS Code). Cổng soát vé và thủ tục xuất cảnh sẽ đóng trước giờ khởi hành <strong>60 phút</strong>.</div>\n"
                    + "    <div class=\"advisory-item\">💵 <strong>3. Ngoại tệ &amp; Khai báo Hải quan:</strong> Trường hợp mang theo tiền mặt trên <strong>5.000 USD</strong> (hoặc ngoại tệ tương đương trên 15.000.000 VNĐ), quý khách bắt buộc phải khai báo hải quan cửa khẩu cảng theo quy định của Ngân hàng Nhà nước. Khuyến khích sử dụng thẻ tín dụng quốc tế (Visa/Mastercard) để chi tiêu trên tàu.</div>\n"
                    + "    <div class=\"advisory-item\">🚫 <strong>4. Kiểm dịch nông sản &amp; Hành lý cấm:</strong> Nghiêm cấm mang theo nông sản tươi sống, hạt giống, hoa quả tươi, thịt/thực phẩm chưa qua chế biến vào các quốc gia sở tại theo luật kiểm dịch sinh học quốc tế. Cấm vũ khí, chất cháy nổ, ma túy, flycam chưa được cấp phép.</div>\n"
                    + "    <div class=\"advisory-item\">🏥 <strong>5. Bảo hiểm y tế quốc tế &amp; SOLAS:</strong> Quý khách được cấp chứng thư bảo hiểm du lịch quốc tế hỗ trợ y tế nước ngoài. Tất cả hành khách bắt buộc tham gia buổi diễn tập an toàn cứu sinh (Muster Drill) trên tàu trước giờ rời cảng.</div>\n"
                    + "  </div>\n"
                    + "</div>\n";
        } else {
            return "<div class=\"section\">\n"
                    + "  <div class=\"section-title\">ĐIỀU 5: CÁC LƯU Ý QUAN TRỌNG KHI THAM GIA HẢI TRÌNH NỘI ĐỊA</div>\n"
                    + "  <div class=\"advisory-card domestic\">\n"
                    + "    <div class=\"advisory-item\">🪪 <strong>1. Giấy tờ tùy thân hợp lệ:</strong> Hành khách người lớn phải mang theo bản gốc <strong>CCCD / Căn cước</strong> hoặc Hộ chiếu còn hạn. Trẻ em dưới 14 tuổi bắt buộc mang theo bản gốc <strong>Giấy khai sinh</strong> (hoặc bản sao trích lục/công chứng hợp lệ) và phải có người giám hộ đi kèm.</div>\n"
                    + "    <div class=\"advisory-item\">⏰ <strong>2. Thời gian tập trung &amp; Check-in:</strong> Quý khách có mặt tại Nhà ga đón khách trước giờ tàu khởi hành tối thiểu <strong>90 phút</strong> để làm thủ tục nhận phòng và kiểm tra an ninh cảng. Quầy làm thủ tục sẽ đóng trước giờ tàu chạy <strong>30 phút</strong>.</div>\n"
                    + "    <div class=\"advisory-item\">🇻🇳 <strong>3. Tiền tệ &amp; Thanh toán:</strong> Giao dịch trên du thuyền và tại các điểm dừng chân trong nước sử dụng tiền Đồng Việt Nam (VND), chấp nhận chuyển khoản, quét mã VietQR và thẻ thanh toán nội địa.</div>\n"
                    + "    <div class=\"advisory-item\">🚫 <strong>4. Quy định hành lý cấm:</strong> Nghiêm cấm mang theo vũ khí, chất nổ, pháo sáng, chất dễ cháy, ma túy, hóa chất độc hại, flycam/drone chưa có giấy phép hoặc vật nuôi/thú cưng lên tàu.</div>\n"
                    + "    <div class=\"advisory-item\">🦺 <strong>5. Diễn tập an toàn cứu sinh (SOLAS):</strong> 100% hành khách trên tàu bắt buộc phải lắng nghe hướng dẫn an toàn và tham gia buổi diễn tập an toàn cứu hộ trên biển trước khi du thuyền rời bến.</div>\n"
                    + "  </div>\n"
                    + "</div>\n";
        }
    }

    private String buildTermsAndSignatures(BookingDetailResponse detail, String contractRef) {
        boolean isAccepted = detail.getContractStatus() == BookingContractStatus.Accepted;
        String acceptedTime = formatInstant(detail.getContractAcceptedAt());
        String docRef =
                detail.getContractDocumentReference() != null
                        ? escapeHtml(detail.getContractDocumentReference())
                        : "Lưu trữ số điện tử CASMS";

        return "<div class=\"section\" style=\"page-break-inside: avoid;\">\n"
                + "  <div class=\"section-title\">ĐIỀU 6: HIỆU LỰC HỢP ĐỒNG &amp; KÝ KẾT XÁC"
                + " NHẬN</div>\n"
                + "  <div style=\"font-size: 12px; margin-bottom: 12px;\">\n"
                + "    Hợp đồng này được xác lập có hiệu lực kể từ ngày phát hành/ký kết. Hai bên"
                + " cam kết thực hiện đúng các điều khoản đã thỏa thuận trong hợp đồng.<br>\n"
                + "    <em>* Mã hồ sơ lưu trữ: <strong>"
                + docRef
                + "</strong> | Thời điểm xác nhận: <strong>"
                + acceptedTime
                + "</strong></em>\n"
                + "  </div>\n"
                + "  <div class=\"signature-grid\">\n"
                + "    <div class=\"signature-box\">\n"
                + "      <strong>ĐẠI DIỆN BÊN A</strong><br>\n"
                + "      <span>(CÔNG TY CỔ PHẦN HẢI TRÌNH DU THUYỀN CASMS)</span>\n"
                + "      <div class=\"signature-space\">\n"
                + (isAccepted
                        ? "        <div class=\"seal-mark accepted\">✓ ĐÃ KÝ SỐ &amp; ĐÓNG DẤU ĐIỆN"
                                + " TỬ<br><small>CASMS CRUISE MARITIME</small></div>\n"
                        : "        <div class=\"seal-mark\">[Ký và đóng dấu]</div>\n")
                + "      </div>\n"
                + "      <strong>BAN ĐIỀU HÀNH HẢI TRÌNH</strong>\n"
                + "    </div>\n"
                + "    <div class=\"signature-box\">\n"
                + "      <strong>ĐẠI DIỆN BÊN B</strong><br>\n"
                + "      <span>(HÀNH KHÁCH / ĐẠI DIỆN ĐOÀN)</span>\n"
                + "      <div class=\"signature-space\">\n"
                + (isAccepted
                        ? "        <div class=\"seal-mark accepted\">✓ ĐÃ XÁC NHẬN CHẤP THUẬN HỢP"
                                + " ĐỒNG<br><small>"
                                + acceptedTime
                                + "</small></div>\n"
                        : "        <div class=\"seal-mark\">[Ký và ghi rõ họ tên]</div>\n")
                + "      </div>\n"
                + "      <strong>"
                + (detail.getPassengers() != null && !detail.getPassengers().isEmpty()
                        && detail.getPassengers().get(0).getFullName() != null
                        && !detail.getPassengers().get(0).getFullName().isBlank()
                        ? escapeHtml(detail.getPassengers().get(0).getFullName())
                        : "ĐẠI DIỆN HÀNH KHÁCH")
                + "</strong>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</div>\n"
                + "<div class=\"footer-note\">\n"
                + "  Bản hợp đồng được trích xuất động từ Cơ sở dữ liệu Hành khách của Hệ thống Quản"
                + " lý Hải trình CASMS. Mọi sao chép trái phép không qua xác thực của hệ thống đều"
                + " không có giá trị pháp lý.\n"
                + "</div>\n";
    }

    private String formatCabinType(CabinType type) {
        if (type == null) {
            return "Tiêu chuẩn";
        }
        return switch (type) {
            case BALCONY -> "Ban công (Balcony)";
            case SUITE -> "Hạng sang (Suite)";
            case OCEAN_VIEW -> "Hướng biển (Ocean View)";
            case INTERIOR -> "Nội thất (Interior)";
        };
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "Chưa cập nhật";
        }
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(instant);
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return "Chưa cập nhật";
        }
        return DateTimeFormatter.ofPattern("dd/MM/yyyy").format(date);
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "0 " + (currency != null ? currency : "VND");
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
        return nf.format(amount) + " " + (currency != null ? currency : "VND");
    }

    private String buildContractStyles() {
        return "body { font-family: 'Times New Roman', Times, serif, 'Segoe UI', Arial;"
                + " color: #222; background-color: #f1f5f9; margin: 0; padding: 24px; line-height:"
                + " 1.5; }\n"
                + ".no-print { max-width: 860px; margin: 0 auto 20px auto; display: flex;"
                + " justify-content: space-between; align-items: center; background: #0f172a;"
                + " color: #fff; padding: 12px 20px; border-radius: 8px; box-shadow: 0 4px 12px"
                + " rgba(0,0,0,0.15); font-family: 'Segoe UI', sans-serif; font-size: 13px; }\n"
                + ".btn-print { background: #2563eb; color: #fff; border: none; padding: 8px 18px;"
                + " font-size: 14px; font-weight: 600; border-radius: 6px; cursor: pointer;"
                + " transition: background 0.2s; }\n"
                + ".btn-print:hover { background: #1d4ed8; }\n"
                + ".contract-paper { background: #fff; max-width: 860px; margin: 0 auto; padding:"
                + " 45px 55px; box-shadow: 0 0 20px rgba(0,0,0,0.08); border-radius: 4px;"
                + " box-sizing: border-box; }\n"
                + "@media print {\n"
                + "  body { background: #fff !important; padding: 0 !important; font-size: 12.5px;"
                + " }\n"
                + "  .no-print { display: none !important; }\n"
                + "  .contract-paper { box-shadow: none !important; border: none !important;"
                + " padding: 0 !important; max-width: 100% !important; width: 100% !important; }\n"
                + "  @page { size: A4 portrait; margin: 15mm; }\n"
                + "}\n"
                + ".contract-header { display: flex; justify-content: space-between; margin-bottom:"
                + " 22px; border-bottom: 2px solid #0f172a; padding-bottom: 12px; }\n"
                + ".header-left { width: 48%; font-size: 12.5px; }\n"
                + ".header-left strong { font-size: 14px; color: #1e3a8a; text-transform:"
                + " uppercase; }\n"
                + ".header-right { width: 48%; text-align: center; font-size: 12.5px; }\n"
                + ".header-right strong { font-size: 13.5px; text-transform: uppercase; }\n"
                + ".contract-title { text-align: center; margin: 20px 0 22px 0; }\n"
                + ".contract-title h1 { font-size: 19px; margin: 0 0 6px 0; text-transform:"
                + " uppercase; color: #0f172a; letter-spacing: 0.5px; }\n"
                + ".contract-title p { margin: 0; font-style: italic; font-size: 12.5px; color:"
                + " #475569; }\n"
                + ".meta-badges { display: flex; justify-content: center; gap: 12px; margin-top:"
                + " 10px; font-size: 12px; font-family: 'Segoe UI', sans-serif; }\n"
                + ".badge { padding: 4px 10px; border-radius: 4px; background: #e2e8f0;"
                + " font-weight: 600; }\n"
                + ".badge-success { background: #dcfce7; color: #166534; border: 1px solid #86efac;"
                + " }\n"
                + ".badge-warning { background: #fef9c3; color: #854d0e; border: 1px solid #fde047;"
                + " }\n"
                + ".badge-scope-domestic { background: #e0f2fe; color: #0369a1; border: 1px solid #7dd3fc; font-weight: bold; }\n"
                + ".badge-scope-international { background: #ede9fe; color: #6d28d9; border: 1px solid #c4b5fd; font-weight: bold; }\n"
                + ".section { margin-bottom: 20px; }\n"
                + ".section-title { font-size: 13.5px; font-weight: bold; text-transform:"
                + " uppercase; color: #1e293b; margin-bottom: 8px; border-left: 4px solid #2563eb;"
                + " padding-left: 8px; }\n"
                + ".two-col { display: flex; gap: 16px; font-size: 12.5px; }\n"
                + ".party-box { flex: 1; background: #f8fafc; border: 1px solid #e2e8f0;"
                + " border-radius: 6px; padding: 12px 14px; }\n"
                + ".party-box h4 { margin: 0 0 8px 0; font-size: 12.5px; color: #1e3a8a;"
                + " border-bottom: 1px solid #cbd5e1; padding-bottom: 4px; text-transform:"
                + " uppercase; }\n"
                + ".info-row { margin-bottom: 4px; }\n"
                + ".info-label { font-weight: 600; color: #475569; display: inline-block;"
                + " min-width: 110px; }\n"
                + "table.data-table { width: 100%; border-collapse: collapse; margin-top: 8px;"
                + " font-size: 12px; }\n"
                + "table.data-table th, table.data-table td { border: 1px solid #cbd5e1; padding:"
                + " 7px 9px; text-align: left; }\n"
                + "table.data-table th { background-color: #f1f5f9; color: #0f172a; font-weight:"
                + " 600; text-align: center; }\n"
                + "table.data-table td.center { text-align: center; }\n"
                + "table.data-table td.right { text-align: right; }\n"
                + ".commit-grid { display: flex; gap: 14px; font-size: 11.5px; margin-top: 6px; }\n"
                + ".commit-box { flex: 1; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 9px 12px; }\n"
                + ".commit-box h5 { margin: 0 0 6px 0; font-size: 11.5px; color: #1e3a8a; text-transform: uppercase; border-bottom: 1px solid #cbd5e1; padding-bottom: 3px; }\n"
                + ".commit-list { margin: 0; padding-left: 15px; line-height: 1.55; color: #334155; }\n"
                + ".commit-list li { margin-bottom: 4px; }\n"
                + ".advisory-card { background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; padding: 10px 14px; font-size: 11.5px; line-height: 1.6; color: #78350f; }\n"
                + ".advisory-item { margin-bottom: 5px; }\n"
                + ".advisory-item:last-child { margin-bottom: 0; }\n"
                + ".advisory-item strong { color: #92400e; }\n"
                + ".signature-grid { display: flex; justify-content: space-between; margin-top:"
                + " 30px; page-break-inside: avoid; }\n"
                + ".signature-box { width: 46%; text-align: center; font-size: 12.5px; }\n"
                + ".signature-space { height: 75px; display: flex; align-items: center;"
                + " justify-content: center; }\n"
                + ".seal-mark { display: inline-block; border: 2px dashed #94a3b8; color: #64748b;"
                + " padding: 5px 12px; border-radius: 4px; font-size: 11px; font-family: 'Segoe"
                + " UI', sans-serif; }\n"
                + ".seal-mark.accepted { border: 2px solid #16a34a; color: #15803d; font-weight:"
                + " bold; }\n"
                + ".footer-note { margin-top: 25px; font-size: 11px; color: #64748b; text-align:"
                + " center; border-top: 1px solid #e2e8f0; padding-top: 10px; font-style: italic;"
                + " }\n";
    }
}
