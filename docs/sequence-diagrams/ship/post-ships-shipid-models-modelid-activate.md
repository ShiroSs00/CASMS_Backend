# POST /ships/{shipId}/models/{modelId}/activate

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/models/{modelId}/activate — ShipModelController.activate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/models/{modelId}/activate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke activate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: assetService.activate()

    Note over Controller,Service: Source branch: for ShipModelObject cabinMapping : cabinMappingObjects(model.getShipModelId())
    Note over Controller,Service: Source branch: for ShipModelObject deckMapping : deckMappingObjects(model.getShipModelId())
    Note over Controller,Service: Source branch: if !Objects.equals(model.getAssetRevision(), manifest.path("revision").asText())
    Note over Controller,Service: Source branch: if !actual.getShip().getShipId().equals(shipId) || actual.getDeckNumber() != modelDeck.path("number").asInt()
    Note over Controller,Service: Source branch: if !cabin.getShip().getShipId().equals(shipId) || cabin.getDeck().getDeckNumber(
    Note over Controller,Service: Source branch: if !other.getShipModelId().equals(modelId)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipModelRepository.findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc()
            Service->>Repository: ShipModelRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```
