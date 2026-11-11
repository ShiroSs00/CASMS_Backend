package com.casms.payment.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.payment.dto.request.ChargeDisputeCreateRequest;
import com.casms.payment.dto.request.ChargeDisputeResolveRequest;
import com.casms.payment.dto.response.ChargeDisputeResponse;
import com.casms.payment.entity.*;
import com.casms.payment.enums.*;
import com.casms.payment.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeDisputeService {

    private final ChargeDisputeRepository disputeRepository;
    private final ChargeRepository chargeRepository;
    private final PassengerRepository passengerRepository;
    private final UserRepository userRepository;
    private final OnboardTransactionRepository transactionRepository;

    @Transactional
    public ChargeDisputeResponse createDispute(ChargeDisputeCreateRequest request) {
        Charge charge =
                chargeRepository
                        .findById(request.getChargeId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Charge", request.getChargeId()));

        Passenger passenger =
                passengerRepository
                        .findById(request.getPassengerId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Passenger", request.getPassengerId()));

        if (request.getDisputedAmount().compareTo(charge.getNetAmount()) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "DISPUTED_AMOUNT_EXCEEDED",
                    "Disputed amount cannot exceed charge amount of " + charge.getNetAmount());
        }

        ChargeDispute dispute = new ChargeDispute();
        dispute.setCharge(charge);
        dispute.setPassenger(passenger);
        dispute.setDisputedAmount(request.getDisputedAmount());
        dispute.setReasonCode(request.getReasonCode());
        dispute.setDescription(request.getDescription().trim());
        dispute.setStatus(ChargeDisputeStatus.OPEN);
        dispute.setReportedAt(Instant.now());

        ChargeDispute saved = disputeRepository.save(dispute);
        return toDisputeResponse(saved);
    }

    @Transactional
    public ChargeDisputeResponse resolveDispute(
            UUID disputeId, ChargeDisputeResolveRequest request, UUID staffUserId) {
        ChargeDispute dispute =
                disputeRepository
                        .findById(disputeId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("ChargeDispute", disputeId));

        if (dispute.getStatus() == ChargeDisputeStatus.RESOLVED
                || dispute.getStatus() == ChargeDisputeStatus.CANCELLED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "DISPUTE_ALREADY_CLOSED",
                    "Dispute is already " + dispute.getStatus());
        }

        User staff = null;
        if (staffUserId != null) {
            staff = userRepository.findById(staffUserId).orElse(null);
        }

        dispute.setResolutionType(request.getResolutionType());
        dispute.setResolutionNote(request.getResolutionNote().trim());
        dispute.setApprovedAmount(request.getApprovedAmount());
        dispute.setStatus(ChargeDisputeStatus.RESOLVED);
        dispute.setAssignedToUser(staff);

        // If approved to reverse or adjust charge, create balancing reversal transaction
        if (request.getResolutionType() == ChargeDisputeResolutionType.REVERSE_CHARGE
                || request.getResolutionType() == ChargeDisputeResolutionType.ADJUST_CHARGE) {
            BigDecimal adjustment =
                    request.getApprovedAmount() != null
                                    && request.getApprovedAmount().compareTo(BigDecimal.ZERO) > 0
                            ? request.getApprovedAmount()
                            : dispute.getDisputedAmount();

            Charge originalCharge = dispute.getCharge();
            if (originalCharge.getTransaction() != null) {
                OnboardTransaction originalTx = originalCharge.getTransaction();
                OnboardTransaction reversalTx = new OnboardTransaction();
                reversalTx.setOnboardAccount(originalTx.getOnboardAccount());
                reversalTx.setPassenger(dispute.getPassenger());
                reversalTx.setTransactionType(TransactionType.REFUND);
                reversalTx.setAmount(adjustment.negate());
                reversalTx.setCurrencyCode(originalTx.getCurrencyCode());
                reversalTx.setTransactionTime(Instant.now());
                reversalTx.setPostedAt(Instant.now());
                reversalTx.setStatus(TransactionStatus.POSTED);
                OnboardTransaction savedTx = transactionRepository.save(reversalTx);
                dispute.setResolutionTransaction(savedTx);
            }
        }

        ChargeDispute saved = disputeRepository.save(dispute);
        return toDisputeResponse(saved);
    }

    @Transactional(readOnly = true)
    public ChargeDisputeResponse getDispute(UUID disputeId) {
        ChargeDispute dispute =
                disputeRepository
                        .findById(disputeId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("ChargeDispute", disputeId));
        return toDisputeResponse(dispute);
    }

    @Transactional(readOnly = true)
    public List<ChargeDisputeResponse> getDisputesForPassenger(UUID passengerId) {
        return disputeRepository
                .findByPassenger_PassengerIdOrderByReportedAtDesc(passengerId)
                .stream()
                .map(this::toDisputeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChargeDisputeResponse> getDisputesForOnboardAccount(UUID onboardAccountId) {
        return disputeRepository
                .findByCharge_Transaction_OnboardAccount_OnboardAccountIdOrderByReportedAtDesc(
                        onboardAccountId)
                .stream()
                .map(this::toDisputeResponse)
                .toList();
    }

    private ChargeDisputeResponse toDisputeResponse(ChargeDispute d) {
        String passengerName =
                d.getPassenger() != null && d.getPassenger().getFullName() != null
                        ? d.getPassenger().getFullName()
                        : "N/A";

        return ChargeDisputeResponse.builder()
                .chargeDisputeId(d.getChargeDisputeId())
                .chargeId(d.getCharge() != null ? d.getCharge().getChargeId() : null)
                .passengerId(d.getPassenger() != null ? d.getPassenger().getPassengerId() : null)
                .passengerName(passengerName)
                .disputedAmount(d.getDisputedAmount())
                .reasonCode(d.getReasonCode())
                .description(d.getDescription())
                .status(d.getStatus())
                .reportedAt(d.getReportedAt())
                .assignedToUserId(
                        d.getAssignedToUser() != null ? d.getAssignedToUser().getUserId() : null)
                .resolutionType(d.getResolutionType())
                .resolutionNote(d.getResolutionNote())
                .approvedAmount(d.getApprovedAmount())
                .resolutionTransactionId(
                        d.getResolutionTransaction() != null
                                ? d.getResolutionTransaction().getTransactionId()
                                : null)
                .build();
    }
}
