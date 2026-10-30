package com.casms.pos;

import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.payment.entity.*;
import com.casms.payment.enums.*;
import com.casms.payment.repository.*;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.enums.CruiseStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class OnboardAllowanceService {
    private final BookingRepository bookings;
    private final BookingPassengerRepository passengers;
    private final OnboardAccountRepository accounts;
    private final OnboardAccountMemberRepository members;
    private final SpendingLimitRepository limits;
    private final PosLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PosDtos.Allowance get(UUID bookingId) {
        var booking=bookings.findById(bookingId).orElseThrow(()->new ResourceNotFoundException("Booking",bookingId));
        var account=booking.getSettlementOnboardAccount();
        if(account==null)throw new BusinessException("POS_ALLOWANCE_NOT_ALLOCATED","Booking has no onboard spending account");
        return new PosDtos.Allowance(bookingId,account.getOnboardAccountId(),account.getCreditLimit());
    }
    public PosDtos.Allowance allocate(UUID bookingId,PosDtos.AllowanceInput input,CasmsPrincipal employee) {
        // Serializes initial account provisioning without taking a booking row lock before account locks.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?::text,0))",Object.class,bookingId.toString());
        var booking=bookings.findById(bookingId).orElseThrow(()->new ResourceNotFoundException("Booking",bookingId));
        if(booking.getBookingStatus()!=BookingStatus.Confirmed || (booking.getCruise().getStatus()!=CruiseStatus.PUBLISHED
                && booking.getCruise().getStatus()!=CruiseStatus.IN_PROGRESS))
            throw new BusinessException("POS_ALLOWANCE_BOOKING_INELIGIBLE","Allocate only for a confirmed booking on a published or in-progress cruise");
        var group=passengers.findByBookingBookingId(bookingId).stream()
                .filter(p->p.getParticipationStatus()==BookingPassengerParticipationStatus.Active).toList();
        if(group.isEmpty())throw new BusinessException("POS_ALLOWANCE_NO_MEMBERS","Booking has no active passengers");
        var ids=group.stream().map(p->p.getBookingPassengerId()).collect(java.util.stream.Collectors.toSet());
        Set<UUID> seen=new HashSet<>();
        for(var cap:input.memberLimits())if(!ids.contains(cap.bookingPassengerId()) || !seen.add(cap.bookingPassengerId()))
            throw new BusinessException("POS_INVALID_MEMBER_LIMIT","Member caps must reference distinct active passengers in this booking");
        var account=booking.getSettlementOnboardAccount();
        if(account==null) {
            account=new OnboardAccount();account.setCruise(booking.getCruise());account.setStatus(OnboardAccountStatus.ACTIVE);
            account.setCreditLimit(input.amount());account.setActivatedAt(Instant.now());accounts.saveAndFlush(account);
            booking.setSettlementOnboardAccount(account);bookings.saveAndFlush(booking);
        } else {
            ledger.lockAccount(account.getOnboardAccountId());
            if(!account.getCruise().getCruiseId().equals(booking.getCruise().getCruiseId()) || account.getStatus()!=OnboardAccountStatus.ACTIVE)
                throw new BusinessException("POS_ACCOUNT_NOT_ACTIVE","Booking account must be active and belong to the booking cruise");
        }
        UUID accountId=account.getOnboardAccountId();
        var used=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM \"OnboardTransaction\" WHERE onboard_account_id=? AND status='POSTED' "
                +"AND transaction_type IN ('CHARGE','VOID','ADJUSTMENT')",BigDecimal.class,accountId);
        if(input.amount().compareTo(used)>=0)account.setCreditLimit(input.amount());
        else throw new BusinessException("POS_ALLOWANCE_BELOW_USED","Allocated allowance cannot be lower than already used allowance");
        for(var bp:group) {
            var existing=jdbc.queryForList("SELECT account_member_id FROM \"OnboardAccountMember\" WHERE onboard_account_id=? AND passenger_id=?",
                    UUID.class,accountId,bp.getPassenger().getPassengerId());
            OnboardAccountMember member;
            if(existing.isEmpty()) {
                member=new OnboardAccountMember();member.setOnboardAccount(account);member.setPassenger(bp.getPassenger());
                member.setMemberRole(Objects.equals(booking.getRepresentativeBookingPassengerId(),bp.getBookingPassengerId())?MemberRole.OWNER:MemberRole.AUTHORIZED);
                member.setStatus(AccountMemberStatus.ACTIVE);member.setChargePermission(ChargePermission.ALLOWED);member.setEffectiveFrom(Instant.now());
                members.saveAndFlush(member);
            } else member=members.findById(existing.getFirst()).orElseThrow();
            for(var cap:input.memberLimits())if(cap.bookingPassengerId().equals(bp.getBookingPassengerId())) {
                var spent=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM \"OnboardTransaction\" WHERE onboard_account_id=? AND passenger_id=? "
                        +"AND status='POSTED' AND transaction_type IN ('CHARGE','VOID','ADJUSTMENT')",BigDecimal.class,accountId,bp.getPassenger().getPassengerId());
                if(cap.amount().compareTo(spent)<0)throw new BusinessException("POS_MEMBER_LIMIT_BELOW_USED","Member limit cannot be lower than already used allowance");
                jdbc.update("UPDATE \"SpendingLimit\" SET status='CANCELLED',updated_at=now() WHERE account_member_id=? AND status='ACTIVE'",member.getAccountMemberId());
                var limit=new SpendingLimit();limit.setAccountMember(member);limit.setLimitAmount(cap.amount());limit.setCurrencyCode("USD");
                // This is a whole-account allowance, not a window starting at a planned departure.
                // Reconfiguring it must include every existing debit, even after an early cruise start.
                limit.setEffectiveFrom(account.getCreatedAt());limit.setStatus(SpendingLimitStatus.ACTIVE);limit.setSetByUser(employee.getUser());limits.save(limit);
            }
        }
        accounts.saveAndFlush(account);
        try {
            jdbc.update("INSERT INTO \"OnboardAllowanceChange\" (change_id,onboard_account_id,actor_user_id,allocated_amount,member_limits_json,recorded_at) VALUES (?,?,?,?,?,now())",
                    UUID.randomUUID(),accountId,employee.getUserId(),input.amount(),mapper.writeValueAsString(input.memberLimits()));
        } catch(JsonProcessingException ex) {throw new IllegalStateException(ex);}
        return new PosDtos.Allowance(bookingId,accountId,input.amount());
    }
}
