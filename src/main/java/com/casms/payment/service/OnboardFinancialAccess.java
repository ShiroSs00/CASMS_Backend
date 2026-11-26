package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.payment.entity.OnboardAccount;
import com.casms.payment.enums.AccountMemberStatus;
import com.casms.payment.repository.OnboardAccountMemberRepository;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OnboardFinancialAccess {
    private final BookingRepository bookings;
    private final BookingPassengerRepository passengers;
    private final OnboardAccountMemberRepository members;

    public void requireAuthenticated(CasmsPrincipal actor) {
        if (actor == null || !actor.isEnabled() || !actor.isAccountNonLocked())
            throw denied();
    }

    public boolean isFinance(CasmsPrincipal actor, Permission... permissions) {
        requireAuthenticated(actor);
        return !actor.hasRole(Role.PASSENGER)
                && actor.hasAnyRole(Role.ADMIN, Role.FINANCE_OFFICER)
                && actor.hasAnyPermission(permissions);
    }

    public void requireFinance(CasmsPrincipal actor, Permission... permissions) {
        if (!isFinance(actor, permissions)) throw denied();
    }

    public void requireBookingInvoice(CasmsPrincipal actor, Booking booking) {
        if (isFinance(actor, Permission.PAYMENT_READ, Permission.INVOICE_GENERATE, Permission.PAYMENT_PROCESS))
            return;
        if (!actor.hasRole(Role.PASSENGER) || booking.getBookedByUser() == null
                || !actor.getUserId().equals(booking.getBookedByUser().getUserId()))
            throw denied();
    }

    public Scope readFolio(CasmsPrincipal actor, OnboardAccount account, Booking selectedBooking) {
        requireAuthenticated(actor);
        UUID accountId = account.getOnboardAccountId();
        Booking booking = selectedBooking != null ? selectedBooking
                : bookings.findBySettlementOnboardAccount_OnboardAccountId(accountId).orElse(null);
        if (booking != null && (booking.getSettlementOnboardAccount() == null
                || !accountId.equals(booking.getSettlementOnboardAccount().getOnboardAccountId())
                || !account.getCruise().getCruiseId().equals(booking.getCruise().getCruiseId())))
            throw denied();
        if (isFinance(actor, Permission.PAYMENT_READ, Permission.PAYMENT_REPORT, Permission.PAYMENT_PROCESS))
            return new Scope(booking, null);
        if (!actor.hasRole(Role.PASSENGER) || booking == null
                || booking.getBookingStatus() != BookingStatus.Confirmed)
            throw denied();

        UUID passengerId = passengers.findByPassenger_User_UserId(actor.getUserId()).stream()
                .filter(bp -> booking.getBookingId().equals(bp.getBooking().getBookingId())
                        && bp.getParticipationStatus() == BookingPassengerParticipationStatus.Active)
                .map(bp -> bp.getPassenger().getPassengerId())
                .findFirst().orElseThrow(OnboardFinancialAccess::denied);
        Instant now = Instant.now();
        if (members.findByOnboardAccount_OnboardAccountId(accountId).stream().noneMatch(member ->
                passengerId.equals(member.getPassenger().getPassengerId())
                        && member.getStatus() == AccountMemberStatus.ACTIVE
                        && (member.getEffectiveFrom() == null || !member.getEffectiveFrom().isAfter(now))
                        && (member.getEffectiveTo() == null || member.getEffectiveTo().isAfter(now))))
            throw denied();
        return new Scope(booking, passengerId);
    }

    public Booking booking(UUID id) {
        return bookings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Financial record is outside the permitted scope");
    }

    public record Scope(Booking booking, UUID passengerId) {
        public boolean accountWide() {
            return passengerId == null;
        }
    }
}
