# Sơ Đồ Lớp Thiết Kế Chi Tiết: Nghiệm Thu Kỹ Thuật Trước Chuyến & Quy Định Hàng Hải (Safety Preparation & Maritime Regulations Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho danh mục kiểm tra kỹ thuật an toàn trước khi xuất bến (Pre-Departure Readiness Checklist), quy định hàng hải bắt buộc và quản lý sự cố vận hành cơ khí/hàng hải trên tàu.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class CruisePreparationController {
        -CruisePreparationService prepService
        +getChecklistSections(cruiseId) ApiResponse~List~SectionResponse~~
        +submitSectionInspection(cruiseId, sectionCode, request) ApiResponse
        +evaluateDepartureReadiness(cruiseId) ApiResponse~ReadinessAssessmentResponse~
    }

    class ShipRegulationController {
        -ShipRegulationService regulationService
        +listRegulations(shipId) ApiResponse~List~RegulationResponse~~
        +createRegulation(request) ApiResponse
        +updateRegulation(regulationId, request) ApiResponse
    }

    class OperationalIssueController {
        -OperationalIssueService issueService
        +reportIssue(cruiseId, request) ApiResponse~IssueResponse~
        +listActiveIssues(cruiseId) ApiResponse
        +resolveIssue(issueId, resolutionDto) ApiResponse
    }

    %% 2. Service Layer
    class CruisePreparationService {
        -CruisePreparationSectionRepository sectionRepo
        -PreparationItemRepository itemRepo
        -CruiseRepository cruiseRepo
        +initializeChecklistForCruise(cruiseId) void
        +inspectSection(sectionId, inspectorName, items) CruisePreparationSection
        +isAllMandatorySectionsPassed(cruiseId) boolean
    }

    class ShipRegulationService {
        -ShipRegulationRepository regulationRepo
        +findMandatoryRegulations(shipId) List~ShipRegulation~
        +validateCompliance(shipId) boolean
    }

    class OperationalIssueService {
        -OperationalIssueRepository issueRepo
        +logIncident(cruiseId, severity, description) OperationalIssue
        +escalateToCaptain(issueId) void
        +closeIncident(issueId, correctiveAction) OperationalIssue
    }

    %% 3. Repository Layer
    class CruisePreparationSectionRepository {
        +findByCruiseId(cruiseId) List~CruisePreparationSection~
        +findByCruiseIdAndSectionCode(cruiseId, code) Optional~CruisePreparationSection~
        +save(section) CruisePreparationSection
    }

    class PreparationItemRepository {
        +findBySectionId(sectionId) List~PreparationItem~
        +saveAll(items) List~PreparationItem~
    }

    class ShipRegulationRepository {
        +findByShipId(shipId) List~ShipRegulation~
        +findByIsMandatoryTrue() List~ShipRegulation~
        +save(regulation) ShipRegulation
    }

    class OperationalIssueRepository {
        +findByCruiseIdAndStatus(cruiseId, status) List~OperationalIssue~
        +save(issue) OperationalIssue
    }

    %% 4. Entity Layer
    class CruisePreparationSection {
        +UUID sectionId
        +String sectionCode
        +String sectionTitle
        +PreparationStatus status
        +Instant inspectedAt
        +String inspectedBy
    }

    class PreparationItem {
        +UUID itemId
        +String itemName
        +Boolean isPassed
        +String note
    }

    class ShipRegulation {
        +UUID regulationId
        +String regulationCode
        +String title
        +String contentText
        +String authorityBody
        +Boolean isMandatory
    }

    class OperationalIssue {
        +UUID issueId
        +String issueTitle
        +IssueSeverity severity
        +IssueStatus status
        +Instant reportedAt
        +Instant resolvedAt
        +String resolutionSummary
    }

    class OperationalRegulation {
        +UUID regulationId
        +String regulationType
        +String enforcementDetails
    }

    %% Inter-Layer and Domain Relationships
    CruisePreparationController --> CruisePreparationService : delegatesTo
    ShipRegulationController --> ShipRegulationService : delegatesTo
    OperationalIssueController --> OperationalIssueService : delegatesTo

    CruisePreparationService --> CruisePreparationSectionRepository : accesses
    CruisePreparationService --> PreparationItemRepository : accesses
    ShipRegulationService --> ShipRegulationRepository : accesses
    OperationalIssueService --> OperationalIssueRepository : accesses

    CruisePreparationSectionRepository ..> CruisePreparationSection : manages
    PreparationItemRepository ..> PreparationItem : manages
    ShipRegulationRepository ..> ShipRegulation : manages
    OperationalIssueRepository ..> OperationalIssue : manages

    CruisePreparationSection "1" *-- "1..*" PreparationItem : checklistItems
    ShipRegulation "1" o-- "0..*" OperationalRegulation : categorized
```
