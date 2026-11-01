package com.casms.trip.dto.request;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
public record DestinationVisitDraftDto(@Schema(description="Existing ID must belong to this stop; omit for new visit") UUID visitId,
 @NotBlank @Size(max=100) String clientKey,@NotNull UUID destinationId,@NotNull @Positive Integer sequence) {}
