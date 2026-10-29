# PUT /ships/{shipId}/models/{modelId}

```mermaid
sequenceDiagram
    title PUT /ships/{shipId}/models/{modelId} — ShipModelController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /ships/{shipId}/models/{modelId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.update()

    Note over Controller,Service: Source branch: if !Set.of("glb", "gltf").contains(format)
    Note over Controller,Service: Source branch: if !allowed || uriValue.isBlank()) throw new IllegalArgumentException(
    Note over Controller,Service: Source branch: if !model.getVersion().equals(version) && models.existsByShipShipIdAndVersion(shipId, version)
    Note over Controller,Service: Source branch: if checksum != null && !checksum.isBlank() && !SHA256.matcher(checksum.trim()).matches()
    Note over Controller,Service: Source branch: if m.getModelKey() != null
    Note over Controller,Service: Source branch: if m.getStatus() == ShipModelStatus.READY || m.getStatus() == ShipModelStatus.ARCHIVED
    Note over Controller,Service: Source branch: if scale == null || !Double.isFinite(scale) || scale < .1 || scale > 10
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipModelRepository.existsByShipShipIdAndVersion()
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
