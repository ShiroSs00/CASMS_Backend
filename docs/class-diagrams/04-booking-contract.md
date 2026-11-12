# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Đặt Chỗ & Hợp Đồng (Booking & Contract Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho vòng đời đơn đặt chỗ, báo giá giữ phòng, phân bổ buồng phòng và ký hợp đồng vận tải điện tử.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class BookingController {
        -BookingService bookingService
        +getAvailableCabins(cruiseId, categoryCode) ApiResponse
        +createQuote(request) ApiResponse
        +create(request) ApiResponse
        +detail(bookingId) ApiResponse
        +acceptContract(bookingId) ApiResponse
        +printContract(bookingId) ResponseEntity
        +confirm(bookingId) ApiResponse
        +cancel(bookingId) ApiResponse
        +changeCabin(bookingId, request) ApiResponse
    }

    %% 2. Service Layer
    class BookingService {
        -BookingRepository bookingRepo
        -BookingPassengerRepository passengerRepo
        -BookingContractRepository contractRepo
        -CabinAssignmentRepository assignmentRepo
        -BookingQuoteRepository quoteRepo
        +getAvailableCabins(cruiseId, categoryCode) List~AvailableCabinResponse~
        +createQuote(request, principal) BookingQuoteResponse
        +createBooking(request, principal) BookingDetailResponse
        +detail(bookingId, principal) BookingDetailResponse
        +acceptContract(bookingId, principal) BookingDetailResponse
        +generateContractHtml(bookingId, principal) String
        +confirm(bookingId, principal) BookingDetailResponse
        +cancel(bookingId, principal) BookingDetailResponse
        +changeCabin(request, principal) BookingDetailResponse
        +releaseExpiredHolds() int
    }

    %% 3. Repository Layer
    class BookingRepository {
        +findByBookingReference(reference) Optional~Booking~
        +findByUserId(userId, pageable) Page~Booking~
        +existsByCruiseIdAndStatus(cruiseId, status) boolean
        +save(booking) Booking
        +findById(id) Optional~Booking~
    }

    class BookingPassengerRepository {
        +findByBookingId(bookingId) List~BookingPassenger~
        +saveAll(passengers) List~BookingPassenger~
    }

    class BookingContractRepository {
        +findByBookingId(bookingId) Optional~BookingContract~
        +save(contract) BookingContract
    }

    class CabinAssignmentRepository {
        +findByBookingId(bookingId) List~CabinAssignment~
        +existsByCabinIdAndCruiseId(cabinId, cruiseId) boolean
        +save(assignment) CabinAssignment
    }

    class BookingQuoteRepository {
        +findById(quoteId) Optional~BookingQuote~
        +save(quote) BookingQuote
    }

    %% 4. Entity Layer
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

    %% Inter-Layer and Domain Relationships
    BookingController --> BookingService : delegatesTo
    BookingService --> BookingRepository : accesses
    BookingService --> BookingPassengerRepository : accesses
    BookingService --> BookingContractRepository : accesses
    BookingService --> CabinAssignmentRepository : accesses
    BookingService --> BookingQuoteRepository : accesses

    BookingRepository ..> Booking : manages
    BookingPassengerRepository ..> BookingPassenger : manages
    BookingContractRepository ..> BookingContract : manages
    CabinAssignmentRepository ..> CabinAssignment : manages
    BookingQuoteRepository ..> BookingQuote : manages

    Booking "1" *-- "1..*" BookingPassenger : includes
    Booking "1" *-- "0..1" BookingContract : signs
    Booking "1" *-- "0..*" BookingQuote : quotedBy
    BookingPassenger "0..*" o-- "0..1" CabinAssignment : holdsCabin
    Booking "1" o-- "0..*" CabinAssignment : manages
```
