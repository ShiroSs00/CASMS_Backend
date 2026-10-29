# POST /ships/{shipId}/models/{modelId}/ready

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/models/{modelId}/ready — ShipModelController.ready
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/models/{modelId}/ready + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke ready(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.setReady()

    Note over Controller,Service: Source branch: if !Set.of("glb", "gltf").contains(format)
    Note over Controller,Service: Source branch: if !allowed || uriValue.isBlank()) throw new IllegalArgumentException(
    Note over Controller,Service: Source branch: if checksum != null && !checksum.isBlank() && !SHA256.matcher(checksum.trim()).matches()
    Note over Controller,Service: Source branch: if model.getModelKey() != null
    Note over Controller,Service: Source branch: if model.getStatus() != ShipModelStatus.DRAFT && model.getStatus() != ShipModelStatus.PROCESSING
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipModelRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
