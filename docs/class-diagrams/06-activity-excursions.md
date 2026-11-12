# Sơ Đồ Lớp Thiết Kế Chi Tiết: Phân Hệ Hoạt Động Giải Trí & Tour Bờ (Activity & Excursion Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho các hoạt động biểu diễn nghệ thuật, thể thao trên tàu, tour tham quan bờ biển và vận hành linh hoạt thời tiết (Dynamic Rescheduling).

## Mã Mermaid UML (Full-Stack 4 Tầng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class OnboardOperationController {
        -OnboardOperationService operationService
        +registerActivity(bookingPassengerId, request) ApiResponse
        +rescheduleActivity(activityId, request) ApiResponse
        +syncOfflineTransactions(transactions) ApiResponse
    }

    class ActivityController {
        -ActivityService activityService
        +createActivity(request) ApiResponse
        +listActivities(cruiseId, category) ApiResponse
        +getActivityDetail(activityId) ApiResponse
        +cancelActivity(activityId, reason) ApiResponse
    }

    class ShoreExcursionController {
        -ShoreExcursionService excursionService
        +createExcursion(request) ApiResponse
        +listExcursions(cruiseId, portId) ApiResponse
        +checkAvailability(excursionId) ApiResponse
        +confirmExcursion(excursionId) ApiResponse
    }

    %% 2. Service Layer
    class OnboardOperationService {
        <<interface>>
        +registerActivity(bookingPassengerId, activityId, notes) Registration
        +rescheduleActivity(activityId, newStartTime, newLocation, isMovedIndoor) OnboardActivity
        +syncOfflineTransactions(offlineTransactions) List~OnboardTransaction~
    }

    class ActivityService {
        -OnboardActivityRepository activityRepo
        -RegistrationRepository registrationRepo
        -ParticipationRepository participationRepo
        +createActivity(dto) OnboardActivity
        +findActivitiesByCruise(cruiseId) List~OnboardActivity~
        +checkCapacity(activityId) boolean
        +cancelActivity(activityId, reason) OnboardActivity
    }

    class ShoreExcursionService {
        -ShoreExcursionRepository excursionRepo
        -TourGroupRepository tourGroupRepo
        -SupplierRepository supplierRepo
        +createExcursion(dto) ShoreExcursion
        +findExcursionsByPort(portId) List~ShoreExcursion~
        +assignTourGuide(tourGroupId, guideName) TourGroup
    }

    %% 3. Repository Layer
    class OnboardActivityRepository {
        <<interface>>
        +findById(id) Optional~OnboardActivity~
        +findByStatus(status) List~OnboardActivity~
        +findByCruiseId(cruiseId) List~OnboardActivity~
        +save(activity) OnboardActivity
    }

    class RegistrationRepository {
        <<interface>>
        +countByOnboardActivityId(activityId) Long
        +existsByBookingPassengerAndActivity(passengerId, activityId) Boolean
        +findByBookingPassengerId(passengerId) List~Registration~
        +save(registration) Registration
    }

    class ParticipationRepository {
        <<interface>>
        +findByRegistrationId(registrationId) Optional~Participation~
        +save(participation) Participation
    }

    class ShoreExcursionRepository {
        <<interface>>
        +findById(id) Optional~ShoreExcursion~
        +findByCruiseStopId(cruiseStopId) List~ShoreExcursion~
        +save(excursion) ShoreExcursion
    }

    class TourGroupRepository {
        <<interface>>
        +findByShoreExcursionId(excursionId) List~TourGroup~
        +save(group) TourGroup
    }

    %% 4. Entity Layer
    class OnboardActivity {
        +UUID onboardActivityId
        +String activityCode
        +String activityName
        +OnboardActivityCategory category
        +Instant startTime
        +Instant endTime
        +String location
        +String meetingPoint
        +Integer capacity
        +BigDecimal price
        +OnboardActivityStatus status
    }

    class Registration {
        +UUID registrationId
        +Instant registrationTime
        +Integer quantity
        +RegistrationStatus status
        +String cancellationReason
    }

    class Participation {
        +UUID participationId
        +ParticipationStatus status
        +Instant checkedInAt
        +String attendedByStaff
    }

    class ShoreExcursion {
        +UUID shoreExcursionId
        +String excursionCode
        +String excursionTitle
        +BigDecimal pricePerAdult
        +Integer maximumCapacity
        +ShoreExcursionStatus status
    }

    class TourGroup {
        +UUID tourGroupId
        +String groupName
        +Integer maxPax
        +String assignedGuideName
        +TourGroupStatus status
    }

    %% Inter-Layer and Domain Relationships
    OnboardOperationController --> OnboardOperationService : delegatesTo
    ActivityController --> ActivityService : delegatesTo
    ShoreExcursionController --> ShoreExcursionService : delegatesTo

    ActivityService --> OnboardActivityRepository : accesses
    ActivityService --> RegistrationRepository : accesses
    ActivityService --> ParticipationRepository : accesses
    ShoreExcursionService --> ShoreExcursionRepository : accesses
    ShoreExcursionService --> TourGroupRepository : accesses

    OnboardActivityRepository ..> OnboardActivity : manages
    RegistrationRepository ..> Registration : manages
    ParticipationRepository ..> Participation : manages
    ShoreExcursionRepository ..> ShoreExcursion : manages
    TourGroupRepository ..> TourGroup : manages

    OnboardActivity "1" *-- "0..*" Registration : receivesRegistrations
    Registration "1" *-- "0..1" Participation : leadsToAttendance
    ShoreExcursion "1" *-- "1..*" TourGroup : organizesGroups
```
