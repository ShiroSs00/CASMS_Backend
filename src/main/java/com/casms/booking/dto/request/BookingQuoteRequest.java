package com.casms.booking.dto.request;

import com.casms.booking.enums.PaymentPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingQuoteRequest {

    @NotNull(message = "Cruise ID is required")
    private UUID cruiseId;

    private String currency;

    private PaymentPlan paymentPlan;

    @NotEmpty(message = "Passenger items cannot be empty")
    @Valid
    private List<PassengerQuoteItem> passengers;
}
