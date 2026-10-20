package com.casms.booking.repository;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.PortRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookingPostgresIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CruiseRepository cruiseRepository;

    @Autowired
    private PortRepository portRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    private Port port;
    private Cruise cruise1;
    private Cruise cruise2;
    private User staffUser;
    private User passengerUser1;
    private User passengerUser2;
    private String adminToken;
    private String passengerToken;

    private final List<Booking> createdBookings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Ensure test port
        port = new Port();
        port.setPortCode("P-" + UUID.randomUUID().toString().substring(0, 8));
        port.setPortName("Test Port " + UUID.randomUUID().toString().substring(0, 4));
        port.setCountryCode("VN");
        port.setTimeZoneId("Asia/Ho_Chi_Minh");
        port.setStatus(PortStatus.ACTIVE);
        port = portRepository.save(port);

        // Ensure test cruises
        cruise1 = new Cruise();
        cruise1.setCruiseCode("CR-" + UUID.randomUUID().toString().substring(0, 8));
        cruise1.setCruiseName("Ha Long Voyage 1");
        cruise1.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        cruise1.setDepartureDatetime(Instant.now().plus(5, ChronoUnit.DAYS));
        cruise1.setReturnDatetime(Instant.now().plus(10, ChronoUnit.DAYS));
        cruise1.setDeparturePort(port);
        cruise1.setReturnPort(port);
        cruise1.setStatus(CruiseStatus.PUBLISHED);
        cruise1 = cruiseRepository.save(cruise1);

        cruise2 = new Cruise();
        cruise2.setCruiseCode("CR-" + UUID.randomUUID().toString().substring(0, 8));
        cruise2.setCruiseName("Ha Long Voyage 2");
        cruise2.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        cruise2.setDepartureDatetime(Instant.now().plus(12, ChronoUnit.DAYS));
        cruise2.setReturnDatetime(Instant.now().plus(18, ChronoUnit.DAYS));
        cruise2.setDeparturePort(port);
        cruise2.setReturnPort(port);
        cruise2.setStatus(CruiseStatus.PUBLISHED);
        cruise2 = cruiseRepository.save(cruise2);

        // Ensure test users
        staffUser = userRepository.findByUsername("admin").orElseGet(() -> {
            User user = new User();
            user.setUsername("test-admin-" + UUID.randomUUID().toString().substring(0, 6));
            user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
            user.setStatus(UserStatus.ACTIVE);
            user.setRoles(Set.of(Role.ADMIN));
            return userRepository.save(user);
        });

        passengerUser1 = new User();
        passengerUser1.setUsername("user1-" + UUID.randomUUID().toString().substring(0, 6));
        passengerUser1.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        passengerUser1.setStatus(UserStatus.ACTIVE);
        passengerUser1.setRoles(Set.of(Role.PASSENGER));
        passengerUser1 = userRepository.save(passengerUser1);

        passengerUser2 = new User();
        passengerUser2.setUsername("user2-" + UUID.randomUUID().toString().substring(0, 6));
        passengerUser2.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        passengerUser2.setStatus(UserStatus.ACTIVE);
        passengerUser2.setRoles(Set.of(Role.PASSENGER));
        passengerUser2 = userRepository.save(passengerUser2);

        adminToken = jwtService.generateToken(staffUser.getUsername(), staffUser.getUserId());
        passengerToken = jwtService.generateToken(passengerUser1.getUsername(), passengerUser1.getUserId());
    }

    @AfterEach
    void tearDown() {
        if (!createdBookings.isEmpty()) {
            bookingRepository.deleteAll(createdBookings);
            createdBookings.clear();
        }
        if (cruise1 != null) {
            cruiseRepository.delete(cruise1);
        }
        if (cruise2 != null) {
            cruiseRepository.delete(cruise2);
        }
        if (port != null) {
            portRepository.delete(port);
        }
        if (passengerUser1 != null) {
            userRepository.delete(passengerUser1);
        }
        if (passengerUser2 != null) {
            userRepository.delete(passengerUser2);
        }
    }

    private Booking createBooking(Cruise cruise, User user, BookingStatus status) {
        Booking booking = new Booking();
        booking.setCruise(cruise);
        booking.setBookedByUser(user);
        booking.setBookingStatus(status);
        booking.setBookingReference("BK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        booking.setAgreedFareAmount(BigDecimal.valueOf(1500.00));
        booking.setCurrency("USD");
        booking.setRequiredDepositAmount(BigDecimal.valueOf(300.00));
        booking = bookingRepository.save(booking);
        createdBookings.add(booking);
        return booking;
    }

    @Test
    @DisplayName("searchBookings - Không có filter (all nulls) chạy trên PostgreSQL thật thành công không lỗi 42P18")
    void testSearchBookings_NoFilter_ReturnsAllBookings() {
        Booking b1 = createBooking(cruise1, passengerUser1, BookingStatus.Draft);
        Booking b2 = createBooking(cruise1, passengerUser2, BookingStatus.Confirmed);
        Booking b3 = createBooking(cruise2, passengerUser1, BookingStatus.Cancelled);

        Page<Booking> result = bookingRepository.searchBookings(null, null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 3);
        List<UUID> ids = result.getContent().stream().map(Booking::getBookingId).toList();
        assertTrue(ids.contains(b1.getBookingId()));
        assertTrue(ids.contains(b2.getBookingId()));
        assertTrue(ids.contains(b3.getBookingId()));
    }

    @Test
    @DisplayName("searchBookings - Có từng filter riêng lẻ (cruiseId, status, userId)")
    void testSearchBookings_IndividualFilters() {
        Booking b1 = createBooking(cruise1, passengerUser1, BookingStatus.Draft);
        Booking b2 = createBooking(cruise1, passengerUser2, BookingStatus.Confirmed);
        Booking b3 = createBooking(cruise2, passengerUser1, BookingStatus.Cancelled);

        // Filter by cruiseId only
        Page<Booking> byCruise = bookingRepository.searchBookings(cruise1.getCruiseId(), null, null, Pageable.unpaged());
        List<UUID> byCruiseIds = byCruise.getContent().stream().map(Booking::getBookingId).toList();
        assertTrue(byCruiseIds.contains(b1.getBookingId()));
        assertTrue(byCruiseIds.contains(b2.getBookingId()));
        assertFalse(byCruiseIds.contains(b3.getBookingId()));

        // Filter by status only
        Page<Booking> byStatus = bookingRepository.searchBookings(null, BookingStatus.Confirmed, null, Pageable.unpaged());
        List<UUID> byStatusIds = byStatus.getContent().stream().map(Booking::getBookingId).toList();
        assertTrue(byStatusIds.contains(b2.getBookingId()));
        assertFalse(byStatusIds.contains(b1.getBookingId()));
        assertFalse(byStatusIds.contains(b3.getBookingId()));

        // Filter by userId only
        Page<Booking> byUser = bookingRepository.searchBookings(null, null, passengerUser2.getUserId(), Pageable.unpaged());
        List<UUID> byUserIds = byUser.getContent().stream().map(Booking::getBookingId).toList();
        assertTrue(byUserIds.contains(b2.getBookingId()));
        assertFalse(byUserIds.contains(b1.getBookingId()));
        assertFalse(byUserIds.contains(b3.getBookingId()));
    }

    @Test
    @DisplayName("searchBookings - Kết hợp nhiều filters (cruiseId + status, cruiseId + userId, all 3)")
    void testSearchBookings_CombinedFilters() {
        Booking b1 = createBooking(cruise1, passengerUser1, BookingStatus.Draft);
        Booking b2 = createBooking(cruise1, passengerUser2, BookingStatus.Confirmed);
        Booking b3 = createBooking(cruise2, passengerUser1, BookingStatus.Confirmed);

        // cruiseId + status
        Page<Booking> cruiseAndStatus = bookingRepository.searchBookings(cruise1.getCruiseId(), BookingStatus.Confirmed, null, Pageable.unpaged());
        List<UUID> r1 = cruiseAndStatus.getContent().stream().map(Booking::getBookingId).toList();
        assertEquals(List.of(b2.getBookingId()), r1);

        // cruiseId + userId
        Page<Booking> cruiseAndUser = bookingRepository.searchBookings(cruise2.getCruiseId(), null, passengerUser1.getUserId(), Pageable.unpaged());
        List<UUID> r2 = cruiseAndUser.getContent().stream().map(Booking::getBookingId).toList();
        assertEquals(List.of(b3.getBookingId()), r2);

        // cruiseId + status + userId (all 3 matching)
        Page<Booking> allThree = bookingRepository.searchBookings(cruise1.getCruiseId(), BookingStatus.Draft, passengerUser1.getUserId(), Pageable.unpaged());
        List<UUID> r3 = allThree.getContent().stream().map(Booking::getBookingId).toList();
        assertEquals(List.of(b1.getBookingId()), r3);

        // Non-matching combination
        Page<Booking> noMatch = bookingRepository.searchBookings(cruise2.getCruiseId(), BookingStatus.Draft, passengerUser2.getUserId(), Pageable.unpaged());
        assertTrue(noMatch.getContent().isEmpty());
    }

    @Test
    @DisplayName("Endpoint GET /booking - Không có filter trả về HTTP 200 (xác minh fix lỗi HTTP 500 SQLState 42P18 parameter $3)")
    void testGetBookingEndpoint_NoFilter_ReturnsHttp200() throws Exception {
        createBooking(cruise1, passengerUser1, BookingStatus.Confirmed);

        mockMvc.perform(get("/booking")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", not(empty())));
    }

    @Test
    @DisplayName("Endpoint GET /booking - Từng filter và kết hợp filters trả về HTTP 200 và dữ liệu hợp lệ")
    void testGetBookingEndpoint_WithFilters_ReturnsHttp200() throws Exception {
        Booking b1 = createBooking(cruise1, passengerUser1, BookingStatus.Draft);
        Booking b2 = createBooking(cruise1, passengerUser2, BookingStatus.Confirmed);

        // Filter status=Confirmed
        mockMvc.perform(get("/booking")
                        .param("status", "Confirmed")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[?(@.bookingReference == '" + b2.getBookingReference() + "')]").exists())
                .andExpect(jsonPath("$.data.content[?(@.bookingReference == '" + b1.getBookingReference() + "')]").doesNotExist());

        // Combined cruiseId + status
        mockMvc.perform(get("/booking")
                        .param("cruiseId", cruise1.getCruiseId().toString())
                        .param("status", "Draft")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[?(@.bookingReference == '" + b1.getBookingReference() + "')]").exists());
    }

    @Test
    @DisplayName("Endpoint GET /booking - Hành khách bình thường chỉ thấy đơn đặt của chính mình")
    void testGetBookingEndpoint_PassengerRole_IsIsolated() throws Exception {
        Booking b1 = createBooking(cruise1, passengerUser1, BookingStatus.Confirmed);
        Booking b2 = createBooking(cruise1, passengerUser2, BookingStatus.Confirmed);

        mockMvc.perform(get("/booking")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[?(@.bookingReference == '" + b1.getBookingReference() + "')]").exists())
                .andExpect(jsonPath("$.data.content[?(@.bookingReference == '" + b2.getBookingReference() + "')]").doesNotExist());
    }
}
