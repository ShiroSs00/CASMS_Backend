# DELETE /ships/{shipId}/regulations/{regulationId}

```mermaid
sequenceDiagram
    title DELETE /ships/{shipId}/regulations/{regulationId} — ShipRegulationController.remove
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipRegulationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /ships/{shipId}/regulations/{regulationId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke remove(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: shipRegulationService.remove()

    Note over Controller,Service: Source branch: if shipId == null || regulationId == null
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShipRegulationRepository.delete()
            Service->>Repository: ShipRegulationRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
