# POST /checkin/guidance-sessions

```mermaid
sequenceDiagram
    title POST /checkin/guidance-sessions — GuidanceSessionController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as GuidanceSessionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /checkin/guidance-sessions + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body

        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws com.casms.common.exception.BusinessException; HTTP mapping requires exception-handler review
    end
```
