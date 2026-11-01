package com.casms.trip.service;

import com.casms.booking.dto.request.BookingCreateRequest;
import com.casms.trip.entity.IdempotencyRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyCanonicalPayloadTest {
    @Test void separateDtosAndReorderedObjectKeysHaveStableHashesButChangedValuesDoNot(){
        var reservations=mock(IdempotencyReservationService.class);
        when(reservations.reserveKey(any(),any(),any(),any())).thenReturn(new IdempotencyReservationService.ReservationResult(IdempotencyReservationService.ReservationStatus.RESERVED,new IdempotencyRecord()));
        var service=new IdempotencyService(reservations,new ObjectMapper());var actor=UUID.randomUUID();var cruise=UUID.randomUUID();
        var first=new BookingCreateRequest();first.setCruiseId(cruise);first.setCurrency("USD");
        var second=new BookingCreateRequest();second.setCruiseId(cruise);second.setCurrency("USD");
        service.execute("dto",actor,"CREATE_BOOKING",first,()->Map.of("result","ok"));
        service.execute("dto",actor,"CREATE_BOOKING",second,()->Map.of("result","ok"));
        var left=new LinkedHashMap<String,Object>();left.put("a",1);left.put("b",List.of(2,3));
        var right=new LinkedHashMap<String,Object>();right.put("b",List.of(2,3));right.put("a",1);
        service.execute("map",actor,"TEST",left,()->"ok");service.execute("map",actor,"TEST",right,()->"ok");
        right.put("a",2);service.execute("map",actor,"TEST",right,()->"ok");
        var hashes=ArgumentCaptor.forClass(String.class);verify(reservations,times(5)).reserveKey(eq(actor),any(),any(),hashes.capture());
        assertEquals(hashes.getAllValues().get(0),hashes.getAllValues().get(1));assertEquals(hashes.getAllValues().get(2),hashes.getAllValues().get(3));assertNotEquals(hashes.getAllValues().get(3),hashes.getAllValues().get(4));
    }
}
