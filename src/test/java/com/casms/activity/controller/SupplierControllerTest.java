package com.casms.activity.controller;

import com.casms.activity.dto.request.SupplierRequest;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.service.SupplierService;
import com.casms.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierControllerTest {

    @Mock
    private SupplierService supplierService;

    @InjectMocks
    private SupplierController supplierController;

    private UUID supplierId;
    private Supplier sampleSupplier;

    @BeforeEach
    void setUp() {
        supplierId = UUID.randomUUID();
        sampleSupplier = new Supplier();
        sampleSupplier.setSupplierId(supplierId);
        sampleSupplier.setSupplierName("Ha Long Bay Tour Ltd");
        sampleSupplier.setContactName("Nguyen Van A");
        sampleSupplier.setStatus(SupplierStatus.ACTIVE);
    }

    @Test
    void list_returnsApiResponseWithSupplierList() {
        when(supplierService.list()).thenReturn(List.of(sampleSupplier));

        ResponseEntity<ApiResponse<List<Supplier>>> response = supplierController.list();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Ha Long Bay Tour Ltd", response.getBody().getData().get(0).getSupplierName());
        verify(supplierService).list();
    }

    @Test
    void detail_returnsApiResponseWithSupplier() {
        when(supplierService.detail(supplierId)).thenReturn(sampleSupplier);

        ResponseEntity<ApiResponse<Supplier>> response = supplierController.detail(supplierId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());
        assertEquals(supplierId, response.getBody().getData().getSupplierId());
        assertEquals("Ha Long Bay Tour Ltd", response.getBody().getData().getSupplierName());
        verify(supplierService).detail(supplierId);
    }

    @Test
    void create_returnsCreatedStatusWithApiResponse() {
        SupplierRequest request = new SupplierRequest(
                "Ha Long Bay Tour Ltd",
                "EXCURSION",
                "Nguyen Van A",
                "0987654321",
                "contact@halongtour.com",
                "LIC-12345"
        );
        when(supplierService.create(any(SupplierRequest.class))).thenReturn(sampleSupplier);

        ResponseEntity<ApiResponse<Supplier>> response = supplierController.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Ha Long Bay Tour Ltd", response.getBody().getData().getSupplierName());
        verify(supplierService).create(request);
    }

    @Test
    void update_returnsApiResponseWithUpdatedSupplier() {
        SupplierRequest request = new SupplierRequest(
                "Ha Long Bay Tour Updated",
                "EXCURSION",
                "Nguyen Van A",
                "0987654321",
                "contact@halongtour.com",
                "LIC-12345"
        );
        sampleSupplier.setSupplierName("Ha Long Bay Tour Updated");
        when(supplierService.update(eq(supplierId), any(SupplierRequest.class))).thenReturn(sampleSupplier);

        ResponseEntity<ApiResponse<Supplier>> response = supplierController.update(supplierId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Ha Long Bay Tour Updated", response.getBody().getData().getSupplierName());
        verify(supplierService).update(supplierId, request);
    }

    @Test
    void deleteUnused_returnsApiResponseSuccess() {
        doNothing().when(supplierService).deleteUnused(supplierId);

        ResponseEntity<ApiResponse<Void>> response = supplierController.deleteUnused(supplierId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Deleted unused supplier successfully", response.getBody().getMessage());
        verify(supplierService).deleteUnused(supplierId);
    }

    @Test
    void activate_returnsApiResponseWithActiveSupplier() {
        when(supplierService.activate(supplierId)).thenReturn(sampleSupplier);

        ResponseEntity<ApiResponse<Supplier>> response = supplierController.activate(supplierId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(SupplierStatus.ACTIVE, response.getBody().getData().getStatus());
        verify(supplierService).activate(supplierId);
    }

    @Test
    void deactivate_returnsApiResponseWithInactiveSupplier() {
        sampleSupplier.setStatus(SupplierStatus.INACTIVE);
        when(supplierService.deactivate(supplierId)).thenReturn(sampleSupplier);

        ResponseEntity<ApiResponse<Supplier>> response = supplierController.deactivate(supplierId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(SupplierStatus.INACTIVE, response.getBody().getData().getStatus());
        verify(supplierService).deactivate(supplierId);
    }

    @Test
    void validate_returnsApiResponseWithValidMessage() {
        doNothing().when(supplierService).validateSupplier(supplierId);

        ResponseEntity<ApiResponse<Map<String, String>>> response = supplierController.validate(supplierId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Supplier information is valid", response.getBody().getData().get("message"));
        verify(supplierService).validateSupplier(supplierId);
    }
}
