# GET /ships/{shipId}/public-model/{modelId}/assets/{*assetPath}

```mermaid
sequenceDiagram
    title GET /ships/{shipId}/public-model/{modelId}/assets/{*assetPath} — PublicShipModelController.asset
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PublicShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ships/{shipId}/public-model/{modelId}/assets/{*assetPath} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke asset(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: assets.publicAsset()
        Controller->>Service: assets.writePublicAsset()

    Note over Controller,Service: Source branch: if !name.endsWith(".glb") || !allowed.contains(name)
    Note over Controller,Service: Source branch: if value.isTextual()) allowed.add(value.asText()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```
