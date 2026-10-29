# PUT /ships/{shipId}/models/{modelId}/cabin-mappings

```mermaid
sequenceDiagram
    title PUT /ships/{shipId}/models/{modelId}/cabin-mappings — ShipModelController.replaceCabinMappings
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /ships/{shipId}/models/{modelId}/cabin-mappings + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke replaceCabinMappings(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: assetService.replaceCabinMappings()

    Note over Controller,Service: Source branch: for ShipModelCabinMappingRequest request : requests
    Note over Controller,Service: Source branch: for ShipModelObject deckMapping : deckMappingObjects(modelId)
    Note over Controller,Service: Source branch: for var entry : resolved.entrySet()
    Note over Controller,Service: Source branch: if !Objects.equals(model.getAssetRevision(), manifest.path("revision").asText())
    Note over Controller,Service: Source branch: if !cabin.getShip().getShipId().equals(shipId) || !Objects.equals(cabin.getDeck().getDeckNumber(), expectedDeck
    Note over Controller,Service: Source branch: if !modelCabins.contains(request.modelId()) || !seenModelCabins.add(request.modelId()
    Note over Controller,Service: Source branch: if model.getStatus() == ShipModelStatus.READY || model.getStatus() == ShipModelStatus.ARCHIVED
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.findById()
            Service->>Repository: ShipModelObjectRepository.deleteAll()
            Service->>Repository: ShipModelObjectRepository.flush()
            Service->>Repository: ShipModelObjectRepository.saveAll()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```
