# Phân Hệ Hoạt Động Giải Trí Trên Tàu & Tour Tham Quan Bờ (Onboard Activities & Excursions)

Mô hình hóa hoạt động giải trí (`OnboardActivity`), đăng ký ghế ngồi (`Registration`), điểm danh (`Participation`), tour khám phá bờ biển (`ShoreExcursion`) và đơn vị cung cấp đối tác địa phương (`Supplier`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

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
        +String currencyCode
        +OnboardActivityStatus status
        +Boolean catalogEntry
    }

    class Registration {
        +UUID registrationId
        +Instant registrationTime
        +Integer quantity
        +RegistrationStatus status
        +String cancellationReason
        +String notes
        +UUID fareBenefitSnapshotId
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
        +BigDecimal pricePerChild
        +Integer maximumCapacity
        +Instant plannedDepartureTime
        +Instant plannedReturnTime
        +ShoreExcursionStatus status
    }

    class TourGroup {
        +UUID tourGroupId
        +String groupName
        +Integer maxPax
        +String assignedGuideName
        +TourGroupStatus status
    }

    class TourGroupPassenger {
        +UUID assignmentId
        +Instant assignedAt
        +TourGroupPassengerAssignmentStatus status
    }

    class Supplier {
        +UUID supplierId
        +String companyName
        +String licenseNumber
        +String contactEmail
        +SupplierStatus status
    }

    class OnboardActivityCategory {
        <<enumeration>>
        SPORTS_WELLNESS
        ENTERTAINMENT
        WORKSHOPS_CLASSES
        FAMILY_KIDS
        SPECIAL_EVENTS
    }

    class OnboardActivityStatus {
        <<enumeration>>
        PLANNED
        OPEN
        FULL
        IN_PROGRESS
        COMPLETED
        CANCELLED
        MOVED_INDOOR
    }

    %% Relationships
    OnboardActivity "1" *-- "0..*" Registration : receivesRegistrations
    Registration "1" *-- "0..1" Participation : leadsToAttendance
    ShoreExcursion "1" *-- "0..*" Registration : excursionBookings
    ShoreExcursion "1" *-- "1..*" TourGroup : organizesGroups
    TourGroup "1" *-- "0..*" TourGroupPassenger : assignsMembers
    Supplier "1" o-- "0..*" ShoreExcursion : operates
    OnboardActivity --> OnboardActivityCategory : categorized
    OnboardActivity --> OnboardActivityStatus : state
```

## Giải Thích Quan Hệ & Ràng Buộc
- **OnboardActivity - Registration - Participation**: Luồng đăng ký từ lúc giữ slot (`Registration`) đến khi có mặt thực tế quét mã tại cửa phòng/sân khấu (`Participation`).
- **Dynamic Rescheduling**: Thuộc tính `status` của `OnboardActivity` hỗ trợ trạng thái `MOVED_INDOOR` và `CANCELLED` khi thời tiết trên biển xấu, cho phép dời lịch hoặc đổi địa điểm mà vẫn giữ nguyên danh sách đăng ký.
- **ShoreExcursion - TourGroup - TourGroupPassenger**: Tour tham quan cảng được chia thành các nhóm nhỏ (`TourGroup`) do hướng dẫn viên (`assignedGuideName`) phụ trách để đảm bảo an toàn và số lượng tối đa (`maxPax`).
- **Supplier**: Nhà cung ứng dịch vụ tại điểm đến địa phương (xe đưa đón, cano cao tốc, nhà hàng đối tác).

