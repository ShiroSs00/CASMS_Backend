# DELETE /cruise/{cruiseId}/staff/{userId}/service

```mermaid
sequenceDiagram
    title DELETE /cruise/{cruiseId}/staff/{userId}/service — CruiseStaffWorkController.removeService
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStaffWorkController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /cruise/{cruiseId}/staff/{userId}/service + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke removeService(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.removeService()

    Note over Controller,Service: Source branch: if List.of("COMPLETED", "CANCELLED").contains(cruises.getFirst())
    Note over Controller,Service: Source branch: if assigning
    Note over Controller,Service: Source branch: if cruises.isEmpty()
    Note over Controller,Service: Source branch: if staff.isEmpty()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
