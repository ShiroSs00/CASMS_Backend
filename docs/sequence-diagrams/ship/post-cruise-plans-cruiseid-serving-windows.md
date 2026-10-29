# POST /cruise-plans/{cruiseId}/serving-windows

```mermaid
sequenceDiagram
    title POST /cruise-plans/{cruiseId}/serving-windows — CruiseScopedProgramsController.createWindow
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-plans/{cruiseId}/serving-windows + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createWindow(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: schedules.create()

    Note over Controller,Service: Source branch: if input.cruiseStopId()!=null)stopScope(cruiseId,input.cruiseStopId()
    Note over Controller,Service: Source branch: if input.startsAt().isBefore(departure)||input.endsAt().isAfter(end)
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
