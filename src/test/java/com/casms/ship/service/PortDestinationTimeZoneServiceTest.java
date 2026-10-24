package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.PortCreateRequest;
import com.casms.ship.dto.response.PortResponse;
import com.casms.ship.dto.response.PortSummaryResponse;
import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.DestinationStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.DestinationRepository;
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

    @InjectMocks
    private PortService portService;

    @InjectMocks
    private DestinationService destinationService;

    @Test
    @DisplayName("PortTimeZoneHelper: Suy luận múi giờ cho Nhật Bản (JP), Việt Nam (VN), Singapore (SG)")
    void testTimeZoneHelperAutoResolution() {
        assertEquals("Asia/Tokyo", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("JP").orElseThrow());
        assertEquals("Asia/Tokyo", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("jp").orElseThrow());
        assertEquals("Asia/Ho_Chi_Minh", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("VN").orElseThrow());
        assertEquals("Asia/Singapore", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("SG").orElseThrow());
        assertEquals("Asia/Seoul", PortTimeZoneHelper.resolveDefaultTimeZoneByCountry("KR").orElseThrow());

        assertTrue(PortTimeZoneHelper.isValidTimeZoneId("Asia/Tokyo"));
        assertTrue(PortTimeZoneHelper.isValidTimeZoneId("Asia/Ho_Chi_Minh"));
        assertFalse(PortTimeZoneHelper.isValidTimeZoneId("Invalid/TimeZone"));

        assertEquals("+09:00", PortTimeZoneHelper.calculateUtcOffset("Asia/Tokyo"));
        assertEquals("+07:00", PortTimeZoneHelper.calculateUtcOffset("Asia/Ho_Chi_Minh"));
        assertEquals("+08:00", PortTimeZoneHelper.calculateUtcOffset("Asia/Singapore"));
        assertEquals("Asia/Tokyo (UTC+09:00)", PortTimeZoneHelper.formatTimeZoneDisplay("Asia/Tokyo"));
        assertNotNull(PortTimeZoneHelper.getCurrentLocalTimeIso("Asia/Tokyo"));
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
}
