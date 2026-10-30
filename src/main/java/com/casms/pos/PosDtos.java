package com.casms.pos;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class PosDtos {
    private PosDtos() {}
    /** Omitted permissions inherit the source; an explicit empty set denies all operations. */
    public record AssignmentInput(Set<@NotNull PosPermission> permissions, boolean enabled) {}
    public record PermissionInput(@NotNull Set<@NotNull PosPermission> permissions) {}
    public record PermissionDefinition(PosPermission code, PosPermission.ResourceType resourceType, String category) {}
    public record ResourcePolicy(UUID resourceId, PosPermission.ResourceType resourceType, boolean configured,
            Set<PosPermission> permissions) {}
    public record AreaActivity(UUID areaId, UUID activityId, Set<PosPermission> permissions, boolean enabled, boolean inherited) {}
    public record TerminalActivity(UUID terminalId, UUID areaId, UUID activityId, Set<PosPermission> permissions,
            boolean enabled, boolean inherited) {}
    public record AvailableActivity(com.casms.activity.controller.ActivityController.ActivityView activity, Set<PosPermission> permissions) {}
    public record TerminalInput(@NotNull UUID areaId, @NotBlank @Size(max=60) String code,
            @NotBlank @Size(max=200) String name, @NotNull Set<@NotNull PosPermission> permissions, boolean enabled) {}
    public record TerminalUpdate(@NotBlank @Size(max=200) String name,
            @NotNull Set<@NotNull PosPermission> permissions, boolean enabled) {}
    public record LoginInput(@NotBlank @Size(max=128) String deviceSecret, UUID areaId, UUID cruiseId) {}
    public record AreaStaffInput(@NotNull UUID cruiseId, Instant startsAt, Instant endsAt, boolean enabled) {}
    public record AreaStaff(UUID assignmentId, UUID areaId, UUID userId, UUID cruiseStaffId, UUID cruiseId,
            boolean enabled, Instant startsAt, Instant endsAt) {}
    public record AreaLinkInput(boolean enabled) {}
    public record TerminalArea(UUID terminalId, UUID areaId, boolean enabled) {}
    public record TerminalService(UUID terminalId, UUID areaId, UUID serviceId, Set<PosPermission> permissions, boolean enabled, boolean inherited) {}
    public record Terminal(UUID terminalId, UUID shipId, UUID areaId, String code, String name,
            Set<PosPermission> permissions, boolean enabled) {}
    public record Credential(Terminal terminal, String deviceSecret) {}
    public record Session(String sessionToken, Instant expiresAt, Terminal terminal, UUID cruiseId, UUID staffAssignmentId) {}
    public record AreaService(UUID areaId, UUID serviceId, Set<PosPermission> permissions, boolean enabled, boolean inherited) {}
    public record AvailableService(UUID serviceId, String code, String name, BigDecimal unitPrice,
            String currency, Set<PosPermission> permissions) {}
    public record Context(Terminal terminal, java.util.List<AvailableService> services, UUID cruiseId, UUID staffAssignmentId) {}
    public record SaleInput(@NotNull UUID serviceId, @NotNull UUID bookingPassengerId,
            @NotNull UUID onboardAccountId, UUID scheduleId, @NotNull @Min(1) @Max(1000) Integer quantity,
            @NotBlank @Size(max=100) String idempotencyKey) {}
    public record Sale(UUID saleId, UUID terminalId, UUID areaId, UUID serviceId, UUID serviceRequestId,
            UUID onboardAccountId, UUID bookingPassengerId, int quantity, BigDecimal unitPrice,
            BigDecimal amount, String currency, UUID employeeId, UUID chargeId, Instant createdAt, Instant reversedAt) {}
    public record Budget(UUID accountId, UUID passengerId, BigDecimal allocatedAmount, BigDecimal usedAmount,
            BigDecimal accountRemaining, BigDecimal remainingAmount, String currency) {}
    public record History(UUID transactionId, String type, BigDecimal amount, String sourceType,
            UUID sourceReferenceId, UUID employeeId, Instant recordedAt) {}
    public record Statement(Budget budget, java.util.List<History> history) {}
    public record MemberLimit(@NotNull UUID bookingPassengerId,
            @NotNull @DecimalMin("0") @Digits(integer=15,fraction=4) BigDecimal amount) {}
    public record AllowanceInput(@NotNull @DecimalMin("0") @Digits(integer=15,fraction=4) BigDecimal amount,
            @NotNull @Size(max=1000) java.util.List<@NotNull @jakarta.validation.Valid MemberLimit> memberLimits) {}
    public record Allowance(UUID bookingId, UUID onboardAccountId, BigDecimal allocatedAmount) {}
}
