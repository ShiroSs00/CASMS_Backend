package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.Port;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripPlanStop;
import com.casms.trip.entity.TripScheduleVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TripGeographyCatalogTest {
    @Test
    void createsActiveCountryWithStandardCodeAndTrimmedLabel() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var catalog = new TripGeographyCatalog(jdbc);
        when(jdbc.update(contains("ON CONFLICT (kind, code) DO NOTHING"),
                eq("KR"), eq("Hàn Quốc"))).thenReturn(1);

        var created = catalog.createCountry(" kr ", " Hàn Quốc ");

        assertEquals(new TripGeographyCatalog.Item(
                TripGeographyCatalog.Kind.COUNTRY, "KR", "Hàn Quốc", null,
                TripGeographyCatalog.Status.ACTIVE), created);
        verify(jdbc).update(contains("COALESCE(MAX(display_order), 0) + 1"),
                eq("KR"), eq("Hàn Quốc"));
    }

    @Test
    void rejectsInvalidIsoCodeAndLabelBeforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var catalog = new TripGeographyCatalog(jdbc);

        for (String code : List.of("ZZ", "VNM", "ß", " ")) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> catalog.createCountry(code, "Tên quốc gia"));
            assertEquals("INVALID_COUNTRY_CODE", error.getCode());
        }
        for (String label : List.of(" ", "a".repeat(101), "abc" + (char) 0)) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> catalog.createCountry("KR", label));
            assertEquals("INVALID_COUNTRY_LABEL", error.getCode());
        }
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void existingCountryReturnsConflictWithoutUpdatingItsStatusOrLabel() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var catalog = new TripGeographyCatalog(jdbc);
        when(jdbc.update(contains("ON CONFLICT (kind, code) DO NOTHING"),
                eq("VN"), eq("Tên thay thế"))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> catalog.createCountry(" vn ", "Tên thay thế"));

        assertEquals("COUNTRY_ALREADY_EXISTS", error.getCode());
        verify(jdbc).update(contains("ON CONFLICT (kind, code) DO NOTHING"),
                eq("VN"), eq("Tên thay thế"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void turningOffVietnamTurnsOffAllRegions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TripGeographyCatalog catalog = new TripGeographyCatalog(jdbc);
        when(jdbc.queryForObject(contains("FOR UPDATE"), eq(String.class))).thenReturn("ACTIVE");
        when(jdbc.update(anyString(), eq("INACTIVE"), eq("COUNTRY"), eq("VN"))).thenReturn(1);
        TripGeographyCatalog.Item vietnam = new TripGeographyCatalog.Item(
                TripGeographyCatalog.Kind.COUNTRY, "VN", "Việt Nam", null,
                TripGeographyCatalog.Status.INACTIVE);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<TripGeographyCatalog.Item>>any(), eq(false)))
                .thenReturn(List.of(vietnam));

        assertEquals(vietnam, catalog.changeStatus(
                TripGeographyCatalog.Kind.COUNTRY, " vn ", TripGeographyCatalog.Status.INACTIVE));
        verify(jdbc).update("UPDATE trip_geography_catalog SET status = 'INACTIVE' WHERE kind = 'REGION'");
    }

    @Test
    void regionCannotBeEnabledWhileVietnamIsDisabled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TripGeographyCatalog catalog = new TripGeographyCatalog(jdbc);
        when(jdbc.queryForObject(contains("FOR UPDATE"), eq(String.class))).thenReturn("INACTIVE");

        BusinessException error = assertThrows(BusinessException.class, () -> catalog.changeStatus(
                TripGeographyCatalog.Kind.REGION, "BAC_BO", TripGeographyCatalog.Status.ACTIVE));
        assertEquals("PARENT_COUNTRY_INACTIVE", error.getCode());
        verify(jdbc).queryForObject(contains("FOR UPDATE"), eq(String.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void domesticTripChecksVietnamAndSelectedRegion() {
        Trip trip = new Trip();
        trip.setPlannedScope("DOMESTIC");
        trip.setDomesticRegions(List.of("BAC_BO"));
        var selection = new TripGeographyCatalog.Selection(Set.of("SG"), Set.of("NAM_BO"));

        assertEquals(List.of("VN", "BAC_BO"), selection.unavailable(trip));
        BusinessException error = assertThrows(BusinessException.class, () -> selection.requireAvailable(trip));
        assertEquals("TRIP_GEOGRAPHY_UNAVAILABLE", error.getCode());
    }

    @Test
    void draftAlsoChecksPortsAlreadyChosen() {
        Trip trip = new Trip();
        trip.setPlannedScope("INTERNATIONAL");
        trip.setCountryCodes(List.of("SG"));
        Port port = new Port();
        port.setCountryCode("JP");
        TripPlanStop stop = new TripPlanStop();
        stop.setPort(port);
        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setStops(List.of(stop));

        var selection = new TripGeographyCatalog.Selection(Set.of("SG"), Set.of());
        assertEquals(List.of(), selection.unavailable(trip));
        assertEquals(List.of("JP"), selection.unavailable(version));
    }
}
