# GET /ships/{shipId}/areas/{areaId}/staff

```mermaid
sequenceDiagram
    title GET /ships/{shipId}/areas/{areaId}/staff — PosAssignmentController.staff
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PosAssignmentController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ships/{shipId}/areas/{areaId}/staff + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke staff(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.staff()


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```

Staffing integration details are recorded in the [staffing workflow](../STAFFING-FLOWS.md).

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
