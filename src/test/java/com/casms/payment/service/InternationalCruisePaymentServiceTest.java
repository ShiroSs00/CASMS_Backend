package com.casms.payment.service;

import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.payment.dto.v1.InternationalPaymentDtos.*;
import com.casms.payment.entity.*;
import com.casms.payment.enums.*;
import com.casms.payment.gateway.MockInternationalPaymentGateway;
import com.casms.payment.repository.*;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternationalCruisePaymentServiceTest {

    @Mock private OnboardAccountRepository onboardAccountRepository;
    @Mock private OnboardAccountMemberRepository onboardAccountMemberRepository;
    @Mock private SpendingLimitRepository spendingLimitRepository;
    @Mock private OnboardTransactionRepository onboardTransactionRepository;
    @Mock private RegisteredPaymentMethodRepository registeredPaymentMethodRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentWebhookEventRepository paymentWebhookEventRepository;
    @Mock private OfflineSyncLogRepository offlineSyncLogRepository;
    @Mock private FinalSettlementRepository finalSettlementRepository;
    @Mock private SettlementItemRepository settlementItemRepository;
    @Mock private ChargeDisputeRepository chargeDisputeRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CruiseRepository cruiseRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentService paymentService;
    @Mock private UserRepository userRepository;
    @Mock private CurrencyExchangeRateRepository currencyExchangeRateRepository;

    private CurrencyExchangeService currencyExchangeService;
    private MockInternationalPaymentGateway paymentGateway;
    private InternationalCruisePaymentService service;

    private UUID cruiseId;
    private UUID passengerId;
    private UUID accountId;
    private Cruise cruise;
    private Passenger passenger;
    private User passengerUser;
    private User financeUser;
    private CasmsPrincipal passengerPrincipal;
    private CasmsPrincipal financePrincipal;
    private OnboardAccount activeAccount;

    // In-memory store for simulated ledger persistence across calls within a test
    private final List<OnboardTransaction> storedTransactions = new ArrayList<>();
    private final List<Payment> storedPayments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        storedTransactions.clear();
        storedPayments.clear();

        currencyExchangeService = new CurrencyExchangeService(currencyExchangeRateRepository);
        paymentGateway = new MockInternationalPaymentGateway();
        service = new InternationalCruisePaymentService(
                onboardAccountRepository,
                onboardAccountMemberRepository,
                spendingLimitRepository,
                onboardTransactionRepository,
                registeredPaymentMethodRepository,
                paymentRepository,
                refundRepository,
                paymentAttemptRepository,
                paymentWebhookEventRepository,
                offlineSyncLogRepository,
                finalSettlementRepository,
                settlementItemRepository,
                chargeDisputeRepository,
                invoiceRepository,
                cruiseRepository,
                passengerRepository,
                bookingRepository,
                paymentService,
                userRepository,
                currencyExchangeService,
                paymentGateway,
                new ObjectMapper()
        );

        cruiseId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        cruise.setCruiseCode("CR-ASIA-2026");
        cruise.setBaseCurrency("USD");

        passengerUser = new User();
        passengerUser.setUserId(UUID.randomUUID());
        passengerUser.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
        passengerUser.setUsername("passenger_asia");
        passengerUser.setRoles(Set.of(Role.PASSENGER));

        financeUser = new User();
        financeUser.setUserId(UUID.randomUUID());
        financeUser.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
        financeUser.setUsername("finance_officer");
        financeUser.setRoles(Set.of(Role.FINANCE_OFFICER));

        passenger = new Passenger();
        passenger.setPassengerId(passengerId);
        passenger.setFullName("NGUYEN VAN MINH");
        passenger.setUser(passengerUser);

        passengerPrincipal = new CasmsPrincipal(passengerUser, null);
        financePrincipal = new CasmsPrincipal(financeUser, null);

        activeAccount = new OnboardAccount();
        activeAccount.setOnboardAccountId(accountId);
        activeAccount.setCruise(cruise);
        activeAccount.setPrimaryPassenger(passenger);
        activeAccount.setBaseCurrency("USD");
        activeAccount.setCreditLimit(new BigDecimal("500.0000"));
        activeAccount.setPreAuthorizedAmount(BigDecimal.ZERO);
        activeAccount.setStatus(OnboardAccountStatus.ACTIVE);
        activeAccount.setActivatedAt(Instant.now());

        lenient().when(onboardTransactionRepository.findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(accountId))
                .thenAnswer(inv -> new ArrayList<>(storedTransactions));
        lenient().when(paymentRepository.findByOnboardAccount_OnboardAccountIdOrderByRequestedAtDesc(accountId))
                .thenAnswer(inv -> new ArrayList<>(storedPayments));
        lenient().when(onboardTransactionRepository.save(any(OnboardTransaction.class)))
                .thenAnswer(inv -> {
                    OnboardTransaction tx = inv.getArgument(0);
                    if (tx.getTransactionId() == null) {
                        tx.setTransactionId(UUID.randomUUID());
                    }
                    storedTransactions.removeIf(t -> t.getTransactionId().equals(tx.getTransactionId()));
                    storedTransactions.add(tx);
                    return tx;
                });
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> {
                    Payment p = inv.getArgument(0);
                    if (p.getPaymentId() == null) {
                        p.setPaymentId(UUID.randomUUID());
                    }
                    storedPayments.removeIf(existing -> existing.getPaymentId().equals(p.getPaymentId()));
                    storedPayments.add(p);
                    return p;
                });
    }

    // =========================================================================
    // 1. Creating an onboard account
    // =========================================================================
    @Test
    @DisplayName("1. Create and activate an onboard account in cruise base currency (USD)")
    void testCreateOnboardAccount_Success() {
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
        when(onboardAccountRepository.findByCruise_CruiseIdAndPrimaryPassenger_PassengerId(cruiseId, passengerId))
                .thenReturn(Optional.empty());
        when(onboardAccountRepository.save(any(OnboardAccount.class))).thenAnswer(inv -> {
            OnboardAccount acc = inv.getArgument(0);
            acc.setOnboardAccountId(accountId);
            return acc;
        });
        when(onboardAccountMemberRepository.findByOnboardAccount_OnboardAccountId(accountId))
                .thenReturn(Collections.emptyList());
        when(onboardAccountMemberRepository.save(any(OnboardAccountMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateOnboardAccountRequest req = new CreateOnboardAccountRequest(
                cruiseId, passengerId, new BigDecimal("1500.00"), "USD", true);

        OnboardAccountResponse res = service.createOrActivateAccount(req, financePrincipal);

        assertThat(res).isNotNull();
        assertThat(res.accountId()).isEqualTo(accountId);
        assertThat(res.baseCurrency()).isEqualTo("USD");
        assertThat(res.spendingLimit()).isEqualByComparingTo("1500.0000");
        assertThat(res.status()).isEqualTo(OnboardAccountStatus.ACTIVE);
        assertThat(res.outstandingBalance()).isEqualByComparingTo("0.0000");
        assertThat(res.availableSpendingLimit()).isEqualByComparingTo("1500.0000");
    }

    // =========================================================================
    // 2. Recording onboard charges
    // =========================================================================
    @Test
    @DisplayName("2. Record onboard charges (Restaurant, Spa, Drinks, Excursion = 320 USD)")
    void testRecordOnboardCharges_Success() {
        when(onboardAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(activeAccount));
        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        service.recordTransaction(new CreateTransactionRequest(
                accountId, passengerId, "RESTAURANT:FINE-DINING", new BigDecimal("50.00"),
                "USD", TransactionType.CHARGE, null, "POS-REST-01", "idem-rest-1",
                "Fine Dining Dinner", false), financePrincipal);

        service.recordTransaction(new CreateTransactionRequest(
                accountId, passengerId, "SPA:AROMA-MASSAGE", new BigDecimal("100.00"),
                "USD", TransactionType.CHARGE, null, "POS-SPA-01", "idem-spa-1",
                "Spa Massage", false), financePrincipal);

        service.recordTransaction(new CreateTransactionRequest(
                accountId, passengerId, "BAR:COCKTAIL", new BigDecimal("20.00"),
                "USD", TransactionType.CHARGE, null, "POS-BAR-02", "idem-bar-1",
                "Poolside Drinks", false), financePrincipal);

        service.recordTransaction(new CreateTransactionRequest(
                accountId, passengerId, "EXCURSION:SINGAPORE-TOUR", new BigDecimal("150.00"),
                "USD", TransactionType.CHARGE, null, "POS-DESK-01", "idem-exc-1",
                "Singapore Shore Excursion", false), financePrincipal);

        installPersonalMembership();
        AccountBalanceResponse balance = service.getAccountBalance(accountId, "VND", passengerPrincipal);

        assertThat(balance.totalCharges()).isEqualByComparingTo("320.0000");
        assertThat(balance.outstandingBalance()).isEqualByComparingTo("320.0000");
        assertThat(balance.availableSpendingLimit()).isEqualByComparingTo("180.0000");
        // Informational VND conversion: 320 USD * 25400 = 8,128,000 VND (0 decimals)
        assertThat(balance.displayCurrency()).isEqualTo("VND");
        assertThat(balance.convertedOutstandingBalance()).isEqualByComparingTo("8128000");
    }

    // =========================================================================
    // 3. Spending-limit validation
    // =========================================================================
    @Test
    @DisplayName("3. Reject onboard charge that exceeds available spending limit unless Finance Staff approves")
    void testSpendingLimitValidation_RejectAndStaffOverride() {
        when(onboardAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(activeAccount));

        CreateTransactionRequest overLimitReq = new CreateTransactionRequest(
                accountId, passengerId, "LUXURY:JEWELRY", new BigDecimal("600.00"),
                "USD", TransactionType.CHARGE, null, "POS-SHOP-01", "idem-over-1",
                "Duty-free watch", false);

        assertThatThrownBy(() -> service.recordTransaction(overLimitReq, passengerPrincipal))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.recordTransaction(overLimitReq, financePrincipal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds available spending limit");

        // Now test with Finance Staff Override = true
        CreateTransactionRequest overrideReq = new CreateTransactionRequest(
                accountId, passengerId, "LUXURY:JEWELRY", new BigDecimal("600.00"),
                "USD", TransactionType.CHARGE, null, "POS-SHOP-01", "idem-over-approved",
                "Duty-free watch (Finance Approved)", true);

        TransactionResponse approvedRes = service.recordTransaction(overrideReq, financePrincipal);
        assertThat(approvedRes.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(activeAccount.getCreditLimit()).isEqualByComparingTo("600.0000");
    }

    // =========================================================================
    // 4. Concurrent transactions / Pessimistic lock verification
    // =========================================================================
    @Test
    @DisplayName("4. Prevent concurrent spending-limit violations via PESSIMISTIC_WRITE lock")
    void testConcurrentTransactions_UsesPessimisticWriteLock() {
        when(onboardAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(activeAccount));

        // First charge uses 400 of 500 USD limit
        service.recordTransaction(new CreateTransactionRequest(
                accountId, passengerId, "EXCURSION:TOKYO", new BigDecimal("400.00"),
                "USD", TransactionType.CHARGE, null, "POS-01", "idem-conc-1", "Tokyo Tour", false), financePrincipal);

        // Second serialized charge under pessimistic lock attempts 200 USD (remaining is 100 USD) -> rejected
        CreateTransactionRequest secondCharge = new CreateTransactionRequest(
                accountId, passengerId, "SPA:VIP", new BigDecimal("200.00"),
                "USD", TransactionType.CHARGE, null, "POS-02", "idem-conc-2", "VIP Spa", false);

        assertThatThrownBy(() -> service.recordTransaction(secondCharge, financePrincipal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds available spending limit");

        verify(onboardAccountRepository, times(2)).findByIdForUpdate(accountId);
    }

    private void installPersonalMembership() {
        var member = new OnboardAccountMember();
        member.setPassenger(passenger);
        member.setStatus(AccountMemberStatus.ACTIVE);
        when(onboardAccountMemberRepository.findByOnboardAccount_OnboardAccountId(accountId))
                .thenReturn(List.of(member));
    }

    // =========================================================================
    // 5. Duplicate transaction prevention (Idempotency)
    // =========================================================================
    @Test
    @DisplayName("5. Prevent duplicate onboard transaction when retrying with same idempotencyKey")
    void testDuplicateTransactionPrevention_Idempotency() {
        when(onboardAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(activeAccount));
        when(onboardTransactionRepository.findByIdempotencyKey("idem-dup-tx-001"))
                .thenAnswer(inv -> storedTransactions.stream()
                        .filter(t -> "idem-dup-tx-001".equals(t.getIdempotencyKey()))
                        .findFirst());

        CreateTransactionRequest req = new CreateTransactionRequest(
                accountId, passengerId, "BAR:WINE", new BigDecimal("45.00"),
                "USD", TransactionType.CHARGE, null, "POS-BAR-01", "idem-dup-tx-001", "Wine bottle", false);

        TransactionResponse first = service.recordTransaction(req, financePrincipal);
        TransactionResponse second = service.recordTransaction(req, financePrincipal);

        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        var changedAmount = new CreateTransactionRequest(
                accountId, passengerId, "BAR:WINE", new BigDecimal("46.00"),
                "USD", TransactionType.CHARGE, null, "POS-BAR-01", "idem-dup-tx-001", "Wine bottle", false);
        assertThatThrownBy(() -> service.recordTransaction(changedAmount, financePrincipal))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("TRANSACTION_IDEMPOTENCY_CONFLICT"));
        assertThat(storedTransactions).hasSize(1);
    }

    // =========================================================================
    // 6 & 7. Payment Authorization & Capture
    // =========================================================================
    @Test
    @DisplayName("6 & 7. Pre-authorize tokenized card payment and subsequently capture it")
    void testPaymentAuthorizationAndCapture_Success() {
        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(paymentRepository.findByIdempotencyKey("idem-auth-01")).thenReturn(Optional.empty());
        when(paymentAttemptRepository.countByPayment_PaymentId(any())).thenReturn(0);

        AuthorizePaymentRequest authReq = new AuthorizePaymentRequest(
                accountId, null, null, "tok_visa_sg_4242",
                new BigDecimal("400.00"), "USD", "USD", false,
                "idem-auth-01", "Check-in Pre-Authorization");

        installPersonalMembership();
        GatewayPaymentResponse authRes = service.authorizePayment(authReq, passengerPrincipal);

        assertThat(authRes.status()).isEqualTo(PaymentStatus.Authorized);
        assertThat(authRes.authorizedAmount()).isEqualByComparingTo("400.0000");
        assertThat(authRes.processorReference()).startsWith("GW-AUTH-");
        assertThat(activeAccount.getPreAuthorizedAmount()).isEqualByComparingTo("400.0000");

        // Now capture 320 USD of the 400 USD pre-authorization
        Payment storedAuth = storedPayments.get(0);
        when(paymentRepository.findByIdForUpdate(storedAuth.getPaymentId())).thenReturn(Optional.of(storedAuth));

        CapturePaymentRequest captureReq = new CapturePaymentRequest(
                storedAuth.getPaymentId(), new BigDecimal("320.00"), "idem-cap-01");

        GatewayPaymentResponse capRes = service.capturePayment(captureReq, financePrincipal);

        assertThat(capRes.status()).isEqualTo(PaymentStatus.Captured);
        assertThat(capRes.capturedAmount()).isEqualByComparingTo("320.0000");
    }

    // =========================================================================
    // 8. Failed payment, Uncertain Timeout & Safe Retry
    // =========================================================================
    @Test
    @DisplayName("8. Handle failed payment (tok_fail_*) and uncertain timeout (tok_timeout_*) safely without assuming success")
    void testFailedAndUncertainTimeoutPaymentAndRetry() {
        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        // 1. Declined card token (tok_fail_*)
        DirectChargePaymentRequest failReq = new DirectChargePaymentRequest(
                accountId, null, null, "tok_fail_insufficient_funds",
                new BigDecimal("100.00"), "USD", "USD", false,
                "idem-charge-fail", "Attempt 1");

        installPersonalMembership();
        GatewayPaymentResponse failRes = service.chargePayment(failReq, passengerPrincipal);
        assertThat(failRes.status()).isEqualTo(PaymentStatus.Failed);
        assertThat(failRes.failureReason()).contains("Card issuer declined");

        // 2. Network timeout token (tok_timeout_*) -> remains Pending until reconciled
        DirectChargePaymentRequest timeoutReq = new DirectChargePaymentRequest(
                accountId, null, null, "tok_timeout_gateway",
                new BigDecimal("100.00"), "USD", "USD", false,
                "idem-charge-timeout", "Attempt 2");

        GatewayPaymentResponse timeoutRes = service.chargePayment(timeoutReq, passengerPrincipal);
        assertThat(timeoutRes.status()).isEqualTo(PaymentStatus.Pending);

        // 3. Retry with valid token and new idempotency key succeeds
        DirectChargePaymentRequest retryReq = new DirectChargePaymentRequest(
                accountId, null, null, "tok_mastercard_hk_8888",
                new BigDecimal("100.00"), "USD", "USD", false,
                "idem-charge-retry-ok", "Attempt 3");

        GatewayPaymentResponse retryRes = service.chargePayment(retryReq, passengerPrincipal);
        assertThat(retryRes.status()).isEqualTo(PaymentStatus.Captured);
        assertThat(retryRes.capturedAmount()).isEqualByComparingTo("100.0000");
    }

    // =========================================================================
    // 9. Refund processing (Onboard Transaction & Gateway Payment)
    // =========================================================================
    @Test
    @DisplayName("9. Process partial and full refunds on captured gateway payment")
    void testGatewayRefundProcessing_Success() {
        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        GatewayPaymentResponse charged = service.chargePayment(new DirectChargePaymentRequest(
                accountId, null, null, "tok_visa_jp_9999",
                new BigDecimal("200.00"), "USD", "USD", false,
                "idem-charge-for-refund", "Initial Charge"), financePrincipal);

        Payment paymentEntity = storedPayments.get(0);
        when(paymentRepository.findByIdForUpdate(charged.paymentId())).thenReturn(Optional.of(paymentEntity));

        // Partial refund of 80 USD
        GatewayPaymentResponse partialRefund = service.refundGatewayPayment(
                charged.paymentId(),
                new GatewayRefundRequest(new BigDecimal("80.00"), "Cancelled excursion", "idem-ref-1"),
                financePrincipal);

        assertThat(partialRefund.status()).isEqualTo(PaymentStatus.PartiallyRefunded);
        assertThat(partialRefund.refundedAmount()).isEqualByComparingTo("80.0000");

        // Remaining refund of 120 USD -> transitions to Refunded
        GatewayPaymentResponse fullRefund = service.refundGatewayPayment(
                charged.paymentId(),
                new GatewayRefundRequest(new BigDecimal("120.00"), "Full cancellation", "idem-ref-2"),
                financePrincipal);

        assertThat(fullRefund.status()).isEqualTo(PaymentStatus.Refunded);
        assertThat(fullRefund.refundedAmount()).isEqualByComparingTo("200.0000");
    }

    // =========================================================================
    // 10. Multi-Currency Support & DCC Consent Enforcement
    // =========================================================================
    @Test
    @DisplayName("10. Multi-currency conversion: zero-decimal JPY/VND rounding and explicit DCC consent enforcement")
    void testMultiCurrencyConversionAndDccConsent() {
        // 1. Informational display conversion to JPY (0 decimal places)
        var jpyDisplay = currencyExchangeService.convertForDisplay(new BigDecimal("320.00"), "USD", "JPY");
        assertThat(jpyDisplay.conversionPurpose()).isEqualTo("INFORMATIONAL_DISPLAY");
        assertThat(jpyDisplay.convertedAmount().scale()).isEqualTo(0);
        assertThat(jpyDisplay.convertedAmount()).isEqualByComparingTo("49120"); // 320 * 153.5

        // 2. Settlement conversion without explicit customer DCC consent must be rejected
        assertThatThrownBy(() -> currencyExchangeService.convertForSettlement(
                new BigDecimal("320.00"), "USD", "VND", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("explicit customer consent");

        // 3. Settlement conversion with explicit customer DCC consent succeeds and records rate & source
        var vndSettlement = currencyExchangeService.convertForSettlement(
                new BigDecimal("320.00"), "USD", "VND", true);
        assertThat(vndSettlement.conversionPurpose()).isEqualTo("ACTUAL_SETTLEMENT");
        assertThat(vndSettlement.convertedAmount()).isEqualByComparingTo("8128000");
        assertThat(vndSettlement.exchangeRate()).isEqualByComparingTo("25400.000000");
    }

    // =========================================================================
    // 11 & 12. Offline Transaction Synchronization, Duplicate & Conflict Detection
    // =========================================================================
    @Test
    @DisplayName("11 & 12. Offline POS batch sync: preserves occurrenceTime, ignores duplicates, and flags conflicts")
    void testOfflineTransactionSync_DuplicatesAndConflicts() {
        when(onboardAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(activeAccount));
        when(onboardTransactionRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    return storedTransactions.stream()
                            .filter(t -> key.equals(t.getIdempotencyKey()))
                            .findFirst();
                });

        Instant offlineTime1 = Instant.parse("2026-05-10T04:15:00Z");
        Instant offlineTime2 = Instant.parse("2026-05-10T05:30:00Z");

        OfflineTransactionItemRequest validOfflineTx = new OfflineTransactionItemRequest(
                UUID.randomUUID(), accountId, passengerId, "DUTY_FREE:PERFUME",
                new BigDecimal("120.00"), "USD", TransactionType.CHARGE,
                null, "POS-OFFLINE-01", "idem-offline-001", offlineTime1, "Duty free perfume");

        // Conflict item: exceeds remaining 380 USD limit (requests 900 USD)
        OfflineTransactionItemRequest overLimitOfflineTx = new OfflineTransactionItemRequest(
                UUID.randomUUID(), accountId, passengerId, "CASINO:CHIPS",
                new BigDecimal("900.00"), "USD", TransactionType.CHARGE,
                null, "POS-OFFLINE-01", "idem-offline-conflict-002", offlineTime2, "Over limit offline charge");

        OfflineBatchSyncRequest batch1 = new OfflineBatchSyncRequest(
                UUID.randomUUID(), null, "POS-OFFLINE-01",
                List.of(validOfflineTx, overLimitOfflineTx));

        OfflineBatchSyncResponse res1 = service.syncOfflineTransactions(batch1, financePrincipal);

        assertThat(res1.syncedCount()).isEqualTo(1);
        assertThat(res1.conflictCount()).isEqualTo(1);
        assertThat(res1.duplicateIgnoredCount()).isEqualTo(0);
        assertThat(res1.results().get(0).occurrenceTime()).isEqualTo(offlineTime1);
        assertThat(res1.results().get(1).conflictCode()).isEqualTo("OFFLINE_SPENDING_LIMIT_EXCEEDED");

        // Re-sync the same valid item -> idempotently ignored as duplicate
        OfflineBatchSyncRequest batch2 = new OfflineBatchSyncRequest(
                UUID.randomUUID(), null, "POS-OFFLINE-01",
                List.of(validOfflineTx));

        OfflineBatchSyncResponse res2 = service.syncOfflineTransactions(batch2, financePrincipal);
        assertThat(res2.duplicateIgnoredCount()).isEqualTo(1);
        assertThat(res2.syncedCount()).isEqualTo(0);
    }

    // =========================================================================
    // 13. End-of-Cruise Settlement & Account Closure
    // =========================================================================
    @Test
    @DisplayName("13. End-of-cruise settlement calculates balance, collects payment, and closes onboard account")
    void testEndOfCruiseSettlement_FullLifecycle() {
        when(onboardAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(activeAccount));
        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(chargeDisputeRepository.findByCharge_Transaction_OnboardAccount_OnboardAccountIdOrderByReportedAtDesc(accountId))
                .thenReturn(Collections.emptyList());

        // Post 200 USD charge and 30 USD refund -> Net outstanding = 170 USD
        service.recordTransaction(new CreateTransactionRequest(
                accountId, passengerId, "SPA:PACKAGE", new BigDecimal("200.00"),
                "USD", TransactionType.CHARGE, null, "POS-01", "idem-settle-c1", "Spa", false), financePrincipal);

        UUID chargeTxId = storedTransactions.get(0).getTransactionId();
        when(onboardTransactionRepository.findById(chargeTxId)).thenReturn(Optional.of(storedTransactions.get(0)));

        service.refundTransaction(chargeTxId,
                new TransactionActionRequest(new BigDecimal("30.00"), "Promo credit", "idem-settle-r1"),
                financePrincipal);

        final List<SettlementItem> savedItems = new ArrayList<>();
        when(settlementItemRepository.save(any(SettlementItem.class))).thenAnswer(inv -> {
            SettlementItem item = inv.getArgument(0);
            item.setSettlementItemId(UUID.randomUUID());
            savedItems.add(item);
            return item;
        });

        UUID settlementId = UUID.randomUUID();
        final FinalSettlement[] holder = new FinalSettlement[1];
        when(finalSettlementRepository.save(any(FinalSettlement.class))).thenAnswer(inv -> {
            FinalSettlement fs = inv.getArgument(0);
            if (fs.getFinalSettlementId() == null) {
                fs.setFinalSettlementId(settlementId);
            }
            holder[0] = fs;
            return fs;
        });
        when(finalSettlementRepository.findById(settlementId)).thenAnswer(inv -> Optional.ofNullable(holder[0]));
        when(settlementItemRepository.findByFinalSettlement_FinalSettlementIdOrderByOccurredAtAsc(settlementId))
                .thenAnswer(inv -> new ArrayList<>(savedItems));

        // 1. Initiate settlement statement
        SettlementStatementResponse statement = service.initiateSettlement(
                new InitiateSettlementRequest(accountId, false), financePrincipal);

        assertThat(statement.settlementStatus()).isEqualTo(FinalSettlementStatus.Pending);
        assertThat(statement.totalCharges()).isEqualByComparingTo("200.0000");
        assertThat(statement.totalRefunds()).isEqualByComparingTo("30.0000");
        assertThat(statement.outstandingBalance()).isEqualByComparingTo("170.0000");

        // 2. Register tokenized card and process settlement
        RegisteredPaymentMethod pm = new RegisteredPaymentMethod();
        pm.setPaymentMethodId(UUID.randomUUID());
        pm.setPassenger(passenger);
        pm.setProvider("MOCK_INTL_GATEWAY");
        pm.setPaymentToken("tok_visa_sg_settle");
        pm.setStatus("ACTIVE");
        when(registeredPaymentMethodRepository.findById(pm.getPaymentMethodId())).thenReturn(Optional.of(pm));

        SettlementStatementResponse completed = service.processSettlement(
                settlementId,
                new ProcessSettlementRequest(
                        pm.getPaymentMethodId(), null, null,
                        false, "USD", false, "idem-settle-pay-1", false),
                financePrincipal);

        assertThat(completed.settlementStatus()).isEqualTo(FinalSettlementStatus.Completed);
        assertThat(completed.outstandingBalance()).isEqualByComparingTo("0.0000");
        assertThat(completed.accountStatus()).isEqualTo(OnboardAccountStatus.CLOSED);
    }

    // =========================================================================
    // 14. Unauthorized Access & Raw Card Security Rejection
    // =========================================================================
    @Test
    @DisplayName("14. Reject unauthorized passenger access to another passenger's account and reject raw PAN storage")
    void testUnauthorizedAccessAndRawCardRejection() {
        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        User otherUser = new User();
        otherUser.setUserId(UUID.randomUUID());
        otherUser.setUsername("intruder_passenger");
        otherUser.setRoles(Set.of(Role.PASSENGER));
        otherUser.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
        CasmsPrincipal otherPassengerPrincipal = new CasmsPrincipal(otherUser, null);

        // 1. Intruder passenger cannot read another passenger's onboard account
        assertThatThrownBy(() -> service.getAccount(accountId, otherPassengerPrincipal))
                .isInstanceOf(AccessDeniedException.class);

        // 2. Passenger cannot self-approve spending limit override
        when(onboardAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(activeAccount));
        assertThatThrownBy(() -> service.updateSpendingLimit(
                accountId,
                new UpdateSpendingLimitRequest(new BigDecimal("5000.00"), "Self upgrade", true),
                passengerPrincipal))
                .isInstanceOf(AccessDeniedException.class);

        // 3. Reject raw 16-digit PAN in registerPaymentMethod
        when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
        RegisterPaymentMethodRequest rawCardReq = new RegisterPaymentMethodRequest(
                passengerId, "STRIPE", "4111222233334444", null,
                "VISA", "4444", "NGUYEN VAN MINH", 12, 2029, "SG", "SGD", true);

        assertThatThrownBy(() -> service.registerPaymentMethod(rawCardReq, passengerPrincipal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Never send or store raw card numbers");
    }

    // =========================================================================
    // 15. Webhook Signature Validation & Idempotency
    // =========================================================================
    @Test
    @DisplayName("15. Verify HMAC-SHA256 webhook signature and process webhook idempotently")
    void testWebhookSignatureValidationAndIdempotency() {
        Payment pendingPayment = new Payment();
        pendingPayment.setPaymentId(UUID.randomUUID());
        pendingPayment.setRequestedAmount(new BigDecimal("250.0000"));
        pendingPayment.setCurrency("USD");
        pendingPayment.setStatus(PaymentStatus.Pending);
        pendingPayment.setProcessorReference("MOCK-CHG-999");

        String rawJson = "{\"externalEventId\":\"evt_001\",\"eventType\":\"PAYMENT_CAPTURED\"}";
        WebhookNotificationPayload payload = new WebhookNotificationPayload(
                "evt_001", "PAYMENT_CAPTURED", pendingPayment.getPaymentId(),
                "MOCK-CHG-999", new BigDecimal("250.0000"), "USD", null);

        // 1. Invalid signature -> rejected with 401
        assertThatThrownBy(() -> service.processGatewayWebhook(
                "MOCK_INTL_GATEWAY", "invalid_hmac_sig", rawJson, payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid HMAC-SHA256 webhook signature");

        // 2. Valid HMAC-SHA256 signature -> processed and updates payment to Captured
        String validSignature = paymentGateway.computeHmacSha256(rawJson, "casms-webhook-secret-key-2026");
        when(paymentWebhookEventRepository.findByExternalEventId("evt_001")).thenReturn(Optional.empty());
        when(paymentRepository.findByIdForUpdate(pendingPayment.getPaymentId())).thenReturn(Optional.of(pendingPayment));
        when(paymentWebhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent ev = inv.getArgument(0);
            ev.setWebhookEventId(UUID.randomUUID());
            return ev;
        });

        Map<String, Object> result = service.processGatewayWebhook(
                "MOCK_INTL_GATEWAY", validSignature, rawJson, payload);

        assertThat(result.get("status")).isEqualTo("PROCESSED");
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.Captured);
        assertThat(pendingPayment.getCapturedAmount()).isEqualByComparingTo("250.0000");
    }
}
