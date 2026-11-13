# PUT /cruise/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId}

```mermaid
sequenceDiagram
    title PUT /cruise/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId} — CruiseStaffWorkController.assignActivity
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStaffWorkController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /cruise/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assignActivity(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.assignActivity()

    Note over Controller,Service: Source branch: if List.of("COMPLETED", "CANCELLED").contains(cruises.getFirst())
    Note over Controller,Service: Source branch: if assigning
    Note over Controller,Service: Source branch: if cruises.isEmpty()
    Note over Controller,Service: Source branch: if definitions.isEmpty()
    Note over Controller,Service: Source branch: if hasDuty(staffId, kind.column, occurrenceId)
    Note over Controller,Service: Source branch: if staff.isEmpty()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
