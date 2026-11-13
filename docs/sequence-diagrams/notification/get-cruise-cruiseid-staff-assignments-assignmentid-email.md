# GET /cruise/{cruiseId}/staff/assignments/{assignmentId}/email

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/staff/assignments/{assignmentId}/email — StaffAssignmentEmailController.status
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as StaffAssignmentEmailController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/staff/assignments/{assignmentId}/email + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke status(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.status()

    Note over Controller,Service: Source branch: if !queuedEmail.equals(currentEmail)
    Note over Controller,Service: Source branch: if !rs.getBoolean("active")
    Note over Controller,Service: Source branch: if currentEmail == null || queuedEmail == null
    Note over Controller,Service: Source branch: if rows.isEmpty()) throw new ResourceNotFoundException("Staff assignment email", assignmentId
    Note over Controller,Service: Source branch: if rs.getBoolean("sent")
    Note over Controller,Service: Source branch: if rs.getInt("attempts") >= MAX_ATTEMPTS
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
