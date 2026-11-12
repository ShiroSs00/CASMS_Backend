# Sơ Đồ Lớp Thiết Kế Tầng Kiến Trúc Phần Mềm (Layered Architecture Class Diagram)

Mô hình hóa chi tiết cấu trúc triển khai mã nguồn thực tế trong Spring Boot theo kiến trúc phân tầng: **Controller Layer ➔ Service Layer ➔ Repository Layer ➔ Entity / Database**, minh họa qua phân hệ Vận hành trên tàu (`OnboardOperation`) và Đặt chỗ (`Booking`).

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% Presentation / Controller Layer
    class OnboardOperationController {
        -OnboardOperationService operationService
        +registerActivity(bookingPassengerId, request) ResponseEntity~ApiResponse~
        +rescheduleActivity(activityId, request) ResponseEntity~ApiResponse~
        +syncOfflineTransactions(transactions) ResponseEntity~ApiResponse~
    }

    class BookingController {
        -BookingService bookingService
        +createBooking(request) ResponseEntity~BookingResponse~
        +confirmBooking(bookingId) ResponseEntity~ApiResponse~
        +acceptContract(bookingId) ResponseEntity~ApiResponse~
    }

    %% Service Layer Interfaces
    class OnboardOperationService {
        +registerActivity(bookingPassengerId, activityId, notes) Registration
        +rescheduleActivity(activityId, newStartTime, newLocation, isMovedIndoor) OnboardActivity
        +syncOfflineTransactions(offlineTransactions) List~OnboardTransaction~
    }

    class BookingService {
        +createBooking(dto) BookingResponse
        +confirmBooking(bookingId) void
        +acceptContract(bookingId) void
    }

    %% Service Layer Implementations
    class OnboardOperationServiceImpl {
        -OnboardActivityRepository activityRepo
        -RegistrationRepository registrationRepo
        -OnboardTransactionRepository transactionRepo
        -BookingPassengerRepository passengerRepo
        +registerActivity(bookingPassengerId, activityId, notes) Registration
        +rescheduleActivity(activityId, newStartTime, newLocation, isMovedIndoor) OnboardActivity
        +syncOfflineTransactions(offlineTransactions) List~OnboardTransaction~
    }

    class BookingServiceImpl {
        -BookingRepository bookingRepo
        -BookingPassengerRepository passengerRepo
        -BookingContractRepository contractRepo
        +createBooking(dto) BookingResponse
        +confirmBooking(bookingId) void
        +acceptContract(bookingId) void
    }

    %% Data Access / Repository Layer
    class OnboardActivityRepository {
        +findById(id) Optional~OnboardActivity~
        +findByStatus(status) List~OnboardActivity~
    }

    class RegistrationRepository {
        +countByOnboardActivityId(activityId) Long
        +existsByBookingPassengerAndActivity(passengerId, activityId) Boolean
        +save(registration) Registration
    }

    class OnboardTransactionRepository {
        +findBySyncStatus(status) List~OnboardTransaction~
        +saveAll(transactions) List~OnboardTransaction~
    }

    class BookingRepository {
        +findByBookingReference(reference) Optional~Booking~
        +save(booking) Booking
    }

    %% Entity Domain Layer
    class OnboardActivity {
        +UUID onboardActivityId
        +String activityName
        +Integer capacity
        +OnboardActivityStatus status
    }

    class Registration {
        +UUID registrationId
        +RegistrationStatus status
        +Instant registrationTime
    }

    class OnboardTransaction {
        +UUID transactionId
        +BigDecimal amount
        +TransactionSyncStatus syncStatus
    }

    class Booking {
        +UUID bookingId
        +String bookingReference
        +BookingStatus bookingStatus
    }

    %% Relationships across Architectural Layers
    OnboardOperationController --> OnboardOperationService : delegatesTo
    BookingController --> BookingService : delegatesTo

    OnboardOperationService <|.. OnboardOperationServiceImpl : implements
    BookingService <|.. BookingServiceImpl : implements

    OnboardOperationServiceImpl --> OnboardActivityRepository : uses
    OnboardOperationServiceImpl --> RegistrationRepository : uses
    OnboardOperationServiceImpl --> OnboardTransactionRepository : uses
    BookingServiceImpl --> BookingRepository : uses

    OnboardActivityRepository ..> OnboardActivity : queries/persists
    RegistrationRepository ..> Registration : queries/persists
    OnboardTransactionRepository ..> OnboardTransaction : queries/persists
    BookingRepository ..> Booking : queries/persists
```
