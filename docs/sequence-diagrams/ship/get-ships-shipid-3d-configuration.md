# GET /ships/{shipId}/3d-configuration

```mermaid
sequenceDiagram
    title GET /ships/{shipId}/3d-configuration — ShipModelController.configuration
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ships/{shipId}/3d-configuration + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke configuration(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.configuration()

    Note over Controller,Service: Source branch: for ShipModelObject object : modelObjects
    Note over Controller,Service: Source branch: if mapped != null
    Note over Controller,Service: Source branch: if model.getUpdatedAt().isAfter(configurationUpdatedAt)
    Note over Controller,Service: Source branch: if object != null
    Note over Controller,Service: Source branch: if object.getCabin() != null) cabinNodes.put(object.getCabin().getCabinId(), object
    Note over Controller,Service: Source branch: if object.getDeck() != null && object.getObjectType() == ShipModelObjectType.DECK
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.findByShipShipIdOrderByDeckDeckNumberAscCabinNumberAsc()
            Service->>Repository: DeckRepository.findByShipShipIdOrderByDeckNumber()
            Service->>Repository: LocationDeviceRepository.findByShipShipId()
            Service->>Repository: ShipModelObjectRepository.findByShipModelShipModelId()
            Service->>Repository: ShipRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
