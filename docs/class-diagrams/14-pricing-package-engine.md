# Phân Hệ Động Cơ Định Giá & Phiên Bản Gói Du Thuyền (Cruise Pricing & Package Engine)

Quản lý cây mẫu gói du thuyền (`CruisePackageTemplate`), các phiên bản hiệu lực theo thời gian (`CruisePackageTemplateVersion`), phân bổ giá vé linh hoạt theo hạng phòng và đợt khởi hành (`DepartureCruisePackagePrice`), cùng các ưu đãi dịch vụ đi kèm (`CruisePackageAddonOffer`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

    class CruisePackageTemplate {
        +UUID templateId
        +String templateCode
        +String templateName
        +Boolean isPublished
        +Instant createdAt
    }

    class CruisePackageTemplateVersion {
        +UUID versionId
        +Integer versionNumber
        +String versionName
        +Instant effectiveFrom
        +Instant effectiveTo
        +VersionStatus status
    }

    class DepartureCruisePackage {
        +UUID departurePackageId
        +UUID cruiseId
        +BigDecimal basePrice
        +PackageDepartureStatus status
    }

    class DepartureCruisePackagePrice {
        +UUID priceId
        +String cabinCategoryCode
        +BigDecimal adultPrice
        +BigDecimal childPrice
        +BigDecimal singleSupplementRate
    }

    class CruisePackageBenefit {
        +UUID benefitId
        +String benefitCode
        +String benefitName
        +Integer defaultQuota
    }

    class CruisePackageAddonOffer {
        +UUID addonOfferId
        +String offerTitle
        +BigDecimal promotionalPrice
        +Instant validUntil
    }

    class VersionStatus {
        <<enumeration>>
        DRAFT
        ACTIVE
        EXPIRED
        SUPERSEDED
    }

    class PackageDepartureStatus {
        <<enumeration>>
        OPEN_FOR_SALE
        SOLD_OUT
        CLOSED
    }

    %% Relationships
    CruisePackageTemplate "1" *-- "1..*" CruisePackageTemplateVersion : managesVersions
    CruisePackageTemplateVersion "1" *-- "0..*" DepartureCruisePackage : instantiatesForCruise
    DepartureCruisePackage "1" *-- "1..*" DepartureCruisePackagePrice : pricesByCabin
    CruisePackageTemplateVersion "1" *-- "0..*" CruisePackageBenefit : definesBenefits
    DepartureCruisePackage "1" o-- "0..*" CruisePackageAddonOffer : offersAddons
    CruisePackageTemplateVersion --> VersionStatus : versionStatus
    DepartureCruisePackage --> PackageDepartureStatus : departureStatus
```

## Giải Thích Quan Hệ & Ràng Buộc
- **Quản lý phiên bản gói (Versioning)**: Gói du thuyền mẫu được tách thành nhiều phiên bản (`CruisePackageTemplateVersion`). Điều này đảm bảo khi điều chỉnh giá hoặc chính sách quyền lợi cho các mùa sau, dữ liệu các hợp đồng và vé đã bán của các mùa trước vẫn được bảo toàn nguyên vẹn.
- **Biểu giá động theo hạng phòng**: Mỗi đợt xuất phát (`DepartureCruisePackage`) có bảng giá chi tiết cho người lớn (`adultPrice`), trẻ em (`childPrice`) và phụ thu phòng đơn (`singleSupplementRate`) ứng với từng hạng phòng cụ thể.

