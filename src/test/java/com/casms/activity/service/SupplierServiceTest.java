package com.casms.activity.service;

import com.casms.activity.dto.request.SupplierRequest;
import com.casms.activity.dto.request.SupplierVerificationRequest;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ShoreExcursionRepository shoreExcursionRepository;

    @InjectMocks
    private SupplierService supplierService;

    private UUID supplierId;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        supplierId = UUID.randomUUID();

        supplier = new Supplier();
        supplier.setSupplierName("Local Travel");
        supplier.setContactName("Nguyen Van A");
        supplier.setStatus(SupplierStatus.INACTIVE);
        supplier.setVerificationStatus(
                SupplierVerificationStatus.PENDING
        );
    }

    @Test
    void list_returnsAllSuppliers() {
        when(supplierRepository.findAll()).thenReturn(List.of(supplier));

        List<Supplier> result = supplierService.list();

        assertEquals(1, result.size());
        verify(supplierRepository).findAll();
    }

    @Test
    void detail_existingSupplier_returnsSupplier() {
        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.of(supplier));

        assertSame(supplier, supplierService.detail(supplierId));
    }

    @Test
    void detail_missingSupplier_throwsException() {
        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> supplierService.detail(supplierId)
        );
    }

    @Test
    void detail_nullId_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> supplierService.detail(null)
        );
    }

    @Test
    void deleteUnused_supplierInUse_throwsException() {
        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.of(supplier));
        when(shoreExcursionRepository
                .existsBySupplier_SupplierId(supplierId))
                .thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> supplierService.deleteUnused(supplierId)
        );

        verify(supplierRepository, never()).delete(any(Supplier.class));
    }

    @Test
    void deleteUnused_supplierNotInUse_deletesSupplier() {
        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.of(supplier));
        when(shoreExcursionRepository
                .existsBySupplier_SupplierId(supplierId))
                .thenReturn(false);

        supplierService.deleteUnused(supplierId);

        verify(supplierRepository).delete(supplier);
    }

    @Test
    void activate_unverifiedSupplier_throwsException() {
        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.of(supplier));

        assertThrows(
                IllegalStateException.class,
                () -> supplierService.activate(supplierId)
        );

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    void activate_verifiedSupplier_setsActiveStatus() {
        supplier.setVerificationStatus(
                SupplierVerificationStatus.VERIFIED
        );

        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.of(supplier));
        when(supplierRepository.save(supplier)).thenReturn(supplier);

        Supplier result = supplierService.activate(supplierId);

        assertEquals(SupplierStatus.ACTIVE, result.getStatus());
        verify(supplierRepository).save(supplier);
    }

    @Test
    void deactivate_activeSupplier_setsInactiveStatus() {
        supplier.setStatus(SupplierStatus.ACTIVE);

        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.of(supplier));
        when(supplierRepository.save(supplier)).thenReturn(supplier);

        Supplier result = supplierService.deactivate(supplierId);

        assertEquals(SupplierStatus.INACTIVE, result.getStatus());
    }

    @Test
    void validateSupplier_missingRequiredName_throwsException() {
        supplier.setSupplierName(" ");

        when(supplierRepository.findById(supplierId))
                .thenReturn(Optional.of(supplier));

        assertThrows(
                IllegalStateException.class,
                () -> supplierService.validateSupplier(supplierId)
        );
    }
}