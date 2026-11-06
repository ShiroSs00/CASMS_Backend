# GET /destinations/{destinationId}/history

```mermaid
sequenceDiagram
    title GET /destinations/{destinationId}/history — DestinationController.history
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DestinationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /destinations/{destinationId}/history + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke history(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.detail()
        Controller->>Repository: AuditRepository.findByEntityIdOrderByOccurredAtDesc()
    Note over Controller,Service: Source branch: if ports != null
    Note over Controller,Service: Source branch: if ports == null || ports.isEmpty()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
        Controller->>Repository: AuditRepository.findByEntityIdOrderByOccurredAtDesc()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
