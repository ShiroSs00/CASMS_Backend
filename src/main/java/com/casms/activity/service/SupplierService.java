package com.casms.activity.service;

import com.casms.activity.dto.request.SupplierRequest;
import com.casms.activity.dto.request.SupplierVerificationRequest;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;

    public SupplierService(
            SupplierRepository supplierRepository,
            ShoreExcursionRepository shoreExcursionRepository
    ) {
        this.supplierRepository = supplierRepository;
        this.shoreExcursionRepository = shoreExcursionRepository;
    }

    // CREATE
    public Supplier create(SupplierRequest request) {
        validateRequest(request);

        String name = request.supplierName().trim();

        if (supplierRepository.existsBySupplierNameIgnoreCase(name)) {
            throw new IllegalStateException(
                    "Supplier name already exists"
            );
        }

        Supplier supplier = new Supplier();
        applyRequest(supplier, request);

        supplier.setStatus(SupplierStatus.INACTIVE);
        supplier.setVerificationStatus(
                SupplierVerificationStatus.PENDING
        );

        return supplierRepository.save(supplier);
    }

    // READ ALL
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Supplier> list() {
        return supplierRepository.findAll();
    }

    // READ DETAIL
    @Transactional(Transactional.TxType.SUPPORTS)
    public Supplier detail(UUID supplierId) {
        return findSupplier(supplierId);
    }

    // UPDATE
    public Supplier update(
            UUID supplierId,
            SupplierRequest request
    ) {
        validateRequest(request);

        Supplier supplier = findSupplier(supplierId);
        String name = request.supplierName().trim();

        if (supplierRepository
                .existsBySupplierNameIgnoreCaseAndSupplierIdNot(
                        name, supplierId)) {
            throw new IllegalStateException(
                    "Supplier name already exists"
            );
        }

        applyRequest(supplier, request);
        return supplierRepository.save(supplier);
    }

    // DELETE ONLY WHEN UNUSED
    public void deleteUnused(UUID supplierId) {
        Supplier supplier = findSupplier(supplierId);

        if (shoreExcursionRepository
                .existsBySupplier_SupplierId(supplierId)) {
            throw new IllegalStateException(
                    "Cannot delete supplier because it is referenced "
                            + "by a shore excursion"
            );
        }

        supplierRepository.delete(supplier);
    }

    // ACTIVATE
    public Supplier activate(UUID supplierId) {
        Supplier supplier = findSupplier(supplierId);

        validateSupplier(supplierId);

        if (supplier.getVerificationStatus()
                != SupplierVerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Supplier must be verified before activation"
            );
        }

        supplier.setStatus(SupplierStatus.ACTIVE);
        return supplierRepository.save(supplier);
    }

    // DEACTIVATE
    public Supplier deactivate(UUID supplierId) {
        Supplier supplier = findSupplier(supplierId);

        supplier.setStatus(SupplierStatus.INACTIVE);
        return supplierRepository.save(supplier);
    }

    // RECORD EMPLOYEE VERIFICATION RESULT
    public Supplier recordVerification(
            UUID supplierId,
            SupplierVerificationRequest request,
            UUID employeeId
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Verification request must not be null"
            );
        }

        if (employeeId == null) {
            throw new IllegalArgumentException(
                    "Employee ID must not be null"
            );
        }

        Supplier supplier = findSupplier(supplierId);

        validateSupplier(supplierId);

        SupplierVerificationStatus newStatus =
                request.verificationStatus();

        supplier.setVerificationStatus(newStatus);
        supplier.setVerificationNotes(
                request.verificationNotes()
        );
        supplier.setVerifiedBy(employeeId);
        supplier.setVerifiedAt(Instant.now());

        // Nếu kết quả xác minh không còn hợp lệ,
        // không để nhà cung cấp tiếp tục ở trạng thái ACTIVE.
        if (newStatus != SupplierVerificationStatus.VERIFIED) {
            supplier.setStatus(SupplierStatus.INACTIVE);
        }

        return supplierRepository.save(supplier);
    }

    // VALIDATE SUPPLIER INFORMATION
    @Transactional(Transactional.TxType.SUPPORTS)
    public void validateSupplier(UUID supplierId) {
        Supplier supplier = findSupplier(supplierId);

        validateRequiredFields(supplier);
    }

    private Supplier findSupplier(UUID supplierId) {
        if (supplierId == null) {
            throw new IllegalArgumentException(
                    "Supplier ID must not be null"
            );
        }

        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Supplier not found: " + supplierId
                ));
    }

    private void validateRequest(SupplierRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Supplier request must not be null"
            );
        }

        if (request.supplierName() == null
                || request.supplierName().isBlank()) {
            throw new IllegalArgumentException(
                    "Supplier name is required"
            );
        }

        if (request.contactName() == null
                || request.contactName().isBlank()) {
            throw new IllegalArgumentException(
                    "Contact name is required"
            );
        }
    }

    private void validateRequiredFields(Supplier supplier) {
        if (supplier.getSupplierName() == null
                || supplier.getSupplierName().isBlank()) {
            throw new IllegalStateException(
                    "Supplier name is required"
            );
        }

        if (supplier.getContactName() == null
                || supplier.getContactName().isBlank()) {
            throw new IllegalStateException(
                    "Contact name is required"
            );
        }
    }

    private void applyRequest(
            Supplier supplier,
            SupplierRequest request
    ) {
        supplier.setSupplierName(
                request.supplierName().trim()
        );
        supplier.setSupplierType(request.supplierType());
        supplier.setContactName(request.contactName().trim());
        supplier.setContactPhone(request.contactPhone());
        supplier.setContactEmail(request.contactEmail());
        supplier.setNotes(request.notes());
    }
}
