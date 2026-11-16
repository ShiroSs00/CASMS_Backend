# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Thanh Toán & Trả Góp (Payment & Installments Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho việc tích hợp cổng thanh toán trực tuyến, xử lý Webhook ngân hàng VietQR/SePay, chia đợt trả góp và xuất hóa đơn điện tử.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class PaymentController {
        -PaymentService paymentService
        +createPayment(request) ApiResponse~PaymentResponse~
        +confirmPayment(paymentId) ApiResponse~PaymentResponse~
        +failPayment(paymentId) ApiResponse~PaymentResponse~
        +getPaymentsForBooking(bookingId) ApiResponse
        +getPaymentDetail(paymentId) ApiResponse
    }

    class SepayController {
        -SepayService sepayService
        +handleWebhook(payload) ApiResponse~SepayWebhookResponse~
        +getTransactionsByBooking(bookingId) ApiResponse
    }

    class InvoiceController {
        -InvoiceService invoiceService
        +issueInvoice(paymentId) ApiResponse~InvoiceResponse~
        +getInvoice(invoiceId) ApiResponse~InvoiceResponse~
    }

    class GuestPaymentExperienceController {
        -GuestPaymentExperienceService guestPaymentService
        +processCreditCard(request) ApiResponse~CreditCardPaymentResponse~
        +validatePromoCode(request) ApiResponse~PromoCodeResponse~
        +createGroupSplit(request) ApiResponse~GroupSplitResponse~
        +getGroupSplitStatus(splitGroupId) ApiResponse~GroupSplitResponse~
        +requestVatInvoice(request) ApiResponse~CorporateVatInvoiceResponse~
    }

    %% 2. Service Layer
    class PaymentService {
        -PaymentRepository paymentRepo
        -InstallmentRepository installmentRepo
        -BookingRepository bookingRepo
        +createPayment(dto) PaymentResponse
        +confirmPayment(paymentId) PaymentResponse
        +matchWithBooking(transactionRef, amount) boolean
        +updateInstallmentProgress(bookingId, paidAmount) void
    }

    class GuestPaymentExperienceService {
        -BookingRepository bookingRepo
        -PaymentRepository paymentRepo
        -PaymentService paymentService
        -InvoiceRepository invoiceRepo
        +processCreditCardPayment(request) CreditCardPaymentResponse
        +validateAndApplyPromoCode(request) PromoCodeResponse
        +createGroupSplitPayment(request) GroupSplitResponse
        +issueCorporateVatInvoice(request) CorporateVatInvoiceResponse
    }

    class SepayService {
        -SepayTransactionRepository sepayRepo
        -PaymentService paymentService
        +processWebhookTransaction(webhookData) SepayTransaction
        +verifySignature(payload, signature) boolean
    }

    class InvoiceService {
        -InvoiceRepository invoiceRepo
        -InvoiceLineRepository invoiceLineRepo
        +createInvoiceFromPayment(paymentId) Invoice
        +exportPdf(invoiceId) byte[]
    }

    %% 3. Repository Layer
    class PaymentRepository {
        +findById(id) Optional~Payment~
        +findByTransactionReference(ref) Optional~Payment~
        +findByBookingId(bookingId) List~Payment~
        +save(payment) Payment
    }

    class SepayTransactionRepository {
        +findByReferenceNumber(refNum) Optional~SepayTransaction~
        +existsBySepayId(sepayId) boolean
        +save(transaction) SepayTransaction
    }

    class InstallmentRepository {
        +findByBookingIdOrderBySequenceNo(bookingId) List~Installment~
        +findOverdueInstallments(asOfDate) List~Installment~
        +save(installment) Installment
    }

    class InvoiceRepository {
        +findByInvoiceNumber(invoiceNo) Optional~Invoice~
        +findByPaymentId(paymentId) Optional~Invoice~
        +save(invoice) Invoice
    }

    %% 4. Entity Layer
    class Payment {
        +UUID paymentId
        +BigDecimal amount
        +String currency
        +PaymentStatus status
        +PaymentType paymentType
        +String paymentMethod
        +String transactionReference
        +Instant paidAt
        +Instant createdAt
    }

    class SepayTransaction {
        +UUID sepayTransactionId
        +Long sepayId
        +String gateway
        +Instant transactionDate
        +String accountNumber
        +BigDecimal amountIn
        +String transactionContent
        +String referenceNumber
    }

    class Installment {
        +UUID installmentId
        +Integer sequenceNo
        +String title
        +BigDecimal dueAmount
        +BigDecimal paidAmount
        +LocalDate dueDate
        +InstallmentStatus status
    }

    class Invoice {
        +UUID invoiceId
        +String invoiceNumber
        +BigDecimal subtotalAmount
        +BigDecimal taxAmount
        +BigDecimal totalAmount
        +InvoiceStatus status
        +Instant issuedAt
    }

    class InvoiceLine {
        +UUID lineId
        +String itemDescription
        +Integer quantity
        +BigDecimal unitPrice
        +BigDecimal lineTotal
    }

    %% Inter-Layer and Domain Relationships
    PaymentController --> PaymentService : delegatesTo
    SepayController --> SepayService : delegatesTo
    InvoiceController --> InvoiceService : delegatesTo
    GuestPaymentExperienceController --> GuestPaymentExperienceService : delegatesTo

    GuestPaymentExperienceService --> PaymentRepository : accesses
    GuestPaymentExperienceService --> InvoiceRepository : accesses
    GuestPaymentExperienceService --> PaymentService : syncsInstallments

    SepayService --> SepayTransactionRepository : accesses
    SepayService --> PaymentService : triggersSettlement
    PaymentService --> PaymentRepository : accesses
    PaymentService --> InstallmentRepository : accesses
    InvoiceService --> InvoiceRepository : accesses

    PaymentRepository ..> Payment : manages
    SepayTransactionRepository ..> SepayTransaction : manages
    InstallmentRepository ..> Installment : manages
    InvoiceRepository ..> Invoice : manages

    Payment "1" *-- "0..1" SepayTransaction : matchedWebhook
    Installment "0..*" o-- "0..*" Payment : settledVia
    Payment "1" --> "0..1" Invoice : generates
    Invoice "1" *-- "1..*" InvoiceLine : lineItems
```
