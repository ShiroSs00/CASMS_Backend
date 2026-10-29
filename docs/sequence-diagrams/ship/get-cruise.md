# GET /cruise

```mermaid
sequenceDiagram
    title GET /cruise — CruiseController.list
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke list(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.list()
        Controller->>Service: service.listOpenForSale()

    Note over Controller,Service: Source branch: for CabinType type : CabinType.values()
    Note over Controller,Service: Source branch: for CruiseTourPackage ctp : ctpList
    Note over Controller,Service: Source branch: if !hasCruiseRead
    Note over Controller,Service: Source branch: if !hasInventory || totalAvailableCabinCapacity == 0
    Note over Controller,Service: Source branch: if !isDepartureFuture
    Note over Controller,Service: Source branch: if !isItineraryOpen || policyOpt.isEmpty() || cruise.getOpenedForSaleAt() == null || !mainflow.isReady(cruiseId
    Note over Controller,Service: Source branch: if Boolean.FALSE.equals(unassigned)
    Note over Controller,Service: Source branch: if Boolean.TRUE.equals(unassigned)
    Note over Controller,Service: Source branch: if cruise.getStatus() == CruiseStatus.CANCELLED
    Note over Controller,Service: Source branch: if cruise.getStatus() == CruiseStatus.DRAFT
    Note over Controller,Service: Source branch: if cruiseType != null
    Note over Controller,Service: Source branch: if depositPercentage != null && depositPercentage.compareTo(BigDecimal.ONE) > 0
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findAll()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
