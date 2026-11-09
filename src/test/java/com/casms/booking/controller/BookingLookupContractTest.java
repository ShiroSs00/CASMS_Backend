package com.casms.booking.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casms.booking.service.BookingService;
import com.casms.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingLookupContractTest {
    @Test
    void missingReferenceReturnsBadRequestBeforeLookup() throws Exception {
        var service = mock(BookingService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BookingController(service, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/booking/lookup").param("email", "guest@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_PARAMETER"))
                .andExpect(jsonPath("$.details.parameter").value("reference"));
        verifyNoInteractions(service);
    }
}
