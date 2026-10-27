package com.casms.ship.repository;

import com.casms.ship.enums.PortStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class PortRepositoryPostgresIntegrationTest {

    @Autowired
    private PortRepository portRepository;

    @Test
    void testFindPortsWithFilters_NullSearch() {
        var page = portRepository.findPortsWithFilters(null, null, null, PageRequest.of(0, 10));
        assertNotNull(page);
    }

    @Test
    void testFindActivePortsWithFilters_NullSearch() {
        var list = portRepository.findActivePortsWithFilters(null, null);
        assertNotNull(list);
    }

    @Test
    void testFindPortsWithFilters_WithSearch() {
        var page = portRepository.findPortsWithFilters(List.of("VN"), PortStatus.ACTIVE, "port", PageRequest.of(0, 10));
        assertNotNull(page);
    }

    @Test
    void testFindPortsWithFilters_MultipleCountries() {
        var page = portRepository.findPortsWithFilters(List.of("VN", "TH", "SG"), null, null, PageRequest.of(0, 10));
        assertNotNull(page);
    }

    @Test
    void testFindActivePortsWithFilters_MultipleCountries() {
        var list = portRepository.findActivePortsWithFilters(List.of("VN", "TH"), "port");
        assertNotNull(list);
    }

    @Test
    void testFindPortsWithFilters_EmptyCountryList() {
        var page = portRepository.findPortsWithFilters(List.of(), null, null, PageRequest.of(0, 10));
        assertNotNull(page);
    }
}
