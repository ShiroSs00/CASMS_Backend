# POST /employees/{employeeId}/inactivate

```mermaid
sequenceDiagram
    title POST /employees/{employeeId}/inactivate — EmployeeAccountController.inactivate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as EmployeeAccountController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /employees/{employeeId}/inactivate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke inactivate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.inactivate()

    Note over Controller,Service: Source branch: for var assignment : assignments
    Note over Controller,Service: Source branch: if !email.equals(before.email())
    Note over Controller,Service: Source branch: if "INACTIVE".equals(before.status())
    Note over Controller,Service: Source branch: if "PENDING_ACTIVATION".equals(before.status())
    Note over Controller,Service: Source branch: if actor == null || !actor.isEnabled() || !actor.isAccountNonLocked(
    Note over Controller,Service: Source branch: if actor.getUserId().equals(before.userId())
    Note over Controller,Service: Source branch: if assignments.stream().anyMatch(assignment -> !lockedCruises.contains(assignment.getKey()))
    Note over Controller,Service: Source branch: if assignments.stream().anyMatch(assignment -> "IN_PROGRESS".equals(assignment.getValue()))
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
