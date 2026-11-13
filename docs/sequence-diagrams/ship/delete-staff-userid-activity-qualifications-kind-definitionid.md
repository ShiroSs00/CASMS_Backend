# DELETE /staff/{userId}/activity-qualifications/{kind}/{definitionId}

```mermaid
sequenceDiagram
    title DELETE /staff/{userId}/activity-qualifications/{kind}/{definitionId} — CruiseStaffWorkController.removeQualification
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStaffWorkController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /staff/{userId}/activity-qualifications/{kind}/{definitionId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke removeQualification(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.removeQualification()

    Note over Controller,Service: Source branch: if users.isEmpty()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
