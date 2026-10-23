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
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
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
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseRepository;
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

    /**
     * Tra cứu danh sách các phòng còn trống trên tàu của chuyến đi để khách hàng tự chọn.
     */
    @Transactional(readOnly = true)
    public List<AvailableCabinResponse> getAvailableCabins(UUID cruiseId, UUID deckId, CabinType cabinType, Integer minCapacity) {
        Cruise cruise = getCruise(cruiseId);
        if (cruise.getShip() == null) {
            throw new BusinessException("CRUISE_NO_SHIP", "Cruise does not have an assigned ship yet");
        }
        UUID shipId = cruise.getShip().getShipId();

        List<Cabin> cabins = cabinRepository.searchAvailable(shipId, deckId, cabinType, minCapacity, null);
        List<AvailableCabinResponse> results = new ArrayList<>();

        for (Cabin cabin : cabins) {
            long currentOccupied = cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabin.getCabinId(), cruiseId);
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
                java.math.BigDecimal multiplier = (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                        ? cabin.getDeck().getPriceMultiplier()
                        : java.math.BigDecimal.valueOf(1.00);
                res.setPriceMultiplier(multiplier);
                java.math.BigDecimal baseFare = getBaseCabinFare(cabin.getCabinType());
                res.setEstimatedPrice(baseFare.multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP));
                results.add(res);
            }
        }

        return results;
    }

    /**
     * Đặt chỗ chuyến đi mới - Hành khách tự chọn phòng cụ thể và gói dịch vụ.
     */
    @Transactional
    public BookingDetailResponse createBooking(BookingCreateRequest request, CasmsPrincipal principal) {
        log.info("Creating new booking for cruise: {}", request.getCruiseId());

        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new BusinessException("PASSENGERS_REQUIRED", "At least one passenger must be included in the booking");
        }

        // Validate primary passenger contact information for guest booking
        PassengerBookingItemRequest primaryPassenger = request.getPassengers().get(0);
        if (principal == null && (primaryPassenger.getEmail() == null || primaryPassenger.getEmail().isBlank())) {
            throw new BusinessException("PRIMARY_EMAIL_REQUIRED", "Email of the primary passenger is required for booking confirmation and contract signing");
        }

        // Validate document numbers and consistency within the booking request
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
        if (cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("INVALID_CRUISE_STATUS", "Bookings can only be created for PUBLISHED cruises (Current: " + cruise.getStatus() + ")");
        }
        if (cruise.getDepartureDatetime().isBefore(Instant.now())) {
            throw new BusinessException("CRUISE_ALREADY_DEPARTED", "Cannot book a cruise that has already departed");
        }
        if (cruise.getShip() == null) {
            throw new BusinessException("CRUISE_NO_SHIP", "Cruise does not have an assigned ship yet");
        }

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
                .collect(Collectors.groupingBy(PassengerBookingItemRequest::getCabinId));

        Map<UUID, Cabin> validatedCabins = new HashMap<>();
        for (Map.Entry<UUID, List<PassengerBookingItemRequest>> entry : cabinPassengersMap.entrySet()) {
            UUID cabinId = entry.getKey();
            List<PassengerBookingItemRequest> passengersInCabin = entry.getValue();

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
            int remaining = cabin.getCapacity() - (int) currentOccupied;

            if (passengersInCabin.size() > remaining) {
                throw new BusinessException("CABIN_OVERCAPACITY",
                        String.format("Cabin %s has only %d available slot(s), but %d passengers were assigned to it",
                                cabin.getCabinNumber(), remaining, passengersInCabin.size()));
            }

            validatedCabins.put(cabinId, cabin);
        }

        // 4. Kiểm tra các gói tour (TourPackage) và tính tổng tiền vé
        Map<UUID, TourPackage> validatedPackages = new HashMap<>();
        BigDecimal totalPackageAmount = BigDecimal.ZERO;
        BigDecimal totalCabinBaseAmount = BigDecimal.ZERO;

        for (PassengerBookingItemRequest pReq : request.getPassengers()) {
            Cabin cabin = validatedCabins.get(pReq.getCabinId());
            java.math.BigDecimal baseFare = getBaseCabinFare(cabin.getCabinType());
            java.math.BigDecimal multiplier = (cabin.getDeck() != null && cabin.getDeck().getPriceMultiplier() != null)
                    ? cabin.getDeck().getPriceMultiplier()
                    : java.math.BigDecimal.valueOf(1.00);
            java.math.BigDecimal cabinPrice = baseFare.multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP);
            totalCabinBaseAmount = totalCabinBaseAmount.add(cabinPrice);

            if (pReq.getTourPackageId() != null) {
                TourPackage pkg = tourPackageRepository.findById(pReq.getTourPackageId())
                        .orElseThrow(() -> new ResourceNotFoundException("TourPackage", pReq.getTourPackageId()));
                if (pkg.getStatus() != TourPackageStatus.ACTIVE) {
                    throw new BusinessException("PACKAGE_NOT_ACTIVE", "Tour package is not currently ACTIVE: " + pkg.getPackageName());
                }
                validatedPackages.put(pReq.getTourPackageId(), pkg);
                totalPackageAmount = totalPackageAmount.add(pkg.getPrice());
            }
        }

        // 5. Xác định hình thức thanh toán & mức cọc
        PaymentPlan paymentPlan = request.getPaymentPlan() != null ? request.getPaymentPlan() : PaymentPlan.DEPOSIT;
        BigDecimal agreedFareAmount = totalCabinBaseAmount.add(totalPackageAmount);
        BigDecimal requiredDepositAmount;
        if (paymentPlan == PaymentPlan.FULL) {
            requiredDepositAmount = agreedFareAmount;
        } else {
            requiredDepositAmount = agreedFareAmount.multiply(BigDecimal.valueOf(0.30)).setScale(4, RoundingMode.HALF_UP);
        }

        String currency = (request.getCurrency() != null && !request.getCurrency().isBlank()) ? request.getCurrency().toUpperCase() : "USD";

        Instant scheduleCreatedAt = Instant.now();
        Instant finalPaymentDueAt = cruise.getDepartureDatetime().minus(Duration.ofDays(7));
        if (finalPaymentDueAt.isBefore(scheduleCreatedAt)) {
            finalPaymentDueAt = scheduleCreatedAt;
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
        booking.setBookingReference(businessCodeGenerator.next("BK"));
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
            bp.setRequestedCabinCategory(cabin.getCabinType().name());
            bp = bookingPassengerRepository.save(bp);
            createdBookingPassengers.add(bp);

            if (representative == null) {
                representative = bp;
            }

            // Gán phòng tự chọn (CabinAssignment)
            CabinAssignment assignment = new CabinAssignment();
            assignment.setBookingPassenger(bp);
            assignment.setCabin(cabin);
            assignment.setValidFrom(Instant.now());
            assignment.setValidTo(null);
            assignment.setAssignedByUser(bookedByUser);
            assignment.setChangeReason("Khách hàng tự chọn phòng khi đặt tour");
            cabinAssignmentRepository.save(assignment);

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
                        a.getAuthority().equals("ROLE_CUSTOMER_SERVICE") ||
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
        List<UUID> cabinIds = capacityPassengers.stream()
                .filter(passenger -> passenger.getParticipationStatus() == BookingPassengerParticipationStatus.Active)
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
        }

        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setConfirmedAt(Instant.now());
        booking = bookingRepository.save(booking);

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

        if (booking.getRequiredDepositAmount() != null && collectedAmount.compareTo(booking.getRequiredDepositAmount()) >= 0) {
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
                if (auth != null && auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal) {
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
            iRes.setSequenceNo(inst.getSequenceNo());
            iRes.setInstallmentType(inst.getInstallmentType());
            iRes.setRequiredAmount(inst.getRequiredAmount());
            iRes.setCurrency(inst.getCurrency());
            iRes.setDueAt(inst.getDueAt());
            iRes.setStatus(inst.getStatus());

            if (installments.size() == 1) {
                iRes.setDescription("Thanh toán toàn bộ 100% tiền vé");
            } else if (installments.size() == 2) {
                iRes.setDescription(inst.getSequenceNo() == 1 ? "Đợt 1: Đặt cọc 30% khi ký hợp đồng" : "Đợt 2: Thanh toán 70% còn lại trước ngày khởi hành");
            } else {
                if (inst.getSequenceNo() == 1) iRes.setDescription("Đợt 1: Đặt cọc giữ chỗ 30% khi ký hợp đồng");
                else if (inst.getSequenceNo() == 2) iRes.setDescription("Đợt 2: Thanh toán giữa kỳ 40%");
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
                BigDecimal s1 = agreedFare.multiply(BigDecimal.valueOf(0.30)).setScale(4, RoundingMode.HALF_UP);
                BigDecimal s2 = agreedFare.multiply(BigDecimal.valueOf(0.40)).setScale(4, RoundingMode.HALF_UP);
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

    void validatePaymentSchedule(PaymentPlan plan, Instant createdAt, Instant finalDue, Instant departure) {
        Instant firstDue = createdAt.plus(Duration.ofDays(2));
        if (!firstDue.isBefore(departure)) {
            throw new BusinessException("PAYMENT_SCHEDULE_UNAVAILABLE",
                    "The selected payment plan cannot be completed before cruise departure");
        }
        if (plan == PaymentPlan.FULL) {
            return;
        }
        if (firstDue.isAfter(finalDue)) {
            throw new BusinessException("PAYMENT_SCHEDULE_UNAVAILABLE",
                    "The final installment would be due before the initial installment");
        }
        if (plan == PaymentPlan.STAGES) {
            Instant secondDue = resolveSecondStageDue(createdAt, departure);
            if (secondDue.isAfter(finalDue) || !secondDue.isBefore(departure)) {
                throw new BusinessException("PAYMENT_SCHEDULE_UNAVAILABLE",
                        "The staged payment deadlines are not in chronological order for this departure date");
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
            case Cash -> payment.getInternalReceiptReference() != null && !payment.getInternalReceiptReference().isBlank();
            case GatewaySandbox -> payment.getProcessorReference() != null && !payment.getProcessorReference().isBlank();
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
        notif.setTitle("Thư mời ký hợp đồng & Xác nhận đặt chỗ chuyến đi " + booking.getBookingReference());
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

    private BigDecimal getBaseCabinFare(CabinType cabinType) {
        if (cabinType == null) return BigDecimal.valueOf(300);
        return switch (cabinType) {
            case INTERIOR -> BigDecimal.valueOf(300);
            case OCEAN_VIEW -> BigDecimal.valueOf(450);
            case BALCONY -> BigDecimal.valueOf(650);
            case SUITE -> BigDecimal.valueOf(1100);
        };
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
            guestUser.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
            guestUser.setRoles(EnumSet.of(Role.PASSENGER));
            return userRepository.save(guestUser);
        }

        // Trường hợp không có email: tạo tài khoản khách vãng lai
        User anonymousGuest = new User();
        String anonUsername = "guest_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4);
        anonymousGuest.setUsername(anonUsername);
        anonymousGuest.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        anonymousGuest.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
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
                               a.getAuthority().equals("ROLE_CUSTOMER_SERVICE") ||
                               a.getAuthority().equals("ROLE_FINANCE_OFFICER") ||
                               a.getAuthority().equals("ROLE_OPERATIONS_MANAGER") ||
                               (principal.isStaff() && a.getAuthority().equals("BOOKING_READ")));

        if (!isStaff && !booking.getBookedByUser().getUserId().equals(principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You can only view or manage your own bookings");
        }
    }
}
