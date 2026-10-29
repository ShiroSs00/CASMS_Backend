# GET /ships/{shipId}/regulations

```mermaid
sequenceDiagram
    title GET /ships/{shipId}/regulations — ShipRegulationController.list
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipRegulationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ships/{shipId}/regulations + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke list(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: shipRegulationService.list()

    Note over Controller,Service: Source branch: if !shipRepository.existsById(shipId)
    Note over Controller,Service: Source branch: if shipId == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipRegulationRepository.findByShip_ShipId()
            Service->>Repository: ShipRepository.existsById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws EntityNotFoundException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
