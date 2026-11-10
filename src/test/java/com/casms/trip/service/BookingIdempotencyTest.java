package com.casms.trip.service;

import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.common.exception.BusinessException;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingIdempotencyTest {
    @Test void completionFailureIsPropagatedToRollBackBooking() {
        var reservations = mock(IdempotencyReservationService.class);
        var record = new IdempotencyRecord(); record.setIdempotencyId(UUID.randomUUID());
        when(reservations.reserveBookingKey(isNull(), eq("key"), anyString()))
                .thenReturn(new IdempotencyReservationService.ReservationResult(IdempotencyReservationService.ReservationStatus.RESERVED, record));
        doThrow(new IllegalStateException("write failed")).when(reservations)
                .completeKeyInCurrentTransaction(any(), any(), any());
        var service = new IdempotencyService(reservations, new ObjectMapper());
        var result = new BookingDetailResponse(); result.setBookingId(UUID.randomUUID());
        assertEquals("IDEMPOTENCY_COMPLETION_FAILED", assertThrows(BusinessException.class,
                () -> service.executeBookingCreate("key", null, Map.of("passenger", "one"), () -> result)).getCode());
        verify(reservations, never()).completeKey(any(), any(), any());
        verify(reservations, never()).releaseKey(any());
    }

    @Test void staleBookingReservationIsNeverDeletedOrExecutedAgain() {
        var repo = mock(IdempotencyRecordRepository.class);
        var record = new IdempotencyRecord(); record.setRequestHash("hash");
        record.setCreatedAt(Instant.now().minusSeconds(3600));
        when(repo.findByActorIdAndOperationAndIdempotencyKey(null, "CREATE_BOOKING", "key"))
                .thenReturn(Optional.of(record));
        var result = new IdempotencyReservationService(repo).checkStatus(null, "CREATE_BOOKING", "key", "hash");
        assertEquals(IdempotencyReservationService.ReservationStatus.IN_PROGRESS, result.getStatus());
        verify(repo, never()).delete(any());
    }
}
