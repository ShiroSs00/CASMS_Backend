# Phân Hệ Đặt Chỗ, Giữ Chỗ & Hợp Đồng Hải Trình (Booking & Contract Lifecycle)

Vòng đời đơn đặt chỗ (`Booking`), danh sách thành viên đi cùng (`BookingPassenger`), phân bổ phòng (`CabinAssignment`), báo giá giữ chỗ (`BookingQuote`) và văn bản hợp đồng vận chuyển hành khách (`BookingContract`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

    class Booking {
        +UUID bookingId
        +String bookingReference
        +BigDecimal agreedFareAmount
        +String currency
        +BookingStatus bookingStatus
        +BookingContractStatus contractStatus
        +String contractDocumentReference
        +Instant contractAcceptedAt
        +PaymentPlan paymentPlan
        +Instant expiresAt
        +Instant createdAt
    }

    class BookingPassenger {
        +UUID bookingPassengerId
        +BookingPassengerParticipationStatus participationStatus
        +String requestedCabinCategory
        +CabinSelectionMode cabinSelectionMode
        +String cabinGroupKey
        +UUID fareOfferId
    }

    class BookingContract {
        +UUID contractId
        +String contractReference
        +String contractTitle
        +Boolean isInternational
        +String documentReference
        +BookingContractStatus status
        +Instant signedAt
        +String physicalArchivalCode
    }

    class CabinAssignment {
        +UUID assignmentId
        +Instant assignedAt
        +Instant checkInTime
        +Instant checkOutTime
        +AssignmentStatus status
    }

    class BookingQuote {
        +UUID quoteId
        +BigDecimal grossAmount
        +BigDecimal discountAmount
        +BigDecimal netPayableAmount
        +Instant validUntil
        +String quoteFingerprint
    }

    class BookingFareSnapshot {
        +UUID snapshotId
        +BigDecimal baseFare
        +BigDecimal portTaxes
        +BigDecimal serviceSurcharges
        +Instant snapshotTime
    }

    %% Relationships
    Booking "1" *-- "1..*" BookingPassenger : includes
    Booking "1" *-- "0..1" BookingContract : signs
    Booking "1" *-- "0..*" BookingQuote : quotedBy
    Booking "1" *-- "0..*" BookingFareSnapshot : freezeFares
    BookingPassenger "0..*" o-- "0..1" CabinAssignment : holdsCabin
    Booking "1" o-- "0..*" CabinAssignment : manages
```

## Giải Thích Quan Hệ & Ràng Buộc
- **Booking - BookingPassenger**: Một đơn đặt chỗ có ít nhất 1 hoặc nhiều hành khách tham gia (`1 *-- 1..*`).
- **Booking - BookingContract**: Hợp đồng vận tải du lịch điện tử ký kết giữa khách hàng và hãng tàu. Thuộc tính `isInternational` phân biệt hợp đồng nội địa (Tuân thủ Bộ luật Hàng hải Việt Nam, Luật Du lịch 2017) với hợp đồng quốc tế (Công ước Athens 1974, quy định xuất nhập cảnh và thị thực).
- **BookingPassenger - CabinAssignment**: Phân bổ buồng phòng cụ thể cho hành khách dựa trên hạng phòng và nhóm cabin (`cabinGroupKey`).
- **BookingFareSnapshot**: Lưu lại ảnh chụp giá vé tại thời điểm đặt phòng nhằm tránh biến động tỷ giá và bảo vệ quyền lợi người tiêu dùng.

