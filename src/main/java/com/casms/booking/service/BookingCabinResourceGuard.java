package com.casms.booking.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.CabinUpdateRequest;
import com.casms.ship.entity.Cabin;
import com.casms.ship.enums.CabinStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Separate integration guard. Protected 3D/import/geometry/mapping source and job transactions stay untouched. */
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class BookingCabinResourceGuard {
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final PlatformTransactionManager transactions;
    private final ThreadLocal<UUID> importingShip = new ThreadLocal<>();

    @Around("execution(public * com.casms.ship.service.ShipModelImportService.importModel(..))")
    public Object importContext(ProceedingJoinPoint call) throws Throwable {
        UUID previous = importingShip.get();
        importingShip.set((UUID) call.getArgs()[0]);
        try {
            return call.proceed();
        } finally {
            if (previous == null) {
                importingShip.remove();
            } else {
                importingShip.set(previous);
            }
        }
    }

    /** Runs inside the importer's existing TransactionTemplate, after its existing pessimistic Ship lock. */
    @Around("execution(* com.casms.ship.repository.ShipModelRepository.lockShip(..))")
    public Object lockedImportBoundary(ProceedingJoinPoint call) throws Throwable {
        Object result = call.proceed();
        UUID shipId = importingShip.get();
        if (shipId == null || !shipId.equals(call.getArgs()[0]) || (result instanceof Optional<?> value && value.isEmpty())) {
            return result;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "CABIN_RESOURCE_GUARD_UNAVAILABLE",
                    "Chưa xác định được transaction bảo vệ tài nguyên cabin; không tiếp tục import.");
        }
        requireNoFareCommitments(shipId, "MODEL_IMPORT");
        return result;
    }

    @Around("execution(public * com.casms.ship.service.CabinService.update(..)) || "
            + "execution(public * com.casms.ship.service.CabinService.deleteUnused(..)) || "
            + "execution(public * com.casms.ship.service.CabinService.changeOperationalStatus(..)) || "
            + "execution(public * com.casms.ship.service.CabinService.blockCabin(..))")
    public Object cabinBusinessMutation(ProceedingJoinPoint call) throws Throwable {
        try {
            return new TransactionTemplate(transactions).execute(tx -> {
                UUID cabinId = (UUID) call.getArgs()[0];
                var rows = jdbc.queryForList("SELECT ship_id,capacity,status FROM \"Cabin\" WHERE cabin_id=? FOR UPDATE", cabinId);
                if (rows.isEmpty()) {
                    throw new ResourceNotFoundException("Cabin", cabinId);
                }
                var row = rows.getFirst();
                String operation = call.getSignature().getName();
                boolean reduces = isReducingOperation(operation, row, call.getArgs());
                if (reduces) {
                    requireNoFareCommitments((UUID) row.get("ship_id"), operation);
                }
                // Original business service reads a fresh entity after the row lock; never overwrite model-managed fields.
                var cabin = em.find(Cabin.class, cabinId);
                if (cabin != null) {
                    em.refresh(cabin);
                }
                try {
                    return call.proceed();
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new InvocationFailure(failure);
                }
            });
        } catch (InvocationFailure failure) {
            throw failure.getCause();
        }
    }

    private static boolean isReducingOperation(String operation, Map<String, Object> row, Object[] args) {
        if ("deleteUnused".equals(operation)) {
            return true;
        }
        if ("update".equals(operation)) {
            Integer capacity = ((CabinUpdateRequest) args[1]).getCapacity();
            return capacity != null && capacity > 0 && capacity < ((Number) row.get("capacity")).intValue();
        }
        if ("blockCabin".equals(operation) || "changeOperationalStatus".equals(operation)) {
            var status = (CabinStatus) args[1];
            return status != null && !status.name().equals(row.get("status").toString())
                    && (status == CabinStatus.MAINTENANCE || status == CabinStatus.OUT_OF_SERVICE);
        }
        return false;
    }

    private void requireNoFareCommitments(UUID shipId, String operation) {
        long count = Objects.requireNonNull(jdbc.queryForObject("""
                SELECT count(*) FROM "BookingPassenger" p JOIN "Booking" b USING(booking_id) JOIN "Cruise" c USING(cruise_id)
                WHERE c.ship_id=? AND c.status NOT IN ('COMPLETED','CANCELLED') AND p.fare_offer_id IS NOT NULL
                  AND p.participation_status='Active' AND (b.booking_status='Confirmed'
                    OR (b.booking_status='Draft' AND (b.hold_expires_at IS NULL OR b.hold_expires_at>now())))
                """, Long.class, shipId));
        if (count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "CABIN_RESOURCE_DEPENDENCY_BLOCKED",
                    "Có nhu cầu cabin của gói vé đang giữ hoặc đã xác nhận; chưa chứng minh an toàn khi giảm/chuyển cấu hình tài nguyên cabin.",
                    Map.of("shipId", shipId, "operation", operation, "passengerCommitments", count,
                            "blockingDependency", "FARE_CABIN_RESOURCES", "compatibilityProven", false));
        }
    }

    private static final class InvocationFailure extends RuntimeException {
        InvocationFailure(Throwable cause) {
            super(cause);
        }
    }
}
