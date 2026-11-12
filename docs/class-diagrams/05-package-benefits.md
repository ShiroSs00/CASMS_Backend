# Phân Hệ Gói Du Thuyền, Dịch Vụ & Quyền Lợi (Fare & Service Package)

Quản lý danh mục gói tham quan (`TourPackage`), gói dịch vụ tiện ích trên tàu (`ServicePackage`), các hoạt động đi kèm gói (`TourPackageActivity`), chính sách sử dụng (`ServiceUsagePolicy`) và phân bổ cho khách (`BookingPassengerPackage`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

    class TourPackage {
        +UUID tourPackageId
        +String packageCode
        +String packageName
        +TourPackageType packageType
        +BigDecimal basePrice
        +String currencyCode
        +String description
        +Boolean active
    }

    class ServicePackage {
        +UUID servicePackageId
        +String packageCode
        +String packageName
        +BigDecimal standardRate
        +Boolean isAllInclusive
    }

    class Service {
        +UUID serviceId
        +String serviceCode
        +String serviceName
        +String preCruiseUsagePolicy
        +Integer preCruiseUsageLimit
        +BigDecimal unitPrice
    }

    class TourPackageActivity {
        +UUID tourPackageId
        +UUID onboardActivityId
        +BenefitType benefitType
        +BigDecimal discountedPrice
    }

    class BookingPassengerPackage {
        +UUID passengerPackageId
        +BigDecimal agreedPrice
        +Instant activatedAt
        +PackageStatus status
    }

    class ServiceSchedule {
        +UUID scheduleId
        +Instant startsAt
        +Instant endsAt
        +Integer quotaCapacity
        +String serviceUnitCode
    }

    class BookingCruisePackageBenefitSnapshot {
        +UUID benefitSnapshotId
        +UUID bookingPassengerId
        +String benefitName
        +Long grantedQuantity
        +Long usedQuantity
        +Long remainingQuantity
        +Instant usageStartsAt
        +Instant usageEndsAt
    }

    class BenefitType {
        <<enumeration>>
        INCLUDED
        DISCOUNTED
        EXCLUSIVE_ACCESS
    }

    %% Relationships
    TourPackage "1" *-- "0..*" TourPackageActivity : associates
    ServicePackage "1" *-- "0..*" Service : bundlesServices
    BookingPassengerPackage "0..*" --> "1" TourPackage : purchasedPackage
    Service "1" *-- "0..*" ServiceSchedule : operatesOn
    BookingPassengerPackage "1" *-- "0..*" BookingCruisePackageBenefitSnapshot : grantsEntitlements
    TourPackageActivity --> BenefitType : definedAs
```

## Giải Thích Quan Hệ & Ràng Buộc
- **TourPackage - TourPackageActivity**: Một gói tour liên kết với các hoạt động giải trí trên tàu hoặc tour trên bờ kèm theo loại quyền lợi (`BenefitType`: miễn phí `INCLUDED`, giảm giá `DISCOUNTED`, hoặc quyền vào độc quyền `EXCLUSIVE_ACCESS`).
- **ServicePackage - Service**: Đóng gói các dịch vụ đơn lẻ (Spa, quầy bar cao cấp, internet vệ tinh Starlink) thành các gói All-Inclusive hoặc Add-on tiện ích.
- **BookingCruisePackageBenefitSnapshot**: Bản ghi chụp định mức quyền lợi khả dụng của hành khách (`grantedQuantity`, `usedQuantity`, `remainingQuantity`), hỗ trợ việc kiểm tra và trừ quota tiêu dùng kể cả khi tàu hoạt động ngoại tuyến ngoài khơi.

