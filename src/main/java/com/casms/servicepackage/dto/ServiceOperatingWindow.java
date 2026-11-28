package com.casms.servicepackage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** A recurring daily interval in the applicable service's local clock. */
public record ServiceOperatingWindow(
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalTime startsAt,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalTime endsAt) {

    @AssertTrue(message = "Operating window start and end times must differ")
    @JsonIgnore
    public boolean isDistinctBounds() {
        return startsAt == null || endsAt == null || !startsAt.equals(endsAt);
    }

    public boolean contains(LocalTime time) {
        if (startsAt.isBefore(endsAt)) {
            return !time.isBefore(startsAt) && time.isBefore(endsAt);
        }
        return !time.isBefore(startsAt) || time.isBefore(endsAt);
    }
}
