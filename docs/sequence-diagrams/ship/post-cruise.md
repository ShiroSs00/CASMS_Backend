# POST /cruise

```mermaid
sequenceDiagram
    title POST /cruise — CruiseController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: idempotencyService.execute()
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: if !Set.of("FIXED", "CUSTOM").contains(mode)
    Note over Controller,Service: Source branch: if creatingAuth!=null&&creatingAuth.getPrincipal(
    Note over Controller,Service: Source branch: if cruise.getCompletionActor() != null
    Note over Controller,Service: Source branch: if cruise.getDepartureActor() != null
    Note over Controller,Service: Source branch: if cruise.getDeparturePort() != null
    Note over Controller,Service: Source branch: if cruise.getOpenedForSaleBy() != null
    Note over Controller,Service: Source branch: if cruise.getReturnPort() != null
    Note over Controller,Service: Source branch: if cruise.getShip() != null
    Note over Controller,Service: Source branch: if cruise.getSourceTrip() != null
    Note over Controller,Service: Source branch: if cruise.getSourceVersion() != null
    Note over Controller,Service: Source branch: if cruise.getStoppedBy() != null
    Note over Controller,Service: Source branch: if cruiseRepository.existsOverlappingCruises(ship.getShipId(), request.getDepartureDatetime(), request.getReturnDatetime())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.existsOverlappingCruises()
            Service->>Repository: CruiseRepository.save()
            Service->>Repository: PortRepository.findById()
            Service->>Repository: ShipRepository.findById()
            Service->>Repository: TripScheduleVersionRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
