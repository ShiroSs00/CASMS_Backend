# Sơ Đồ Lớp Thiết Kế Tầng Kiến Trúc Phần Mềm (Layered Architecture Class Diagram)

Mô hình hóa chi tiết cấu trúc triển khai mã nguồn thực tế trong Spring Boot theo kiến trúc phân tầng: **Controller Layer ➔ Service Layer ➔ Repository Layer ➔ Entity / Database**, minh họa qua phân hệ Vận hành trên tàu (`OnboardOperation`) và Đặt chỗ (`Booking`).

## Mã Mermaid UML

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
        <<interface>>
        +registerActivity(bookingPassengerId, activityId, notes) Registration
        +rescheduleActivity(activityId, newStartTime, newLocation, isMovedIndoor) OnboardActivity
        +syncOfflineTransactions(offlineTransactions) List~OnboardTransaction~
    }

    class BookingService {
        <<interface>>
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
        <<interface>>
        +findById(id) Optional~OnboardActivity~
        +findByStatus(status) List~OnboardActivity~
    }

    class RegistrationRepository {
        <<interface>>
        +countByOnboardActivityId(activityId) Long
        +existsByBookingPassengerAndActivity(passengerId, activityId) Boolean
        +save(registration) Registration
    }

    class OnboardTransactionRepository {
        <<interface>>
        +findBySyncStatus(status) List~OnboardTransaction~
        +saveAll(transactions) List~OnboardTransaction~
    }

    class BookingRepository {
        <<interface>>
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

## Giải Thích Nguyên Lý Kiến Trúc
- **Controller Layer (Tầng Giao tiếp)**: Tiếp nhận HTTP REST request từ Client (Web/Mobile), thực hiện kiểm tra tính hợp lệ của DTO (`@Valid`), ánh xạ mã trạng thái HTTP chuẩn (`200 OK`, `201 Created`, `400 Bad Request`).
- **Service Layer (Tầng Nghiệp vụ)**: Tách biệt Interface (`OnboardOperationService`) và Implementation (`OnboardOperationServiceImpl`) theo nguyên lý **Inversion of Control (IoC)** và **Dependency Injection (DI)** của Spring Framework, bọc giao dịch bằng `@Transactional`.
- **Repository Layer (Tầng Truy xuất Dữ liệu)**: Mở rộng `JpaRepository<T, ID>` tận dụng các truy vấn tối ưu và Hibernate Caching của Spring Data JPA.
- **Entity Layer (Tầng Thực thể Miền)**: Định nghĩa cấu trúc bảng CSDL, quan hệ khóa ngoại và các ràng buộc toàn vẹn dữ liệu.
