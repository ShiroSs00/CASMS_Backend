# POST /ships/{shipId}/models/{modelId}/objects

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/models/{modelId}/objects — ShipModelController.createObject
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/models/{modelId}/objects + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createObject(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.createObject()

    Note over Controller,Service: Source branch: if !cabin.getShip().getShipId().equals(model.getShip().getShipId()) || (deckId != null && !cabin.getDeck().getDeckId().equals(deckId)
    Note over Controller,Service: Source branch: if !deck.getShip().getShipId().equals(model.getShip().getShipId())
    Note over Controller,Service: Source branch: if cabinId != null
    Note over Controller,Service: Source branch: if deckId != null
    Note over Controller,Service: Source branch: if m.getModelKey() != null
    Note over Controller,Service: Source branch: if m.getStatus() == ShipModelStatus.READY || m.getStatus() == ShipModelStatus.ARCHIVED
    Note over Controller,Service: Source branch: if objects.existsByShipModelShipModelIdAndNodeKey(modelId, nodeKey)
    Note over Controller,Service: Source branch: if type == ShipModelObjectType.CABIN && cabinId == null
    Note over Controller,Service: Source branch: if type == ShipModelObjectType.DECK && deckId == null
    Note over Controller,Service: Source branch: if v != null && (!Double.isFinite(v) || Math.abs(v) > 1000)
    Note over Controller,Service: Source branch: if v == null || !Double.isFinite(v) || v <= 0 || v > 1000
    Note over Controller,Service: Source branch: if value == null || value.isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.findById()
            Service->>Repository: DeckRepository.findById()
            Service->>Repository: ShipModelObjectRepository.existsByShipModelShipModelIdAndNodeKey()
            Service->>Repository: ShipModelObjectRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
