package com.casms.ship.repository;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
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
class ItineraryChangeHistoryPostgresIntegrationTest {

    @Autowired
    private ItineraryChangeHistoryRepository historyRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private CruiseStopRepository cruiseStopRepository;

    @Autowired
    private ItineraryDayRepository itineraryDayRepository;

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
    private Itinerary itinerary1;
    private Itinerary itinerary2;
    private ItineraryDay day1;
    private CruiseStop stop1;
    private User adminUser;
    private User staffUser;
    private String adminToken;

    private final List<ItineraryChangeHistory> createdHistories = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Port
        port = new Port();
        port.setPortCode("P-HIST-" + UUID.randomUUID().toString().substring(0, 6));
        port.setPortName("History Test Port");
        port.setCountryCode("VN");
        port.setTimeZoneId("Asia/Ho_Chi_Minh");
        port.setStatus(PortStatus.ACTIVE);
        port = portRepository.save(port);

        // Cruise 1
        cruise1 = new Cruise();
        cruise1.setCruiseCode("CR-H1-" + UUID.randomUUID().toString().substring(0, 6));
        cruise1.setCruiseName("Itinerary Cruise 1");
        cruise1.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        cruise1.setDepartureDatetime(Instant.now().plus(10, ChronoUnit.DAYS));
        cruise1.setReturnDatetime(Instant.now().plus(17, ChronoUnit.DAYS));
        cruise1.setDeparturePort(port);
        cruise1.setReturnPort(port);
        cruise1.setStatus(CruiseStatus.PUBLISHED);
        cruise1 = cruiseRepository.save(cruise1);

        // Cruise 2
        cruise2 = new Cruise();
        cruise2.setCruiseCode("CR-H2-" + UUID.randomUUID().toString().substring(0, 6));
        cruise2.setCruiseName("Itinerary Cruise 2");
        cruise2.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        cruise2.setDepartureDatetime(Instant.now().plus(20, ChronoUnit.DAYS));
        cruise2.setReturnDatetime(Instant.now().plus(27, ChronoUnit.DAYS));
        cruise2.setDeparturePort(port);
        cruise2.setReturnPort(port);
        cruise2.setStatus(CruiseStatus.PUBLISHED);
        cruise2 = cruiseRepository.save(cruise2);

        // Itinerary 1
        itinerary1 = new Itinerary();
        itinerary1.setCruise(cruise1);
        itinerary1.setItineraryName("Itinerary Test 1");
        itinerary1.setStartDate(LocalDate.now().plusDays(10));
        itinerary1.setEndDate(LocalDate.now().plusDays(17));
        itinerary1.setItineraryType(ItineraryType.ROUND_TRIP);
        itinerary1.setStatus(ItineraryStatus.APPROVED);
        itinerary1 = itineraryRepository.save(itinerary1);

        // Itinerary 2
        itinerary2 = new Itinerary();
        itinerary2.setCruise(cruise2);
        itinerary2.setItineraryName("Itinerary Test 2");
        itinerary2.setStartDate(LocalDate.now().plusDays(20));
        itinerary2.setEndDate(LocalDate.now().plusDays(27));
        itinerary2.setItineraryType(ItineraryType.ROUND_TRIP);
        itinerary2.setStatus(ItineraryStatus.APPROVED);
        itinerary2 = itineraryRepository.save(itinerary2);

        // ItineraryDay for Itinerary 1
        day1 = new ItineraryDay();
        day1.setItinerary(itinerary1);
        day1.setDayNumber(1);
        day1.setDate(LocalDate.now().plusDays(11));
        day1.setDayType(ItineraryDayType.PORT_DAY);
        day1 = itineraryDayRepository.save(day1);

        // CruiseStop for Itinerary 1
        stop1 = new CruiseStop();
        stop1.setItinerary(itinerary1);
        stop1.setArrivalItineraryDay(day1);
        stop1.setDepartureItineraryDay(day1);
        stop1.setPort(port);
        stop1.setStopOrder(1);
        stop1.setStatus(CruiseStopStatus.PLANNED);
        stop1.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop1.setCallType(CruiseStopCallType.NORMAL);
        stop1.setPlannedArrival(Instant.now().plus(11, ChronoUnit.DAYS));
        stop1.setPlannedDeparture(Instant.now().plus(11, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS));
        stop1 = cruiseStopRepository.save(stop1);

        // Users
        adminUser = userRepository.findByUsername("admin").orElseGet(() -> {
            User user = new User();
            user.setUsername("admin-hist-" + UUID.randomUUID().toString().substring(0, 6));
            user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
            user.setStatus(UserStatus.ACTIVE);
            user.setRoles(Set.of(Role.ADMIN));
            return userRepository.save(user);
        });

