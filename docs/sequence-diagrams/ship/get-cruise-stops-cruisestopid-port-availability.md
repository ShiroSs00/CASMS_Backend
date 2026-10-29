# GET /cruise-stops/{cruiseStopId}/port-availability

```mermaid
sequenceDiagram
    title GET /cruise-stops/{cruiseStopId}/port-availability — CruiseStopController.checkPortAvailability
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStopController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise-stops/{cruiseStopId}/port-availability + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke checkPortAvailability(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body

        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
