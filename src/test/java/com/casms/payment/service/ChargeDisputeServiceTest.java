package com.casms.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.payment.dto.request.ChargeDisputeCreateRequest;
import com.casms.payment.dto.request.ChargeDisputeResolveRequest;
import com.casms.payment.dto.response.ChargeDisputeResponse;
import com.casms.payment.entity.*;
import com.casms.payment.enums.ChargeDisputeReasonCode;
import com.casms.payment.enums.ChargeDisputeResolutionType;
import com.casms.payment.enums.ChargeDisputeStatus;
import com.casms.payment.enums.TransactionType;
import com.casms.payment.repository.ChargeDisputeRepository;
import com.casms.payment.repository.ChargeRepository;
import com.casms.payment.repository.OnboardTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ChargeDisputeServiceTest {

    @Mock private ChargeDisputeRepository disputeRepository;
    @Mock private ChargeRepository chargeRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private UserRepository userRepository;
    @Mock private OnboardTransactionRepository transactionRepository;

    @InjectMocks private ChargeDisputeService disputeService;

    private UUID chargeId;
    private UUID passengerId;
    private Charge charge;
    private Passenger passenger;

    @BeforeEach
    void setUp() {
        chargeId = UUID.randomUUID();
        passengerId = UUID.randomUUID();

        passenger = new Passenger();
        passenger.setPassengerId(passengerId);
        passenger.setFullName("Nguyen Van A");

        charge = new Charge();
        charge.setChargeId(chargeId);
        charge.setNetAmount(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("createDispute opens dispute when amount is valid")
    void testCreateDisputeSuccess() {
        ChargeDisputeCreateRequest req = new ChargeDisputeCreateRequest();
        req.setChargeId(chargeId);
        req.setPassengerId(passengerId);
        req.setDisputedAmount(new BigDecimal("50.00"));
        req.setReasonCode(ChargeDisputeReasonCode.DUPLICATE_CHARGE);
        req.setDescription("Bị tính tiền 2 lần cho 1 ly rượu");

        when(chargeRepository.findById(chargeId)).thenReturn(Optional.of(charge));
        when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
        when(disputeRepository.save(any(ChargeDispute.class)))
                .thenAnswer(inv -> {
                    ChargeDispute d = inv.getArgument(0);
                    d.setChargeDisputeId(UUID.randomUUID());
                    return d;
                });

        ChargeDisputeResponse response = disputeService.createDispute(req);

        assertNotNull(response);
        assertEquals(ChargeDisputeStatus.OPEN, response.getStatus());
        assertEquals(new BigDecimal("50.00"), response.getDisputedAmount());
        assertEquals("Nguyen Van A", response.getPassengerName());
    }

    @Test
    @DisplayName("createDispute throws exception when disputed amount exceeds charge")
    void testCreateDisputeExceedsCharge() {
        ChargeDisputeCreateRequest req = new ChargeDisputeCreateRequest();
        req.setChargeId(chargeId);
        req.setPassengerId(passengerId);
        req.setDisputedAmount(new BigDecimal("200.00")); // > 150
        req.setReasonCode(ChargeDisputeReasonCode.DUPLICATE_CHARGE);
        req.setDescription("Sai số tiền");

        when(chargeRepository.findById(chargeId)).thenReturn(Optional.of(charge));
        when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> disputeService.createDispute(req));
        assertEquals("DISPUTED_AMOUNT_EXCEEDED", ex.getCode());
    }

    @Test
    @DisplayName("resolveDispute resolves dispute and creates refund transaction")
    void testResolveDisputeSuccess() {
        UUID disputeId = UUID.randomUUID();
        OnboardAccount account = new OnboardAccount();
        account.setOnboardAccountId(UUID.randomUUID());

        OnboardTransaction tx = new OnboardTransaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setOnboardAccount(account);
        tx.setCurrencyCode("USD");
        charge.setTransaction(tx);

        ChargeDispute dispute = new ChargeDispute();
        dispute.setChargeDisputeId(disputeId);
        dispute.setCharge(charge);
        dispute.setPassenger(passenger);
        dispute.setStatus(ChargeDisputeStatus.OPEN);
        dispute.setDisputedAmount(new BigDecimal("50.00"));

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(transactionRepository.save(any(OnboardTransaction.class)))
                .thenAnswer(inv -> {
                    OnboardTransaction t = inv.getArgument(0);
                    t.setTransactionId(UUID.randomUUID());
                    return t;
                });
        when(disputeRepository.save(any(ChargeDispute.class))).thenAnswer(inv -> inv.getArgument(0));

        ChargeDisputeResolveRequest resolveReq = new ChargeDisputeResolveRequest();
        resolveReq.setResolutionType(ChargeDisputeResolutionType.REVERSE_CHARGE);
        resolveReq.setResolutionNote("Đã xác nhận lỗi POS, hoàn tiền đầy đủ");
        resolveReq.setApprovedAmount(new BigDecimal("50.00"));

        ChargeDisputeResponse response =
                disputeService.resolveDispute(disputeId, resolveReq, null);

        assertNotNull(response);
        assertEquals(ChargeDisputeStatus.RESOLVED, response.getStatus());
        assertEquals(new BigDecimal("50.00"), response.getApprovedAmount());
        assertNotNull(response.getResolutionTransactionId());
    }

    @Test
    @DisplayName("resolveDispute throws exception when approved amount exceeds disputed amount")
    void testResolveDisputeExceedsDisputed() {
        UUID disputeId = UUID.randomUUID();
        ChargeDispute dispute = new ChargeDispute();
        dispute.setChargeDisputeId(disputeId);
        dispute.setStatus(ChargeDisputeStatus.OPEN);
        dispute.setDisputedAmount(new BigDecimal("50.00"));

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        ChargeDisputeResolveRequest resolveReq = new ChargeDisputeResolveRequest();
        resolveReq.setResolutionType(ChargeDisputeResolutionType.ADJUST_CHARGE);
        resolveReq.setResolutionNote("Hoàn quá mức khiếu nại");
        resolveReq.setApprovedAmount(new BigDecimal("70.00")); // > 50

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> disputeService.resolveDispute(disputeId, resolveReq, null));
        assertEquals("APPROVED_AMOUNT_EXCEEDED", ex.getCode());
    }
}
