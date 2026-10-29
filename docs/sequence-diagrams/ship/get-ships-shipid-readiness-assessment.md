# GET /ships/{shipId}/readiness-assessment

```mermaid
sequenceDiagram
    title GET /ships/{shipId}/readiness-assessment — ShipController.assessOperationalReadiness
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ships/{shipId}/readiness-assessment + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assessOperationalReadiness(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.assessOperationalReadiness()

    Note over Controller,Service: Source branch: if cabinCount == 0
    Note over Controller,Service: Source branch: if deckCount == 0
    Note over Controller,Service: Source branch: if ship.getStatus() != ShipStatus.ACTIVE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.countByShipShipId()
            Service->>Repository: DeckRepository.countByShipShipId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
