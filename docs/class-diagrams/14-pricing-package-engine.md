# Sơ Đồ Lớp Thiết Kế Chi Tiết: Động Cơ Định Giá & Phiên Bản Gói (Pricing & Package Engine Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho quản lý mẫu gói tour du thuyền, vòng đời phiên bản biểu giá (Versioning) và ma trận định giá theo từng hạng phòng cho mỗi đợt khởi hành.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class CruisePackageController {
        -CruisePackageService packageService
        +createTemplate(request) ApiResponse~CruisePackageResponse~
        +publishVersion(templateId, versionDto) ApiResponse
        +listPackages(pageable) ApiResponse
        +getPackageDetail(templateId) ApiResponse
    }

    class DepartureCruisePackageController {
        -CruisePackageCabinMappingService mappingService
        +mapCabinsToPackage(departureId, mappingRequest) ApiResponse
        +setPackagePricing(departureId, priceDto) ApiResponse
        +getDeparturePackagePrice(departureId, cabinCategory) ApiResponse
    }

    %% 2. Service Layer
    class CruisePackageService {
        -CruisePackageRepository packageRepo
        -CruisePackageEntitlementService entitlementService
        +createPackageTemplate(dto) CruisePackageTemplate
        +publishTemplateVersion(templateId, versionNum) CruisePackageTemplateVersion
        +findActivePackagesForCruise(cruiseId) List~DepartureCruisePackage~
    }

    class CruisePackageAddonService {
        -CruisePackageRepository packageRepo
        +attachAddonOffer(departurePackageId, offerDto) CruisePackageAddonOffer
        +removeAddonOffer(addonOfferId) void
    }

    class CruisePackageCabinMappingService {
        -DepartureCruisePackageRepository departureRepo
        -DepartureCruisePackagePriceRepository priceRepo
        +setPricesForDeparture(departureId, priceList) void
        +calculateFareForCabin(departureId, cabinCode, paxCount) BigDecimal
    }

    %% 3. Repository Layer
    class CruisePackageRepository {
        +findById(id) Optional~CruisePackageTemplate~
        +findByTemplateCode(code) Optional~CruisePackageTemplate~
        +findPublishedTemplates() List~CruisePackageTemplate~
        +save(template) CruisePackageTemplate
    }

    class DepartureCruisePackageRepository {
        +findByCruiseId(cruiseId) List~DepartureCruisePackage~
        +findById(id) Optional~DepartureCruisePackage~
        +save(departurePackage) DepartureCruisePackage
    }

    class DepartureCruisePackagePriceRepository {
        +findByDeparturePackageId(departurePackageId) List~DepartureCruisePackagePrice~
        +findByDeparturePackageIdAndCabinCategoryCode(pkgId, code) Optional~DepartureCruisePackagePrice~
        +saveAll(prices) List~DepartureCruisePackagePrice~
    }

    %% 4. Entity Layer
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

    %% Inter-Layer and Domain Relationships
    CruisePackageController --> CruisePackageService : delegatesTo
    DepartureCruisePackageController --> CruisePackageCabinMappingService : delegatesTo
    CruisePackageController --> CruisePackageAddonService : delegatesTo

    CruisePackageService --> CruisePackageRepository : accesses
    CruisePackageAddonService --> CruisePackageRepository : accesses
    CruisePackageCabinMappingService --> DepartureCruisePackageRepository : accesses
    CruisePackageCabinMappingService --> DepartureCruisePackagePriceRepository : accesses

    CruisePackageRepository ..> CruisePackageTemplate : manages
    DepartureCruisePackageRepository ..> DepartureCruisePackage : manages
    DepartureCruisePackagePriceRepository ..> DepartureCruisePackagePrice : manages

    CruisePackageTemplate "1" *-- "1..*" CruisePackageTemplateVersion : managesVersions
    CruisePackageTemplateVersion "1" *-- "0..*" DepartureCruisePackage : instantiatesForCruise
    DepartureCruisePackage "1" *-- "1..*" DepartureCruisePackagePrice : pricesByCabin
    CruisePackageTemplateVersion "1" *-- "0..*" CruisePackageBenefit : definesBenefits
    DepartureCruisePackage "1" o-- "0..*" CruisePackageAddonOffer : offersAddons
```
