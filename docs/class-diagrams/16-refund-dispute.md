# Sơ Đồ Lớp Thiết Kế Chi Tiết: Hoàn Tiền & Khiếu Nại Chi Phí (Refund & Charge Dispute Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho quy trình xử lý hoàn tiền khi khách hủy vé (Cancellation Policy) và xử lý khiếu nại cước phí sai lệch tại quầy lễ tân (Charge Dispute).

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class RefundController {
        -RefundService refundService
        +requestRefund(bookingId, request) ApiResponse~RefundResponse~
        +approveRefund(refundId) ApiResponse~RefundResponse~
        +processBankRefund(refundId) ApiResponse~RefundResponse~
        +getRefundHistory(bookingId) ApiResponse
    }

    class ChargeDisputeController {
        -ChargeDisputeService disputeService
        +fileDispute(transactionId, request) ApiResponse~DisputeResponse~
        +reviewDispute(disputeId, reviewDto) ApiResponse~DisputeResponse~
        +resolveDispute(disputeId, resolution) ApiResponse~DisputeResponse~
    }

    %% 2. Service Layer
    class RefundService {
        -RefundRepository refundRepo
        -BookingRepository bookingRepo
        -PaymentRepository paymentRepo
        +calculateCancellationFee(bookingId) BigDecimal
        +createRefundRequest(bookingId, reason) Refund
        +executeRefundPayout(refundId) Refund
    }

    class ChargeDisputeService {
        -ChargeDisputeRepository disputeRepo
        -OnboardTransactionRepository transactionRepo
        -OnboardAccountRepository accountRepo
        +initiateDispute(transactionId, reason, amount) ChargeDispute
        +applyCreditAdjustment(disputeId) void
        +rejectDispute(disputeId, justification) ChargeDispute
    }

    %% 3. Repository Layer
    class RefundRepository {
        +findById(id) Optional~Refund~
        +findByBookingId(bookingId) List~Refund~
        +findByStatus(status) List~Refund~
        +save(refund) Refund
    }

    class RefundLineRepository {
        +findByRefundId(refundId) List~RefundLine~
        +saveAll(lines) List~RefundLine~
    }

    class ChargeDisputeRepository {
        +findById(id) Optional~ChargeDispute~
        +findByOnboardAccountId(accountId) List~ChargeDispute~
        +findByStatus(status) List~ChargeDispute~
        +save(dispute) ChargeDispute
    }

    %% 4. Entity Layer
    class Refund {
        +UUID refundId
        +BigDecimal refundAmount
        +BigDecimal penaltyFee
        +String currencyCode
        +RefundStatus status
        +String refundMethod
        +Instant requestedAt
        +Instant completedAt
    }

    class RefundLine {
        +UUID lineId
        +String itemDescription
        +BigDecimal originalAmount
        +BigDecimal refundedAmount
    }

    class ChargeDispute {
        +UUID disputeId
        +BigDecimal disputedAmount
        +String reason
        +DisputeStatus status
        +Instant resolvedAt
        +String resolutionNotes
    }

    class Charge {
        +UUID chargeId
        +BigDecimal amount
        +String chargeCategory
        +Instant postedAt
    }

    %% Inter-Layer and Domain Relationships
    RefundController --> RefundService : delegatesTo
    ChargeDisputeController --> ChargeDisputeService : delegatesTo

    RefundService --> RefundRepository : accesses
    RefundService --> RefundLineRepository : accesses
    ChargeDisputeService --> ChargeDisputeRepository : accesses

    RefundRepository ..> Refund : manages
    RefundLineRepository ..> RefundLine : manages
    ChargeDisputeRepository ..> ChargeDispute : manages

    Refund "1" *-- "1..*" RefundLine : lineBreakdown
    Charge "1" o-- "0..1" ChargeDispute : disputedBy
```
