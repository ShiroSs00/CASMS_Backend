# GET /cruise/{cruiseId}/ship-compatibility

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/ship-compatibility — CruiseController.checkShipCompatibility
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/ship-compatibility + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke checkShipCompatibility(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.checkShipCompatibility()

    Note over Controller,Service: Source branch: if !active
    Note over Controller,Service: Source branch: if !capacitySufficient
    Note over Controller,Service: Source branch: if !configured
    Note over Controller,Service: Source branch: if !scheduleAvailable
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.countActivePassengersByCruiseId()
            Service->>Repository: CabinRepository.countByShipShipId()
            Service->>Repository: CruiseRepository.existsOverlappingCruisesExcluding()
            Service->>Repository: DeckRepository.countByShipShipId()
            Service->>Repository: ShipRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
