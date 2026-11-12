# Phân Hệ Cổng Thanh Toán Trực Tuyến & Lịch Trả Góp (Payment & Installments)

Giao dịch thanh toán (`Payment`), tích hợp cổng VietQR/SePay (`SepayTransaction`), lộ trình trả góp chia đợt (`Installment`), hóa đơn (`Invoice`) và dòng chi tiết hóa đơn (`InvoiceLine`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

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
        +String code
    }

    class Installment {
        +UUID installmentId
        +Integer sequenceNo
        +String title
        +BigDecimal dueAmount
        +BigDecimal paidAmount
        +LocalDate dueDate
        +InstallmentStatus status
        +Instant fullyPaidAt
    }

    class Invoice {
        +UUID invoiceId
        +String invoiceNumber
        +BigDecimal subtotalAmount
        +BigDecimal taxAmount
        +BigDecimal totalAmount
        +String taxIdNumber
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

    class ChargeDispute {
        +UUID disputeId
        +BigDecimal disputedAmount
        +String reason
        +DisputeStatus status
        +Instant resolvedAt
    }

    class PaymentStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        FAILED
        REFUNDED
    }

    class InstallmentStatus {
        <<enumeration>>
        PENDING
        PARTIALLY_PAID
        PAID
        OVERDUE
    }

    %% Relationships
    Payment "1" *-- "0..1" SepayTransaction : matchedWebhook
    Installment "0..*" o-- "0..*" Payment : settledVia
    Payment "1" --> "0..1" Invoice : generates
    Invoice "1" *-- "1..*" InvoiceLine : lineItems
    Payment "1" o-- "0..*" ChargeDispute : disputedUnder
    Payment --> PaymentStatus : status
    Installment --> InstallmentStatus : status
```

## Giải Thích Quan Hệ & Ràng Buộc
- **Payment - SepayTransaction**: Xử lý Webhook tự động từ SePay/VietQR. Khi khách chuyển khoản quét mã VietQR có nội dung khớp mã thanh toán, `SepayTransaction` đối soát tự động chuyển trạng thái `Payment` từ `PENDING` sang `COMPLETED`.
- **Installment**: Hỗ trợ chia nhỏ số tiền cọc và thanh toán nhiều đợt trước ngày khởi hành (ví dụ: Đợt 1 cọc 30%, Đợt 2 thanh toán 40% trước 30 ngày, Đợt 3 thanh toán 30% trước 7 ngày).
- **Invoice - InvoiceLine**: Xuất hóa đơn VAT điện tử cho cơ quan thuế và khách hàng.
- **ChargeDispute**: Cơ chế giải quyết khiếu nại sai lệch cước phí hoặc giao dịch trùng lặp.

