package com.casms.activity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.casms.activity.controller.ActivityController.ActivityCatalogRequest;
import com.casms.activity.controller.ActivityController.ActivityScheduleRequest;
import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ActivityCatalogScheduleTest {
    @Mock OnboardActivityRepository activities;
    @Mock CruiseRepository cruises;
    @Mock BusinessCodeGenerator businessCodeGenerator;
    @InjectMocks ActivityService service;

    @Test void schedulingCopiesMetadataWithoutMutatingReusableSource() {
        var source = new OnboardActivity(); source.setOnboardActivityId(UUID.randomUUID());
        source.setCatalogEntry(true); source.setStatus(OnboardActivityStatus.PLANNED);
        source.setActivityName("Yoga"); source.setCapacity(20); source.setCurrencyCode("USD"); source.setPrice(BigDecimal.TEN);
        var cruise = new Cruise(); cruise.setCruiseId(UUID.randomUUID());
        cruise.setDepartureDatetime(Instant.parse("2030-01-01T00:00:00Z"));
        cruise.setReturnDatetime(Instant.parse("2030-01-02T00:00:00Z"));
        when(activities.lockActivity(source.getOnboardActivityId())).thenReturn(Optional.of(source));
        when(cruises.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        when(businessCodeGenerator.next("AC")).thenReturn("AC-TEST");
        when(activities.save(any())).thenAnswer(i -> i.getArgument(0));
        var input = new ActivityScheduleRequest(source.getOnboardActivityId(),
                Instant.parse("2030-01-01T01:00:00Z"), Instant.parse("2030-01-01T02:00:00Z"), "Deck 1", null, null, 15);
        var occurrence = service.scheduleCatalog(cruise.getCruiseId(), input);
        assertFalse(occurrence.isCatalogEntry()); assertEquals(source.getOnboardActivityId(), occurrence.getSourceActivityId());
        assertSame(cruise, occurrence.getCruise()); assertEquals(15, occurrence.getCapacity());
        assertEquals(BigDecimal.TEN, occurrence.getPrice());
        assertNull(source.getCruise()); assertNull(source.getStartTime()); assertEquals(20, source.getCapacity());
    }

    @Test void catalogContractRejectsScheduleFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var input = new ActivityCatalogRequest("Yoga", null, null, 20, BigDecimal.ZERO, "USD",
                    Instant.now(), null, null, null, null, null, null);
            assertTrue(factory.getValidator().validate(input).stream().anyMatch(v -> v.getPropertyPath().toString().equals("startTime")));
        }
    }
}
