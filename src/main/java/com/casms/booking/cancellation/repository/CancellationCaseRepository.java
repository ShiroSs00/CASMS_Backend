package com.casms.booking.cancellation.repository;

import com.casms.booking.cancellation.entity.CancellationCase;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationCaseRepository extends JpaRepository<CancellationCase, UUID> {
    Optional<CancellationCase> findByBookingIdAndRequestedByAndIdempotencyKey(
            UUID bookingId, UUID requestedBy, String idempotencyKey);
    boolean existsByBookingIdAndStatusIn(UUID bookingId, Collection<CancellationCase.Status> statuses);
    List<CancellationCase> findByBookingIdOrderByRequestedAtDescCaseIdDesc(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CancellationCase c where c.caseId = :id")
    Optional<CancellationCase> lockById(@Param("id") UUID id);

    Page<CancellationCase> findByStatus(CancellationCase.Status status, Pageable pageable);

    @Query("""
            select c from CancellationCase c, OperationalIssue i
            left join i.assignedTo assignee left join i.verifiedBy verifier
            where c.issueId = i.issueId and c.status in :statuses
              and c.issueId = coalesce(:issueId, c.issueId)
              and (assignee.userId = :actor or verifier.userId = :actor)
            """)
    Page<CancellationCase> findAssignedOperationalQueue(@Param("actor") UUID actor,
            @Param("statuses") Collection<CancellationCase.Status> statuses,
            @Param("issueId") UUID issueId, Pageable pageable);
}
