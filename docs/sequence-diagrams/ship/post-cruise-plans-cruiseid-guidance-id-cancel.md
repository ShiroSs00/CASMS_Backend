# POST /cruise-plans/{cruiseId}/guidance/{id}/cancel

```mermaid
sequenceDiagram
    title POST /cruise-plans/{cruiseId}/guidance/{id}/cancel — CruiseGuidanceController.cancel
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseGuidanceController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-plans/{cruiseId}/guidance/{id}/cancel + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke cancel(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: plans.recordOperationalEvent()

    Note over Controller,Service: Source branch: for String table : List.of("CruisePreparationSection", "CruiseStaff", "GuidanceSession")
    Note over Controller,Service: Source branch: if !"[]".equals(visits)) out.put("destinationVisits", json(visits)
    Note over Controller,Service: Source branch: if actor == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
