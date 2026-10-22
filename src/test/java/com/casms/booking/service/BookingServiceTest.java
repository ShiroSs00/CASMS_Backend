package com.casms.booking.service;

import com.casms.booking.dto.request.BookingCancelRequest;
import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.booking.dto.request.CabinChangeRequest;
import com.casms.booking.dto.request.PassengerBookingItemRequest;
import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.entity.CabinAssignment;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.BookingContractRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.PassengerDocumentType;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.repository.NotificationRepository;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.RefundRepository;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.repository.BookingPassengerPackageRepository;
import com.casms.servicepackage.repository.TourPackageRepository;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingPassengerRepository bookingPassengerRepository;
    @Mock private CabinAssignmentRepository cabinAssignmentRepository;
    @Mock private BookingContractRepository bookingContractRepository;
    @Mock private CruiseRepository cruiseRepository;
    @Mock private CabinRepository cabinRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private TourPackageRepository tourPackageRepository;
    @Mock private BookingPassengerPackageRepository bookingPassengerPackageRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private BusinessCodeGenerator businessCodeGenerator;

    @InjectMocks
    private BookingService bookingService;

    private Ship ship;
    private Cruise cruise;
    private Cabin cabin;
    private UUID cruiseId;
    private UUID cabinId;

    @BeforeEach
    void setUp() {
        cruiseId = UUID.randomUUID();
        cabinId = UUID.randomUUID();

        ship = new Ship();
        ship.setShipId(UUID.randomUUID());
        ship.setShipName("Grand Sea Queen");
        ship.setTotalCapacity(500);

        Deck deck = new Deck();
        deck.setDeckId(UUID.randomUUID());
        deck.setDeckNumber(3);
        deck.setDeckName("Deck 3");
        deck.setShip(ship);

        cabin = new Cabin();
        cabin.setCabinId(cabinId);
        cabin.setCabinNumber("C301");
        cabin.setCabinType(CabinType.BALCONY);
        cabin.setCapacity(2);
        cabin.setStatus(CabinStatus.AVAILABLE);
        cabin.setShip(ship);
        cabin.setDeck(deck);

        cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        cruise.setCruiseCode("CR-2026-001");
        cruise.setCruiseName("Halong Discovery");
        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise.setDepartureDatetime(Instant.now().plus(30, ChronoUnit.DAYS));
        cruise.setReturnDatetime(Instant.now().plus(33, ChronoUnit.DAYS));
        cruise.setShip(ship);
    }

    @Test
    @DisplayName("Tra cứu booking công khai bắt buộc có email hoặc số điện thoại")
    void lookupBooking_requiresContactCredential() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> bookingService.lookupBooking("BK-PRIVATE", " ", null));

        assertEquals("LOOKUP_CREDENTIAL_REQUIRED", ex.getCode());
        verify(bookingRepository, never()).findByBookingReference(any());
    }

    @Test
    @DisplayName("Đặt tour thành công cho khách vãng lai và tự động cấp tài khoản theo email")
    void createBooking_guestSuccess() {
        when(businessCodeGenerator.next("BK")).thenReturn("BK-0001");
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);
        request.setPaymentPlan(PaymentPlan.DEPOSIT);

        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setFullName("Nguyen Van A");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p.setEmail("nguyenvana@example.com");
        p.setPhoneNumber("+84912345678");
        p.setCabinId(cabinId);
        request.setPassengers(List.of(p));

        when(userRepository.findByEmail("nguyenvana@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed_pass");
        User savedUser = new User();
        savedUser.setUserId(UUID.randomUUID());
        savedUser.setUsername("nguyenvana");
        savedUser.setEmail("nguyenvana@example.com");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId)).thenReturn(10L);
        when(cabinRepository.findByIdForUpdate(cabinId)).thenReturn(Optional.of(cabin));
        when(cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabinId, cruiseId)).thenReturn(0L);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setBookingId(UUID.randomUUID());
            return b;
        });

        Passenger passenger = new Passenger();
        passenger.setPassengerId(UUID.randomUUID());
        when(passengerRepository.save(any(Passenger.class))).thenReturn(passenger);

        BookingPassenger bp = new BookingPassenger();
        bp.setBookingPassengerId(UUID.randomUUID());
        bp.setPassenger(passenger);
        when(bookingPassengerRepository.save(any(BookingPassenger.class))).thenReturn(bp);

        BookingDetailResponse response = bookingService.createBooking(request, null);

        assertNotNull(response);
        assertEquals(BookingStatus.Draft, response.getBookingStatus());
        assertNotNull(response.getBookingReference());
        verify(notificationRepository).save(any());
        verify(bookingContractRepository).save(any());
    }

    @Test
    @DisplayName("Chặn đặt tour khi khách vãng lai không cung cấp email liên hệ")
    void createBooking_missingPrimaryEmail_throwsException() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);

        PassengerBookingItemRequest p = new PassengerBookingItemRequest();
        p.setFullName("Nguyen Van A");
        p.setEmail(""); // Empty email
        p.setCabinId(cabinId);
        request.setPassengers(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(request, null));
        assertEquals("PRIMARY_EMAIL_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("Chặn đặt tour khi có 2 hành khách trùng số giấy tờ trong cùng 1 đơn")
    void createBooking_duplicatePassengerDocument_throwsException() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);

        PassengerBookingItemRequest p1 = new PassengerBookingItemRequest();
        p1.setFullName("Nguyen Van A");
        p1.setEmail("a@example.com");
        p1.setDocumentType(PassengerDocumentType.Passport);
        p1.setDocumentNumber("B1234567");
        p1.setCabinId(cabinId);

        PassengerBookingItemRequest p2 = new PassengerBookingItemRequest();
        p2.setFullName("Nguyen Van B");
        p2.setEmail("b@example.com");
        p2.setDocumentType(PassengerDocumentType.Passport);
        p2.setDocumentNumber("B1234567"); // Duplicate document number
        p2.setCabinId(cabinId);

        request.setPassengers(List.of(p1, p2));

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(request, null));
        assertEquals("DUPLICATE_PASSENGER_DOCUMENT", ex.getCode());
    }

    @Test
    @DisplayName("Chặn đặt tour khi sức chứa phòng tự chọn không đủ")
    void createBooking_cabinOvercapacity_throwsException() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setCruiseId(cruiseId);

        PassengerBookingItemRequest p1 = new PassengerBookingItemRequest();
        p1.setFullName("Nguyen Van A");
        p1.setEmail("a@example.com");
        p1.setCabinId(cabinId);

        PassengerBookingItemRequest p2 = new PassengerBookingItemRequest();
        p2.setFullName("Nguyen Van B");
        p2.setEmail("b@example.com");
        p2.setCabinId(cabinId);

        PassengerBookingItemRequest p3 = new PassengerBookingItemRequest();
        p3.setFullName("Nguyen Van C");
        p3.setEmail("c@example.com");
        p3.setCabinId(cabinId);

        request.setPassengers(List.of(p1, p2, p3)); // 3 passengers, but cabin capacity is only 2

        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(new User()));
        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId)).thenReturn(0L);
        when(cabinRepository.findByIdForUpdate(cabinId)).thenReturn(Optional.of(cabin));
        when(cabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise(cabinId, cruiseId)).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(request, null));
        assertEquals("CABIN_OVERCAPACITY", ex.getCode());
    }

    @Test
    @DisplayName("Hủy đặt chỗ giải phóng phòng thành công")
    void cancelBooking_success() {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User bookedUser = new User();
        bookedUser.setUserId(userId);

        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookedByUser(bookedUser);
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setCruise(cruise);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingPassengerRepository.findByBookingBookingId(bookingId)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getAuthorities()).thenReturn(List.of());

        BookingCancelRequest cancelReq = new BookingCancelRequest();
        cancelReq.setReason("Bận việc cá nhân");

        BookingDetailResponse response = bookingService.cancel(bookingId, cancelReq, principal);

        assertEquals(BookingStatus.Cancelled, response.getBookingStatus());
        assertEquals("Bận việc cá nhân", response.getCancellationReason());
    }

    @Test
    @DisplayName("Hành khách có BOOKING_CREATE không thể xác nhận booking")
    void confirmBooking_passengerPermissionDenied() {
        UUID bookingId = UUID.randomUUID();
        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        when(principal.getUserId()).thenReturn(UUID.randomUUID());
        doReturn(List.of(new SimpleGrantedAuthority("BOOKING_CREATE"))).when(principal).getAuthorities();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bookingService.confirm(bookingId, principal));

        assertEquals("BOOKING_CONFIRM_FORBIDDEN", ex.getCode());
        verify(bookingRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Giao dịch thanh toán thất bại không được tính là tiền đã thu")
    void confirmBooking_failedPaymentIsRejected() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setRequiredDepositAmount(new BigDecimal("100.00"));
        booking.setCurrency("USD");
        booking.setCruise(cruise);
        BookingContract contract = new BookingContract();
        contract.setStatus(BookingContractStatus.Accepted);

        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        when(principal.getUserId()).thenReturn(UUID.randomUUID());
        doReturn(List.of(new SimpleGrantedAuthority("BOOKING_CONFIRM"))).when(principal).getAuthorities();
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingContractRepository.findByBookingBookingId(bookingId)).thenReturn(Optional.of(contract));
        when(cruiseRepository.findByIdForUpdate(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        Payment failedPayment = new Payment();
        failedPayment.setStatus(PaymentStatus.Failed);
        failedPayment.setOperationType(PaymentOperationType.Collect);
        failedPayment.setPaymentMethod(PaymentMethod.Cash);
        failedPayment.setConfirmedAmount(new BigDecimal("100.00"));
        failedPayment.setConfirmedAt(Instant.now());
        failedPayment.setCurrency("USD");
        failedPayment.setInternalReceiptReference("receipt-failed");
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of(failedPayment));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bookingService.confirm(bookingId, principal));

        assertEquals("BOOKING_DEPOSIT_NOT_PAID", ex.getCode());
        assertEquals(BookingStatus.Draft, booking.getBookingStatus());
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    @DisplayName("Giao dịch đã xác nhận gắn trực tiếp với booking được tính vào khoản cọc")
    void confirmBooking_verifiedDirectBookingPaymentIsAccepted() {
        UUID bookingId = UUID.randomUUID();
        User owner = new User();
        owner.setUserId(UUID.randomUUID());
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingReference("BK-0001");
        booking.setBookedByUser(owner);
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setRequiredDepositAmount(new BigDecimal("100.00"));
        booking.setAgreedFareAmount(new BigDecimal("300.00"));
        booking.setCurrency("USD");
        booking.setCruise(cruise);
        BookingContract contract = new BookingContract();
        contract.setStatus(BookingContractStatus.Accepted);

        CasmsPrincipal principal = mock(CasmsPrincipal.class);
        when(principal.getUserId()).thenReturn(UUID.randomUUID());
        doReturn(List.of(new SimpleGrantedAuthority("BOOKING_CONFIRM"))).when(principal).getAuthorities();
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingContractRepository.findByBookingBookingId(bookingId)).thenReturn(Optional.of(contract));
        when(cruiseRepository.findByIdForUpdate(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.Cash);
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setConfirmedAmount(new BigDecimal("100.00"));
        payment.setConfirmedAt(Instant.now());
        payment.setInternalReceiptReference("receipt-1");
        payment.setCurrency("USD");
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of(payment));
        when(refundRepository.findForBooking(bookingId)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingDetailResponse response = bookingService.confirm(bookingId, principal);

        assertEquals(BookingStatus.Confirmed, response.getBookingStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Lịch đặt sát ngày chỉ được chấp nhận khi các hạn thanh toán còn theo thứ tự")
    void paymentSchedule_nearDepartureIsValidated() {
        Instant createdAt = Instant.parse("2026-10-22T00:00:00Z");
        for (int days : new int[]{1, 3, 7, 14, 30, 60}) {
            Instant departure = createdAt.plus(days, ChronoUnit.DAYS);
            Instant finalDue = departure.minus(7, ChronoUnit.DAYS);
            if (finalDue.isBefore(createdAt)) finalDue = createdAt;
            Instant validatedFinalDue = finalDue;
            boolean supported = days >= 14;

            if (supported) {
                assertDoesNotThrow(() -> bookingService.validatePaymentSchedule(
                        PaymentPlan.DEPOSIT, createdAt, validatedFinalDue, departure), "DEPOSIT at " + days + " days");
                assertDoesNotThrow(() -> bookingService.validatePaymentSchedule(
                        PaymentPlan.STAGES, createdAt, validatedFinalDue, departure), "STAGES at " + days + " days");
            } else {
                assertThrows(BusinessException.class, () -> bookingService.validatePaymentSchedule(
                        PaymentPlan.DEPOSIT, createdAt, validatedFinalDue, departure), "DEPOSIT at " + days + " days");
                assertThrows(BusinessException.class, () -> bookingService.validatePaymentSchedule(
                        PaymentPlan.STAGES, createdAt, validatedFinalDue, departure), "STAGES at " + days + " days");
            }
        }

        Instant exactlyTwoDays = createdAt.plus(2, ChronoUnit.DAYS);
        assertThrows(BusinessException.class, () -> bookingService.validatePaymentSchedule(
                PaymentPlan.FULL, createdAt, createdAt, exactlyTwoDays));
        assertThrows(BusinessException.class, () -> bookingService.validatePaymentSchedule(
                PaymentPlan.FULL, createdAt, createdAt, createdAt.minus(1, ChronoUnit.DAYS)));
        Instant nineDays = createdAt.plus(9, ChronoUnit.DAYS);
        assertDoesNotThrow(() -> bookingService.validatePaymentSchedule(
                PaymentPlan.DEPOSIT, createdAt, nineDays.minus(7, ChronoUnit.DAYS), nineDays));
    }
}
