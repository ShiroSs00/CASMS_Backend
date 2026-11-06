# POST /ships

Ship profile: Tạo Ship thủ công hoặc từ model; phản hồi ShipResponse có bốn trường mới. Xem [luồng đã rà soát](../SHIP-BASIC-PROFILE.md).

```mermaid
sequenceDiagram
    title POST /ships — ShipController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: if !request.isModelReferenceComplete()
    Note over Controller,Service: Source branch: if commissionDate != null && commissionDate.isAfter(LocalDate.now())
    Note over Controller,Service: Source branch: if lengthMeters != null && (!Double.isFinite(lengthMeters) || lengthMeters <= 0
    Note over Controller,Service: Source branch: if request.getModelKey() != null || request.getAssetVersion() != null
    Note over Controller,Service: Source branch: if request.getShipName() == null || request.getShipName().isBlank()
    Note over Controller,Service: Source branch: if totalCapacity == null || totalCapacity < 1
    Note over Controller,Service: Source branch: if totalDecks == null || totalDecks < 1
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```


The changed request, authorization, transaction and response branches are in [Ship model create and update by code](../SHIP-MODEL-BY-CODE.md). Existing static trace details may require separate review.
