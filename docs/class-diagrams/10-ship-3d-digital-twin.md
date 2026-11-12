# Sơ Đồ Lớp Thiết Kế Chi Tiết: Mô Hình 3D Digital Twin Tàu & CAD/BIM (Ship 3D Model & Assets Design Class Diagram)

Mô hình hóa chi tiết đầy đủ 4 tầng kiến trúc: **Controller ➔ Service ➔ Repository ➔ Entity** cho quy trình quản lý file mô hình 3D đa giác, cây phân cấp đối tượng không gian (Spatial Object Nodes) và tiến trình nhập file CAD/BIM bất đồng bộ.

## Mã Mermaid UML (Tối ưu chuẩn Draw.io không bị đè dòng)

```mermaid
classDiagram
    direction TB

    %% 1. Controller Layer
    class ShipModelController {
        -ShipModelService modelService
        -ShipModelAssetService assetService
        +createModel(shipId, request) ApiResponse~ShipModelResponse~
        +uploadAsset(modelId, file) ApiResponse~ShipModelAssetUploadResponse~
        +listObjects(modelId) ApiResponse~List~ShipModelObjectResponse~~
        +registerManifest(modelKey, request) ApiResponse
    }

    class PublicShipModelController {
        -ShipModelService modelService
        +getPublicModel(shipId) ApiResponse~PublicShipModelResponse~
        +getAssetBlob(blobKey) ResponseEntity~byte[]~
    }

    %% 2. Service Layer
    class ShipModelService {
        -ShipModelRepository modelRepo
        -ShipModelObjectRepository objectRepo
        -ShipRepository shipRepo
        +createShipModel(shipId, dto) ShipModelResponse
        +getActiveModelByShip(shipId) Optional~ShipModel~
        +linkObjectToCabin(objectId, cabinId) void
        +deprecateModel(modelId) void
    }

    class ShipModelAssetService {
        -ShipModelAssetStore assetStore
        -ModelImportJobRepository jobRepo
        +uploadBlob(modelId, bytes, fileName) ShipModelAssetBlob
        +streamBlob(blobKey) InputStream
        +triggerImportJob(modelId) ModelImportJob
    }

    class ShipModelImportService {
        -ModelImportJobRepository jobRepo
        -ShipModelObjectRepository objectRepo
        +processImportQueue() void
        +parseGltfHierarchy(jobId, inputStream) void
        +recordIssue(jobId, severity, message) void
    }

    %% 3. Repository Layer
    class ShipModelRepository {
        +findById(id) Optional~ShipModel~
        +findByShipIdAndStatus(shipId, status) List~ShipModel~
        +findByModelKey(modelKey) Optional~ShipModel~
        +save(model) ShipModel
    }

    class ShipModelObjectRepository {
        +findByShipModelId(modelId) List~ShipModelObject~
        +findByNodeKey(nodeKey) Optional~ShipModelObject~
        +saveAll(objects) List~ShipModelObject~
        +save(object) ShipModelObject
    }

    class ModelImportJobRepository {
        +findById(id) Optional~ModelImportJob~
        +findByStatus(status) List~ModelImportJob~
        +save(job) ModelImportJob
    }

    %% 4. Entity Layer
    class ShipModel {
        +UUID shipModelId
        +String version
        +String modelKey
        +String assetRevision
        +String assetVersion
        +String shipManufacturerName
        +ShipModelStatus status
        +Instant importedAt
    }

    class ShipModelObject {
        +UUID objectId
        +String nodeKey
        +String objectName
        +String objectType
        +String transformMatrix
        +UUID deckId
        +UUID cabinId
        +UUID areaId
    }

    class ShipModelAssetBlob {
        +UUID blobId
        +String blobKey
        +String contentType
        +Long byteSize
        +String storagePath
        +String checksumSha256
    }

    class ModelImportJob {
        +UUID jobId
        +String jobReference
        +ImportJobStatus status
        +Integer totalNodes
        +Integer processedNodes
        +Instant startedAt
        +Instant finishedAt
    }

    class ModelImportIssue {
        +UUID issueId
        +String severity
        +String nodeIdentifier
        +String errorMessage
    }

    %% Inter-Layer and Domain Relationships
    ShipModelController --> ShipModelService : delegatesTo
    ShipModelController --> ShipModelAssetService : delegatesTo
    PublicShipModelController --> ShipModelService : delegatesTo
    ShipModelAssetService --> ShipModelImportService : triggersJob

    ShipModelService --> ShipModelRepository : accesses
    ShipModelService --> ShipModelObjectRepository : accesses
    ShipModelAssetService --> ModelImportJobRepository : accesses
    ShipModelImportService --> ModelImportJobRepository : accesses
    ShipModelImportService --> ShipModelObjectRepository : accesses

    ShipModelRepository ..> ShipModel : manages
    ShipModelObjectRepository ..> ShipModelObject : manages
    ModelImportJobRepository ..> ModelImportJob : manages

    ShipModel "1" *-- "0..*" ShipModelObject : containsObjects
    ShipModel "1" *-- "0..*" ShipModelAssetBlob : bundlesAssets
    ShipModel "1" o-- "0..*" ModelImportJob : importedThrough
    ModelImportJob "1" *-- "0..*" ModelImportIssue : logsIssues
```
