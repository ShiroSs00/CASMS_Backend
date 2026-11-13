# GET /staff/{userId}/activity-qualifications

```mermaid
sequenceDiagram
    title GET /staff/{userId}/activity-qualifications — CruiseStaffWorkController.qualifications
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStaffWorkController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /staff/{userId}/activity-qualifications + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke qualifications(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.qualifications()


        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
