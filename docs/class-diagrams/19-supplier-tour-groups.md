# Sơ Đồ Lớp Thiết Kế Chi Tiết: Đối Tác Cung Ứng & Phân Bổ Nhóm Tour Bờ (Suppliers & Tour Groups Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho quản lý nhà cung cấp dịch vụ địa phương (Supplier) và chia nhóm du khách tham quan trên bờ (Tour Group & Passenger Assignments).

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class SupplierController {
        -SupplierService supplierService
        +registerSupplier(request) ApiResponse~SupplierResponse~
        +listSuppliers(portId) ApiResponse~List~SupplierResponse~~
        +evaluateSupplierPerformance(id) ApiResponse
    }

    class TourGroupController {
        -TourGroupService groupService
        +createGroup(excursionId, request) ApiResponse~TourGroupResponse~
        +listGroupsByExcursion(excursionId) ApiResponse
        +assignGuide(groupId, guideName) ApiResponse
    }

    class TourGroupPassengerController {
        -TourGroupPassengerService assignmentService
        +assignPassengerToGroup(passengerId, groupId) ApiResponse
        +batchAssignCabinToGroup(cabinId, groupId) ApiResponse
        +removePassengerFromGroup(assignmentId) ApiResponse
    }

    %% 2. Service Layer
    class SupplierService {
        -SupplierRepository supplierRepo
        +onboardSupplier(dto) Supplier
        +verifyLicense(supplierId) boolean
        +findSuppliersByPort(portId) List~Supplier~
    }

    class TourGroupService {
        -TourGroupRepository groupRepo
        -ShoreExcursionRepository excursionRepo
        +createTourGroup(excursionId, groupName, maxPax) TourGroup
        +checkGroupCapacity(groupId) boolean
        +rebalanceGroups(excursionId) void
    }

    class TourGroupPassengerService {
        -TourGroupPassengerRepository assignmentRepo
        -BookingPassengerRepository passengerRepo
        +assignPassenger(passengerId, groupId) TourGroupPassenger
        +autoAssignPassengersByLanguage(excursionId) void
    }

    %% 3. Repository Layer
    class SupplierRepository {
        +findById(id) Optional~Supplier~
        +findByStatus(status) List~Supplier~
        +save(supplier) Supplier
    }

    class TourGroupRepository {
        +findByShoreExcursionId(excursionId) List~TourGroup~
        +findById(id) Optional~TourGroup~
        +save(group) TourGroup
    }

    class TourGroupPassengerRepository {
        +findByTourGroupId(groupId) List~TourGroupPassenger~
        +countByTourGroupId(groupId) Long
        +save(assignment) TourGroupPassenger
        +saveAll(assignments) List~TourGroupPassenger~
    }

    %% 4. Entity Layer
    class Supplier {
        +UUID supplierId
        +String companyName
        +String licenseNumber
        +String contactEmail
        +String contactPhone
        +SupplierStatus status
    }

    class ShoreExcursion {
        +UUID shoreExcursionId
        +String excursionCode
        +String excursionTitle
        +BigDecimal pricePerAdult
        +Integer maximumCapacity
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
        +UUID bookingPassengerId
        +Instant assignedAt
        +TourGroupPassengerAssignmentStatus status
    }

    %% Inter-Layer and Domain Relationships
    SupplierController --> SupplierService : delegatesTo
    TourGroupController --> TourGroupService : delegatesTo
    TourGroupPassengerController --> TourGroupPassengerService : delegatesTo

    SupplierService --> SupplierRepository : accesses
    TourGroupService --> TourGroupRepository : accesses
    TourGroupPassengerService --> TourGroupPassengerRepository : accesses

    SupplierRepository ..> Supplier : manages
    TourGroupRepository ..> TourGroup : manages
    TourGroupPassengerRepository ..> TourGroupPassenger : manages

    Supplier "1" o-- "0..*" ShoreExcursion : operates
    ShoreExcursion "1" *-- "1..*" TourGroup : organizesGroups
    TourGroup "1" *-- "0..*" TourGroupPassenger : assignsMembers
```
