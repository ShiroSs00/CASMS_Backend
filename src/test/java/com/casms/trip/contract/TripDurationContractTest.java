package com.casms.trip.contract;

import com.casms.common.exception.GlobalExceptionHandler;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.repository.UserRepository;
import com.casms.trip.controller.TripController;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripDraftPatchRequest;
import com.casms.trip.dto.request.TripUpdateRequest;
import com.casms.trip.dto.response.TripCatalogResponse;
import com.casms.trip.entity.Trip;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripPlanDayRepository;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;
import com.casms.trip.service.IdempotencyService;
import com.casms.trip.service.TripService;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC binding/validation/advice and TripService; persistence/idempotency are isolated. */
@ExtendWith(MockitoExtension.class)
class TripDurationContractTest {
    @Mock private TripRepository trips;
    @Mock private TripScheduleVersionRepository versions;
    @Mock private TripPlanDayRepository days;
    @Mock private UserRepository users;
    @Mock private BusinessCodeGenerator codes;
    @Mock private IdempotencyService idempotency;

    private MockMvc mvc;
    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TripService service = new TripService(trips, versions, days, users, codes, null);
        mvc = MockMvcBuilders.standaloneSetup(new TripController(service, null, idempotency))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void allowCreate() {
        when(idempotency.execute(anyString(), isNull(), eq("CREATE_TRIP"), any(), any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(4).get());
    }

    private void allowSave() {
        when(trips.save(any())).thenAnswer(inv -> {
            Trip trip = inv.getArgument(0);
            trip.setTripId(tripId);
            return trip;
        });
    }

    private String payload(String group, int durationDays) {
        return "{\"name\":\"Duration contract\",\"durationGroup\":\"" + group
                + "\",\"durationDays\":" + durationDays + "}";
    }

    @ParameterizedTest
    @CsvSource({"SHORT,1", "SHORT,2", "LONG,3", "LONG,4", "LONG,5"})
    void createAcceptsEveryApprovedPair(String group, int durationDays) throws Exception {
        allowCreate();
        allowSave();
        when(codes.next("TR")).thenReturn("TR-1001");
        when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/trips").header("Idempotency-Key", "duration-create")
                        .contentType(MediaType.APPLICATION_JSON).content(payload(group, durationDays)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.durationGroup").value(group))
                .andExpect(jsonPath("$.data.durationDays").value(durationDays));
    }

    @ParameterizedTest
    @CsvSource({"SHORT,3,422,INVALID_DURATION", "LONG,2,422,INVALID_DURATION",
            "SHORT,0,400,VALIDATION_FAILED", "LONG,6,422,INVALID_DURATION"})
    void createRejectsInvalidPairsWithExistingEnvelope(String group, int durationDays,
                                                      int status, String code) throws Exception {
        if (durationDays > 0) allowCreate();
        mvc.perform(post("/trips").header("Idempotency-Key", "duration-invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(payload(group, durationDays)))
                .andExpect(status().is(status)).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.error", notNullValue()));
        verifyNoInteractions(trips, versions, codes);
    }

    @ParameterizedTest
    @CsvSource({"SHORT,1", "LONG,3"})
    void omittedDaysKeepExistingEnumDefaults(String group, int defaultDays) throws Exception {
        allowCreate();
        allowSave();
        when(codes.next("TR")).thenReturn("TR-1001");
        when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/trips").header("Idempotency-Key", "duration-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Defaults\",\"durationGroup\":\"" + group + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.durationDays").value(defaultDays));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PATCH", "PUT"})
    void retiredGroupNamesAreRejectedBeforeServiceInvocation(String method) throws Exception {
        for (String group : List.of("MEDIUM", "EXTENDED")) {
            mvc.perform(request(method).contentType(MediaType.APPLICATION_JSON).content(payload(group, 3)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                    .andExpect(jsonPath("$.error", notNullValue()));
        }
        verifyNoInteractions(trips, versions, codes, idempotency);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "PUT"})
    void draftUpdatesPreserveValidPairsAndRejectInvalidPairs(String method) throws Exception {
        allowSave();
        for (int durationDays = 1; durationDays <= 5; durationDays++) {
            String group = durationDays <= 2 ? "SHORT" : "LONG";
            Trip trip = new Trip();
            trip.setTripId(tripId);
            trip.setStatus(TripStatus.DRAFT);
            trip.setDurationGroup(TripDurationGroup.valueOf(group));
            trip.setDurationDays(durationDays);
            trip.setDurationNights(durationDays - 1);
            when(trips.findById(tripId)).thenReturn(Optional.of(trip));
            mvc.perform(request(method).contentType(MediaType.APPLICATION_JSON).content(payload(group, durationDays)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.durationGroup").value(group))
                    .andExpect(jsonPath("$.data.durationDays").value(durationDays));

            String wrongGroup = group.equals("SHORT") ? "LONG" : "SHORT";
            mvc.perform(request(method).contentType(MediaType.APPLICATION_JSON).content(payload(wrongGroup, durationDays)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INVALID_DURATION"));
        }
        mvc.perform(request(method).contentType(MediaType.APPLICATION_JSON).content(payload("SHORT", 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(request(method).contentType(MediaType.APPLICATION_JSON).content(payload("LONG", 6)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DAY_COUNT"));
    }

    private MockHttpServletRequestBuilder request(String method) {
        return switch (method) {
            case "POST" -> post("/trips").header("Idempotency-Key", "duration-unknown");
            case "PATCH" -> patch("/trips/{id}/draft", tripId);
            case "PUT" -> put("/trips/{id}", tripId);
            default -> throw new IllegalArgumentException(method);
        };
    }

    @Test
    void generatedOpenApiModelsExposeOnlyApprovedGroups() {
        for (Class<?> type : List.of(TripCreateRequest.class, TripDraftPatchRequest.class, TripUpdateRequest.class)) {
            var schema = ModelConverters.getInstance().read(type).get(type.getSimpleName());
            assertEquals(List.of("SHORT", "LONG"), ((Schema<?>) schema.getProperties().get("durationGroup")).getEnum());
        }
        var catalog = ModelConverters.getInstance().read(TripCatalogResponse.DurationGroupCatalogItem.class)
                .get("DurationGroupCatalogItem");
        assertEquals(List.of("SHORT", "LONG"), ((Schema<?>) catalog.getProperties().get("code")).getEnum());
    }
}
