# GET /ports/{portId}/call-windows

```mermaid
sequenceDiagram
    title GET /ports/{portId}/call-windows — PortWindowController.read
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PortWindowController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ports/{portId}/call-windows + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke read(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.read()


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: PortCallWindowExceptionRepository.findByPortPortIdOrderByValidFromAsc()
            Service->>Repository: PortCallWindowRepository.findByPortPortIdOrderByValidFromAsc()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
