# GET /cruise/{cruiseId}/audit-events

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/audit-events — CruiseController.getAuditEvents
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/audit-events + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getAuditEvents(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: cruiseAuditService.listAuditEvents()

    Note over Controller,Service: Source branch: if !cruiseRepository.existsById(cruiseId)
    Note over Controller,Service: Source branch: if eventType != null && !eventType.isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseAuditEventRepository.findByCruise_CruiseIdAndEventTypeOrderByTimestampDesc()
            Service->>Repository: CruiseAuditEventRepository.findByCruise_CruiseIdOrderByTimestampDesc()
            Service->>Repository: CruiseRepository.existsById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
