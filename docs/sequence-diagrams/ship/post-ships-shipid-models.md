# POST /ships/{shipId}/models

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/models — ShipModelController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/models + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: assetService.register()

    Note over Controller,Service: Source branch: if !Objects.equals(model.getAssetRevision(), manifest.path("revision").asText())
    Note over Controller,Service: Source branch: if !assets.bundleExists(request.modelKey(), request.assetVersion())
    Note over Controller,Service: Source branch: if !commission.isEmpty()
    Note over Controller,Service: Source branch: if !name.isEmpty()) ship.setShipName(name
    Note over Controller,Service: Source branch: if beam != null) ship.setBeamMeters(beam
    Note over Controller,Service: Source branch: if deckCount != null) ship.setTotalDecks(deckCount
    Note over Controller,Service: Source branch: if length != null) ship.setLengthMeters(length
    Note over Controller,Service: Source branch: if manifest.hasNonNull("name") && !manifest.path("name").isTextual()
    Note over Controller,Service: Source branch: if manifestModelId.isEmpty()
    Note over Controller,Service: Source branch: if passengerCapacity != null) ship.setTotalCapacity(passengerCapacity
    Note over Controller,Service: Source branch: if requireCompleteMetadata
    Note over Controller,Service: Source branch: if ship.getCommissionDate(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipModelRepository.existsByShipShipIdAndModelKeyAndAssetVersion()
            Service->>Repository: ShipModelRepository.save()
            Service->>Repository: ShipRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```
