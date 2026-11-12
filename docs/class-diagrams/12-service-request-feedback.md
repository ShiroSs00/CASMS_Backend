# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phục Vụ Buồng Phòng & Đánh Giá (Service Request & Feedback Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho các yêu cầu phục vụ phòng (Room Service, Housekeeping), kiểm soát định mức quyền lợi All-Inclusive và hệ thống thu thập đánh giá độ hài lòng.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class ServicepackageController {
        -ServiceRequestService requestService
        -ServiceUsageQuotaService quotaService
        +createServiceRequest(request) ApiResponse~ServiceRequestResponse~
        +listRequestsByBooking(bookingId) ApiResponse
        +updateStatus(requestId, status) ApiResponse
        +submitFeedback(requestId, feedbackDto) ApiResponse
    }

    class PosController {
        -PosService posService
        +requests(terminalId) ApiResponse~List~ServiceRequestView~~
        +action(requestId, actionDto) ApiResponse~ServiceRequestView~
    }

    %% 2. Service Layer
    class ServiceRequestService {
        -ServiceRequestRepository requestRepo
        -ServiceRepository serviceRepo
        -BookingRepository bookingRepo
        +createRequest(bookingId, serviceId, count) ServiceRequest
        +assignToStaff(requestId, staffUserId) ServiceRequest
        +fulfillRequest(requestId) ServiceRequest
        +cancelRequest(requestId, reason) ServiceRequest
    }

    class ServiceUsageQuotaService {
        -ServiceUsageRepository usageRepo
        -ServiceRequestRepository requestRepo
        +checkQuotaAvailable(passengerId, serviceId) boolean
        +deductQuotaOrSurcharge(requestId, quantity) ServiceUsage
    }

    class FeedbackService {
        -FeedbackRepository feedbackRepo
        -ServiceRequestRepository requestRepo
        +submitRating(requestId, score, comments) Feedback
        +getAverageScoreByService(serviceId) Double
    }

    %% 3. Repository Layer
    class ServiceRequestRepository {
        +findById(id) Optional~ServiceRequest~
        +findByBookingPassengerId(passengerId) List~ServiceRequest~
        +findByStatus(status) List~ServiceRequest~
        +save(request) ServiceRequest
    }

    class ServiceUsageRepository {
        +findByServiceRequestId(requestId) List~ServiceUsage~
        +sumConsumedByPassengerAndService(passengerId, serviceId) Long
        +save(usage) ServiceUsage
    }

    class FeedbackRepository {
        +findByServiceRequestId(requestId) Optional~Feedback~
        +findAverageScore(serviceId) Double
        +save(feedback) Feedback
    }

    class ServiceRepository {
        +findById(id) Optional~Service~
        +findByServiceCode(code) Optional~Service~
        +save(service) Service
    }

    %% 4. Entity Layer
    class ServiceRequest {
        +UUID serviceRequestId
        +String usageMode
        +ServiceUsagePolicy usagePolicySnapshot
        +Integer usageLimitSnapshot
        +String usageTimeZoneSnapshot
        +UUID fareBenefitSnapshotId
        +ServiceRequestStatus status
        +Instant requestedAt
        +Instant completedAt
    }

    class ServiceUsage {
        +UUID usageId
        +Integer consumedQuantity
        +Instant usedAt
        +String recordedByStaff
    }

    class ServiceAdditionalCharge {
        +UUID chargeId
        +BigDecimal surchargeAmount
        +String chargeReason
        +Instant chargedAt
    }

    class Product {
        +UUID productId
        +String productCode
        +String productName
        +BigDecimal unitPrice
        +Boolean isAvailable
    }

    class Feedback {
        +UUID feedbackId
        +Integer ratingScore
        +String comments
        +Instant submittedAt
        +Boolean isAnonymous
    }

    class DeckServiceAssignment {
        +UUID assignmentId
        +UUID deckId
        +UUID staffUserId
        +String shiftName
        +Instant assignedDate
    }

    %% Inter-Layer and Domain Relationships
    ServicepackageController --> ServiceRequestService : delegatesTo
    ServicepackageController --> ServiceUsageQuotaService : delegatesTo
    ServicepackageController --> FeedbackService : delegatesTo
    PosController --> ServiceRequestService : delegatesTo

    ServiceRequestService --> ServiceRequestRepository : accesses
    ServiceRequestService --> ServiceRepository : accesses
    ServiceUsageQuotaService --> ServiceUsageRepository : accesses
    ServiceUsageQuotaService --> ServiceRequestRepository : accesses
    FeedbackService --> FeedbackRepository : accesses

    ServiceRequestRepository ..> ServiceRequest : manages
    ServiceUsageRepository ..> ServiceUsage : manages
    FeedbackRepository ..> Feedback : manages
    ServiceRepository ..> Product : manages

    ServiceRequest "1" *-- "0..*" ServiceUsage : consumesQuota
    ServiceRequest "1" *-- "0..*" ServiceAdditionalCharge : inccursSurcharges
    ServiceRequest "0..*" --> "1" Product : requestsProduct
    ServiceRequest "1" o-- "0..1" Feedback : reviewedBy
```
