package com.casms.ship.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class PortWindowContract {
    private PortWindowContract(){}
    @Schema(description="Port-local [start,end), same-day hours. No default hours. Source is Port Authority/Port Agent, not Geocoding. Updates reset verification and deactivate.")
    public record WindowWrite(Long expectedRevision, @NotNull @Pattern(regexp="ARRIVAL|DEPARTURE") String windowType,
        @NotNull LocalDate validFrom, LocalDate validTo, @NotEmpty Set<DayOfWeek> daysOfWeek,
        @NotNull LocalTime localStartTime, @NotNull LocalTime localEndTime,
        @NotNull @Pattern(regexp="PORT_AUTHORITY|PORT_AGENT") String sourceType, @NotBlank @Size(max=2000) String sourceReference,
        LocalDate sourceEffectiveDate, Boolean overnightAllowed, @Positive Integer maxStayMinutes) {}
    @Schema(description="Verified date-range closure blocks the entire affected local dates. No OVERRIDE support.")
    public record ClosureWrite(Long expectedRevision, @NotNull LocalDate validFrom, @NotNull LocalDate validTo,
        @NotBlank @Size(max=2000) String reason, @NotNull @Pattern(regexp="PORT_AUTHORITY|PORT_AGENT") String sourceType,
        @NotBlank @Size(max=2000) String sourceReference, LocalDate sourceEffectiveDate) {}
    public record Revision(@NotNull Long expectedRevision) {}
    public record Lifecycle(@NotNull Long expectedRevision,@NotNull @Pattern(regexp="ACTIVE|INACTIVE") String lifecycleStatus) {}
    public record Window(UUID windowId, String windowType, LocalDate validFrom, LocalDate validTo, Set<DayOfWeek> daysOfWeek,
        LocalTime localStartTime, LocalTime localEndTime, String sourceType, String sourceReference, LocalDate sourceEffectiveDate,
        Boolean overnightAllowed, Integer maxStayMinutes, String verificationStatus,String lifecycleStatus,
        UUID verifiedBy,Instant verifiedAt,String verifiedTimeZoneId,Long revision,Instant createdAt,Instant updatedAt) {}
    public record Closure(UUID exceptionId,String exceptionType,LocalDate validFrom,LocalDate validTo,String reason,String sourceType,
        String sourceReference,LocalDate sourceEffectiveDate,String verificationStatus,String lifecycleStatus,
        UUID verifiedBy,Instant verifiedAt,String verifiedTimeZoneId,Long revision,Instant createdAt,Instant updatedAt) {}
    public record Directory(UUID portId,String portTimeZoneId,List<Window> windows,List<Closure> exceptions) {}
    public record StayEvidence(UUID windowId,Long revision,String sourceReference,Boolean overnightAllowed,Integer maxStayMinutes,UUID verifiedBy,Instant verifiedAt) {}
    public record Check(UUID portId, String movementType,String status,String code,String message,
        LocalDateTime evaluatedLocalDateTime,String portTimeZoneId,UUID matchedWindowId,Long matchedRevision,String sourceReference,
        UUID closureId,Long closureRevision,Instant evaluatedAt,List<StayEvidence> stayEvidence) {
        public Check withStayEvidence(List<StayEvidence> evidence){return new Check(portId,movementType,status,code,message,evaluatedLocalDateTime,portTimeZoneId,matchedWindowId,matchedRevision,sourceReference,closureId,closureRevision,evaluatedAt,List.copyOf(evidence));}
    }
}
