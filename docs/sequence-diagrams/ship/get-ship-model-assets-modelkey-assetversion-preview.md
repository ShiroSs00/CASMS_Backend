# GET /ship-model-assets/{modelKey}/{assetVersion}/preview

```mermaid
sequenceDiagram
    title GET /ship-model-assets/{modelKey}/{assetVersion}/preview — ShipModelController.previewStoredAsset
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ship-model-assets/{modelKey}/{assetVersion}/preview + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke previewStoredAsset(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: assetService.previewStoredBundle()

    Note over Controller,Service: Source branch: if !Objects.equals(model.getAssetRevision(), manifest.path("revision").asText())
    Note over Controller,Service: Source branch: if !assets.bundleExists(modelKey, assetVersion)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```
