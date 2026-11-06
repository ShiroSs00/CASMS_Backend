# DELETE /ships/{shipId}/models/{modelId}/objects/{objectId}

```mermaid
sequenceDiagram
    title DELETE /ships/{shipId}/models/{modelId}/objects/{objectId} — ShipModelController.deleteObject
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /ships/{shipId}/models/{modelId}/objects/{objectId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke deleteObject(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.deleteObject()

    Note over Controller,Service: Source branch: if m.getModelKey() != null
    Note over Controller,Service: Source branch: if m.getStatus() == ShipModelStatus.READY || m.getStatus() == ShipModelStatus.ARCHIVED
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipModelObjectRepository.delete()
            Service->>Repository: ShipModelObjectRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
