# POST /ships/{shipId}/3d-configuration/validate

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/3d-configuration/validate — ShipModelController.validate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/3d-configuration/validate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke validate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.validateConfiguration()

    Note over Controller,Service: Source branch: for ShipModelObject object : modelObjects
    Note over Controller,Service: Source branch: for var anchor : deck.bleAnchors()
    Note over Controller,Service: Source branch: for var deck : config.decks()
    Note over Controller,Service: Source branch: for var room : deck.rooms()
    Note over Controller,Service: Source branch: if !deck.serviceLevel(
    Note over Controller,Service: Source branch: if config.decks().isEmpty()
    Note over Controller,Service: Source branch: if coordinate == null || !Double.isFinite(coordinate) || Math.abs(coordinate) > 1000
    Note over Controller,Service: Source branch: if model.getUpdatedAt().isAfter(configurationUpdatedAt)
    Note over Controller,Service: Source branch: if object.getCabin() != null) cabinNodes.put(object.getCabin().getCabinId(), object
    Note over Controller,Service: Source branch: if object.getDeck() != null && object.getObjectType() == ShipModelObjectType.DECK
    Note over Controller,Service: Source branch: if required
    Note over Controller,Service: Source branch: if room.modelNodeKey() == null || room.modelNodeKey().isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