        staffUser = new User();
        staffUser.setUsername("staff-hist-" + UUID.randomUUID().toString().substring(0, 6));
        staffUser.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        staffUser.setStatus(UserStatus.ACTIVE);
        staffUser.setRoles(Set.of(Role.ADMIN));
        staffUser = userRepository.save(staffUser);

        adminToken = jwtService.generateToken(adminUser.getUsername(), adminUser.getUserId());
    }

    @AfterEach
    void tearDown() {
        if (!createdHistories.isEmpty()) {
            historyRepository.deleteAll(createdHistories);
            createdHistories.clear();
        }
        if (stop1 != null) {
            cruiseStopRepository.delete(stop1);
        }
        if (day1 != null) {
            itineraryDayRepository.delete(day1);
        }
        if (itinerary1 != null) {
            itineraryRepository.delete(itinerary1);
        }
        if (itinerary2 != null) {
            itineraryRepository.delete(itinerary2);
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
        if (staffUser != null) {
            userRepository.delete(staffUser);
        }
    }

    private ItineraryChangeHistory createHistory(Itinerary itinerary,
                                                 CruiseStop stop,
                                                 ItineraryChangeType changeType,
                                                 User user,
                                                 Instant changedAt) {
        ItineraryChangeHistory h = new ItineraryChangeHistory();
        h.setItinerary(itinerary);
        h.setCruiseStop(stop);
        h.setChangedEntityType(stop != null ? "CruiseStop" : "Itinerary");
        h.setChangedEntityId(stop != null ? stop.getCruiseStopId() : itinerary.getItineraryId());
        h.setChangeType(changeType);
        h.setChangeSummary("Test summary " + changeType);
        h.setReason("Operational adjustment");
        h.setChangedByUser(user);
        h.setChangedAt(changedAt);
        h = historyRepository.save(h);
        createdHistories.add(h);
        return h;
    }

    @Test
    @DisplayName("search - Không có filter (all nulls) chạy trên PostgreSQL thật thành công không lỗi 42P18")
    void testSearch_NoFilter_ReturnsAll() {
        Instant now = Instant.now();
        ItineraryChangeHistory h1 = createHistory(itinerary1, stop1, ItineraryChangeType.TIME_CHANGE, adminUser, now.minus(2, ChronoUnit.HOURS));
        ItineraryChangeHistory h2 = createHistory(itinerary2, null, ItineraryChangeType.PORT_OMITTED, staffUser, now.minus(1, ChronoUnit.HOURS));

        List<ItineraryChangeHistory> list = historyRepository.search(null, null, null, null);

        assertNotNull(list);
        assertTrue(list.size() >= 2);
        List<UUID> ids = list.stream().map(ItineraryChangeHistory::getChangeHistoryId).toList();
        assertTrue(ids.contains(h1.getChangeHistoryId()));
        assertTrue(ids.contains(h2.getChangeHistoryId()));
    }

    @Test
    @DisplayName("search - Giữ nguyên thứ tự lịch sử ORDER BY changedAt DESC")
    void testSearch_Ordering_PreservedChangedAtDesc() {
        Instant now = Instant.now();
        ItineraryChangeHistory oldest = createHistory(itinerary1, null, ItineraryChangeType.TIME_CHANGE, adminUser, now.minus(3, ChronoUnit.HOURS));
        ItineraryChangeHistory middle = createHistory(itinerary1, null, ItineraryChangeType.PORT_OMITTED, adminUser, now.minus(2, ChronoUnit.HOURS));
        ItineraryChangeHistory newest = createHistory(itinerary1, null, ItineraryChangeType.PORT_SUBSTITUTED, adminUser, now.minus(1, ChronoUnit.HOURS));

        List<ItineraryChangeHistory> list = historyRepository.search(itinerary1.getItineraryId(), null, null, null);

        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals(newest.getChangeHistoryId(), list.get(0).getChangeHistoryId());
        assertEquals(middle.getChangeHistoryId(), list.get(1).getChangeHistoryId());
        assertEquals(oldest.getChangeHistoryId(), list.get(2).getChangeHistoryId());
    }

    @Test
    @DisplayName("search - Từng filter riêng lẻ (itineraryId, cruiseStopId, changeType, changedByUserId)")
    void testSearch_IndividualFilters() {
        Instant now = Instant.now();
        ItineraryChangeHistory h1 = createHistory(itinerary1, stop1, ItineraryChangeType.TIME_CHANGE, adminUser, now.minus(2, ChronoUnit.HOURS));
        ItineraryChangeHistory h2 = createHistory(itinerary2, null, ItineraryChangeType.PORT_OMITTED, staffUser, now.minus(1, ChronoUnit.HOURS));

        // itineraryId only
        List<ItineraryChangeHistory> byItin = historyRepository.search(itinerary1.getItineraryId(), null, null, null);
        List<UUID> byItinIds = byItin.stream().map(ItineraryChangeHistory::getChangeHistoryId).toList();
        assertTrue(byItinIds.contains(h1.getChangeHistoryId()));
        assertFalse(byItinIds.contains(h2.getChangeHistoryId()));

        // cruiseStopId only
        List<ItineraryChangeHistory> byStop = historyRepository.search(null, stop1.getCruiseStopId(), null, null);
        List<UUID> byStopIds = byStop.stream().map(ItineraryChangeHistory::getChangeHistoryId).toList();
        assertTrue(byStopIds.contains(h1.getChangeHistoryId()));
        assertFalse(byStopIds.contains(h2.getChangeHistoryId()));

        // changeType only
        List<ItineraryChangeHistory> byType = historyRepository.search(null, null, ItineraryChangeType.PORT_OMITTED, null);
        List<UUID> byTypeIds = byType.stream().map(ItineraryChangeHistory::getChangeHistoryId).toList();
        assertTrue(byTypeIds.contains(h2.getChangeHistoryId()));
        assertFalse(byTypeIds.contains(h1.getChangeHistoryId()));

        // changedByUserId only
        List<ItineraryChangeHistory> byUser = historyRepository.search(null, null, null, staffUser.getUserId());
        List<UUID> byUserIds = byUser.stream().map(ItineraryChangeHistory::getChangeHistoryId).toList();
        assertTrue(byUserIds.contains(h2.getChangeHistoryId()));
        assertFalse(byUserIds.contains(h1.getChangeHistoryId()));
    }

    @Test
    @DisplayName("search - Kết hợp nhiều filters (itineraryId + changeType, all 4 filters)")
    void testSearch_CombinedFilters() {
        Instant now = Instant.now();
        ItineraryChangeHistory h1 = createHistory(itinerary1, stop1, ItineraryChangeType.TIME_CHANGE, adminUser, now.minus(2, ChronoUnit.HOURS));
        ItineraryChangeHistory h2 = createHistory(itinerary1, null, ItineraryChangeType.PORT_OMITTED, adminUser, now.minus(1, ChronoUnit.HOURS));

        // itineraryId + changeType
        List<ItineraryChangeHistory> r1 = historyRepository.search(itinerary1.getItineraryId(), null, ItineraryChangeType.TIME_CHANGE, null);
        assertEquals(1, r1.size());
        assertEquals(h1.getChangeHistoryId(), r1.get(0).getChangeHistoryId());

        // all 4 filters
        List<ItineraryChangeHistory> r2 = historyRepository.search(
                itinerary1.getItineraryId(),
                stop1.getCruiseStopId(),
                ItineraryChangeType.TIME_CHANGE,
                adminUser.getUserId()
        );
        assertEquals(1, r2.size());
        assertEquals(h1.getChangeHistoryId(), r2.get(0).getChangeHistoryId());
    }

    @Test
    @DisplayName("Endpoint GET /itinerary-change-history - Không có filter trả về HTTP 200 (xác minh fix lỗi HTTP 500 SQLState 42P18 parameter $5)")
    void testGetItineraryChangeHistoryEndpoint_NoFilter_ReturnsHttp200() throws Exception {
        createHistory(itinerary1, stop1, ItineraryChangeType.TIME_CHANGE, adminUser, Instant.now());

        mockMvc.perform(get("/itinerary-change-history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    @DisplayName("Endpoint GET /itinerary-change-history - Từng filter và kết hợp filters trả về HTTP 200")
    void testGetItineraryChangeHistoryEndpoint_WithFilters_ReturnsHttp200() throws Exception {
        ItineraryChangeHistory h1 = createHistory(itinerary1, stop1, ItineraryChangeType.TIME_CHANGE, adminUser, Instant.now().minus(2, ChronoUnit.HOURS));
        ItineraryChangeHistory h2 = createHistory(itinerary2, null, ItineraryChangeType.PORT_OMITTED, staffUser, Instant.now().minus(1, ChronoUnit.HOURS));

        // Filter changeType
        mockMvc.perform(get("/itinerary-change-history")
                        .param("changeType", "PORT_OMITTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.changeHistoryId == '" + h2.getChangeHistoryId() + "')]").exists())
                .andExpect(jsonPath("$[?(@.changeHistoryId == '" + h1.getChangeHistoryId() + "')]").doesNotExist());

        // Combined itineraryId + changeType
        mockMvc.perform(get("/itinerary-change-history")
                        .param("itineraryId", itinerary1.getItineraryId().toString())
                        .param("changeType", "TIME_CHANGE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.changeHistoryId == '" + h1.getChangeHistoryId() + "')]").exists())
                .andExpect(jsonPath("$[?(@.changeHistoryId == '" + h2.getChangeHistoryId() + "')]").doesNotExist());
    }
}
