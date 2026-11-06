# PATCH /ports/{portId}/call-windows/exceptions/{id}/lifecycle

```mermaid
sequenceDiagram
    title PATCH /ports/{portId}/call-windows/exceptions/{id}/lifecycle — PortWindowController.lifecycleClosure
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PortWindowController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /ports/{portId}/call-windows/exceptions/{id}/lifecycle + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke lifecycleClosure(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.lifecycleClosure()

    Note over Controller,Service: Source branch: if !c.getPort().getPortId().equals(portId)
    Note over Controller,Service: Source branch: if "ACTIVE".equals(request.lifecycleStatus()
    Note over Controller,Service: Source branch: if expected == null || !expected.equals(actual)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: PortCallWindowExceptionRepository.saveAndFlush()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
