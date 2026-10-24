package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.PortCreateRequest;
import com.casms.ship.dto.response.ItineraryDayResponse;
import com.casms.ship.dto.response.PortResponse;
import com.casms.ship.dto.response.PortSummaryResponse;
import com.casms.ship.dto.response.TimeZoneOptionResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.DestinationStatus;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.ship.util.PortTimeZoneHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortDestinationTimeZoneServiceTest {

    @Mock
    private PortRepository portRepository;

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private ItineraryDayRepository itineraryDayRepository;

    @Mock
    private ItineraryRepository itineraryRepository;

    @InjectMocks
    private PortService portService;

    @InjectMocks
    private DestinationService destinationService;

    @InjectMocks
    private ItineraryDayService itineraryDayService;

    @Test
    @DisplayName("PortTimeZoneHelper: Suy luận múi giờ cho Châu Á, Châu Âu, Châu Mỹ và Châu Đại Dương")
    void testTimeZoneHelperAutoResolution() {
        // Châu Á
        assertEquals("Asia/Tokyo", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("JP").orElseThrow());
        assertEquals("Asia/Tokyo", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("jp").orElseThrow());
        assertEquals("Asia/Ho_Chi_Minh", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("VN").orElseThrow());
        assertEquals("Asia/Singapore", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("SG").orElseThrow());
        assertEquals("Asia/Seoul", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("KR").orElseThrow());

        // Châu Âu & Địa Trung Hải
        assertEquals("Europe/Paris", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("FR").orElseThrow());
        assertEquals("Europe/Rome", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("IT").orElseThrow());
        assertEquals("Europe/London", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("GB").orElseThrow());

        // Châu Mỹ & Caribbean
        assertEquals("America/New_York", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("US").orElseThrow());
        assertEquals("America/Nassau", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("BS").orElseThrow());

        // Châu Đại Dương
        assertEquals("Australia/Sydney", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("AU").orElseThrow());
        assertEquals("Pacific/Auckland", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("NZ").orElseThrow());

        assertTrue(PortTimeZoneHelper.isValidTimeZoneId("Asia/Tokyo"));
        assertTrue(PortTimeZoneHelper.isValidTimeZoneId("Europe/Paris"));
        assertTrue(PortTimeZoneHelper.isValidTimeZoneId("America/New_York"));
        assertFalse(PortTimeZoneHelper.isValidTimeZoneId("Invalid/TimeZone"));
        assertFalse(PortTimeZoneHelper.isValidTimeZoneId("Mars/Olympus"));
        assertFalse(PortTimeZoneHelper.isValidTimeZoneId(""));
        assertFalse(PortTimeZoneHelper.isValidTimeZoneId(null));

        assertEquals("+09:00", PortTimeZoneHelper.calculateUtcOffset("Asia/Tokyo"));
        assertEquals("+07:00", PortTimeZoneHelper.calculateUtcOffset("Asia/Ho_Chi_Minh"));
        assertEquals("+08:00", PortTimeZoneHelper.calculateUtcOffset("Asia/Singapore"));
        assertEquals("Asia/Tokyo (UTC+09:00)", PortTimeZoneHelper.formatTimeZoneDisplay("Asia/Tokyo"));
        assertNotNull(PortTimeZoneHelper.getCurrentLocalTimeIso("Asia/Tokyo"));
    }

    @Test
    @DisplayName("PortTimeZoneHelper: Lấy danh sách múi giờ chuẩn IANA kèm gợi ý theo quốc gia và tìm kiếm")
    void testGetSupportedTimeZoneOptions() {
        List<TimeZoneOptionResponse> options = PortTimeZoneHelper.getSupportedTimeZoneOptions("Tokyo", "JP");
        assertFalse(options.isEmpty());
        assertTrue(options.stream().anyMatch(opt -> opt.getTimeZoneId().equals("Asia/Tokyo")));

        TimeZoneOptionResponse tokyoOpt = options.stream()
                .filter(opt -> opt.getTimeZoneId().equals("Asia/Tokyo"))
                .findFirst().orElseThrow();
        assertTrue(tokyoOpt.isRecommended());
        assertEquals("+09:00", tokyoOpt.getUtcOffset());
        assertNotNull(tokyoOpt.getCurrentLocalTime());

        List<TimeZoneOptionResponse> parisOptions = PortTimeZoneHelper.getSupportedTimeZoneOptions("Paris", "FR");
        assertTrue(parisOptions.stream().anyMatch(opt -> opt.getTimeZoneId().equals("Europe/Paris")));
    }

    @Test
    @DisplayName("PortService.create: Tự động suy luận múi giờ Asia/Tokyo khi tạo cảng ở Nhật Bản mà không cần nhập timeZoneId")
    void testCreatePortAutoResolvesJapanTimeZone() {
        PortCreateRequest request = new PortCreateRequest();
        request.setPortCode("JPYOK");
        request.setPortName("Yokohama Osanbashi Terminal");
        request.setCountryCode("JP");
        request.setTimeZoneId(null); // Không nhập múi giờ
        request.setLatitude(new BigDecimal("35.4522"));
        request.setLongitude(new BigDecimal("139.6486"));

        when(portRepository.existsByPortCode("JPYOK")).thenReturn(false);
        when(portRepository.save(any(Port.class))).thenAnswer(invocation -> {
            Port p = invocation.getArgument(0);
            p.setPortId(UUID.randomUUID());
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        PortResponse response = portService.create(request);

        assertNotNull(response);
        assertEquals("JPYOK", response.getPortCode());
        assertEquals("JP", response.getCountryCode());
        assertEquals("Asia/Tokyo", response.getTimeZoneId());
        assertEquals("+09:00", response.getUtcOffset());
        assertEquals("Asia/Tokyo (UTC+09:00)", response.getFormattedTimeZone());
        assertNotNull(response.getCurrentLocalTime());
    }

    @Test
    @DisplayName("PortService.create: Báo lỗi khi người dùng nhập timeZoneId sai chuẩn IANA")
    void testCreatePortThrowsWhenInvalidTimeZone() {
        PortCreateRequest request = new PortCreateRequest();
        request.setPortCode("JPYOK");
        request.setPortName("Yokohama");
        request.setCountryCode("JP");
        request.setTimeZoneId("GMT+9_Invalid");

        when(portRepository.existsByPortCode("JPYOK")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> portService.create(request));
        assertEquals("INVALID_TIME_ZONE", exception.getCode());
    }

    @Test
    @DisplayName("PortService: Tra cứu và gợi ý múi giờ theo quốc gia thành công")
    void testPortServiceResolveTimeZoneByCountry() {
        TimeZoneOptionResponse res = portService.resolveTimeZoneByCountry("FR");
        assertNotNull(res);
        assertEquals("Europe/Paris", res.getTimeZoneId());
        assertEquals("FR", res.getCountryCode());
        assertTrue(res.isRecommended());
    }

    @Test
    @DisplayName("DestinationService.listPortsByDestination: Trả về danh sách cảng kèm thông tin múi giờ đầy đủ")
    void testListPortsByDestinationEnrichesTimeZones() {
        UUID destinationId = UUID.randomUUID();
        Destination destination = new Destination();
        destination.setDestinationId(destinationId);
        destination.setDestinationName("Tokyo Bay & Kanto");
        destination.setCountryCode("JP");
        destination.setStatus(DestinationStatus.ACTIVE);

        Port portYokohama = new Port();
        portYokohama.setPortId(UUID.randomUUID());
        portYokohama.setPortCode("JPYOK");
        portYokohama.setPortName("Yokohama Port");
        portYokohama.setCountryCode("JP");
        portYokohama.setTimeZoneId("Asia/Tokyo");
        portYokohama.setLatitude(new BigDecimal("35.4522"));
        portYokohama.setLongitude(new BigDecimal("139.6486"));
        portYokohama.setStatus(PortStatus.ACTIVE);
        portYokohama.setDestination(destination);

        Port portTokyo = new Port();
        portTokyo.setPortId(UUID.randomUUID());
        portTokyo.setPortCode("JPTYO");
        portTokyo.setPortName("Tokyo International Cruise Terminal");
        portTokyo.setCountryCode("JP");
        portTokyo.setTimeZoneId("Asia/Tokyo");
        portTokyo.setLatitude(new BigDecimal("35.6194"));
        portTokyo.setLongitude(new BigDecimal("139.7753"));
        portTokyo.setStatus(PortStatus.ACTIVE);
        portTokyo.setDestination(destination);

        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(portRepository.findByDestinationDestinationIdOrderByPortNameAsc(destinationId))
                .thenReturn(List.of(portTokyo, portYokohama));

        List<PortSummaryResponse> ports = destinationService.listPortsByDestination(destinationId);

        assertEquals(2, ports.size());

        PortSummaryResponse p0 = ports.get(0);
        assertEquals("JPTYO", p0.getPortCode());
        assertEquals("Asia/Tokyo", p0.getTimeZoneId());
        assertEquals("+09:00", p0.getUtcOffset());
        assertEquals("Asia/Tokyo (UTC+09:00)", p0.getFormattedTimeZone());
        assertNotNull(p0.getCurrentLocalTime());
        assertEquals(destinationId, p0.getDestinationId());
        assertEquals("Tokyo Bay & Kanto", p0.getDestinationName());

        PortSummaryResponse p1 = ports.get(1);
        assertEquals("JPYOK", p1.getPortCode());
        assertEquals("Asia/Tokyo", p1.getTimeZoneId());
        assertEquals("+09:00", p1.getUtcOffset());
    }

    @Test
    @DisplayName("ItineraryDayService: Kiểm tra hợp lệ khi gán múi giờ tàu cho ngày hành trình")
    void testItineraryDaySetShipTimeZoneValidation() {
        UUID dayId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(UUID.randomUUID());
        itinerary.setStatus(ItineraryStatus.DRAFT);

        ItineraryDay day = new ItineraryDay();
        day.setItineraryDayId(dayId);
        day.setItinerary(itinerary);
        day.setDayNumber(1);
        day.setDate(LocalDate.of(2026, 1, 15));
        day.setDayType(ItineraryDayType.PORT_DAY);

        // Trường hợp nhập múi giờ hợp lệ
        when(itineraryDayRepository.findById(dayId)).thenReturn(Optional.of(day));
        when(itineraryDayRepository.save(any(ItineraryDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItineraryDayResponse validRes = itineraryDayService.setShipTimeZone(dayId, "Europe/Rome");
        assertNotNull(validRes);
        assertEquals("Europe/Rome", validRes.getShipTimeZoneId());
        assertNotNull(validRes.getShipUtcOffset());

        // Trường hợp nhập múi giờ sai
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryDayService.setShipTimeZone(dayId, "Invalid/Mars_Time"));
        assertEquals("INVALID_TIME_ZONE", ex.getCode());
    }

    @Test
    @DisplayName("ItineraryDayService.syncShipTimeZonesFromStops: Đồng bộ múi giờ tự động cho hải trình đa quốc gia (Singapore -> VN -> HK -> JP)")
    void testSyncShipTimeZonesForMultiCountryItinerary() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.DRAFT);

        // Cảng Singapore
        Port portSingapore = new Port();
        portSingapore.setPortCode("SGSIN");
        portSingapore.setTimeZoneId("Asia/Singapore");

        // Cảng Việt Nam (Hạ Long)
        Port portHalong = new Port();
        portHalong.setPortCode("VNHPH");
        portHalong.setTimeZoneId("Asia/Ho_Chi_Minh");

        // Cảng Nhật Bản (Tokyo)
        Port portTokyo = new Port();
        portTokyo.setPortCode("JPTYO");
        portTokyo.setTimeZoneId("Asia/Tokyo");

        // Ngày 1: Xuất phát Singapore (PORT_DAY)
        ItineraryDay day1 = new ItineraryDay();
        day1.setItineraryDayId(UUID.randomUUID());
        day1.setItinerary(itinerary);
        day1.setDayNumber(1);
        day1.setDate(LocalDate.of(2026, 3, 1));
        day1.setDayType(ItineraryDayType.PORT_DAY);
        CruiseStop stop1 = new CruiseStop();
        stop1.setPort(portSingapore);
        day1.getArrivalStops().add(stop1);

        // Ngày 2: Trên biển đến VN (SEA_DAY - không có stop)
        ItineraryDay day2 = new ItineraryDay();
        day2.setItineraryDayId(UUID.randomUUID());
        day2.setItinerary(itinerary);
        day2.setDayNumber(2);
        day2.setDate(LocalDate.of(2026, 3, 2));

        // Ngày 3: Cập cảng Hạ Long (PORT_DAY)
        ItineraryDay day3 = new ItineraryDay();
        day3.setItineraryDayId(UUID.randomUUID());
        day3.setItinerary(itinerary);
        day3.setDayNumber(3);
        day3.setDate(LocalDate.of(2026, 3, 3));
        CruiseStop stop3 = new CruiseStop();
        stop3.setPort(portHalong);
        day3.getArrivalStops().add(stop3);

        // Ngày 4: Cập cảng Tokyo (PORT_DAY)
        ItineraryDay day4 = new ItineraryDay();
        day4.setItineraryDayId(UUID.randomUUID());
        day4.setItinerary(itinerary);
        day4.setDayNumber(4);
        day4.setDate(LocalDate.of(2026, 3, 4));
        CruiseStop stop4 = new CruiseStop();
        stop4.setPort(portTokyo);
        day4.getArrivalStops().add(stop4);

        List<ItineraryDay> days = List.of(day1, day2, day3, day4);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));
        when(itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itineraryId)).thenReturn(days);
        when(itineraryDayRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItineraryDayResponse> synced = itineraryDayService.syncShipTimeZonesFromStops(itineraryId);

        assertEquals(4, synced.size());

        // Day 1: Singapore (UTC+8)
        assertEquals("Asia/Singapore", synced.get(0).getShipTimeZoneId());
        assertEquals(ItineraryDayType.PORT_DAY, synced.get(0).getDayType());

        // Day 2: Sea Day (Kế thừa Asia/Singapore)
        assertEquals("Asia/Singapore", synced.get(1).getShipTimeZoneId());
        assertEquals(ItineraryDayType.SEA_DAY, synced.get(1).getDayType());

        // Day 3: Việt Nam (UTC+7)
        assertEquals("Asia/Ho_Chi_Minh", synced.get(2).getShipTimeZoneId());
        assertEquals(ItineraryDayType.PORT_DAY, synced.get(2).getDayType());

        // Day 4: Nhật Bản (UTC+9)
        assertEquals("Asia/Tokyo", synced.get(3).getShipTimeZoneId());
        assertEquals(ItineraryDayType.PORT_DAY, synced.get(3).getDayType());
    }
}
