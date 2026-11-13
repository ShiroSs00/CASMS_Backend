# POST /auth/employee-account/activate

```mermaid
sequenceDiagram
    title POST /auth/employee-account/activate — EmployeeActivationController.activate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as EmployeeActivationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /auth/employee-account/activate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke activate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.activate()

    Note over Controller,Service: Source branch: if !email.equals(before.email())
    Note over Controller,Service: Source branch: if "PENDING_ACTIVATION".equals(before.status())
    Note over Controller,Service: Source branch: if password.getBytes(StandardCharsets.UTF_8).length > 72
    Note over Controller,Service: Source branch: if pending.isEmpty()) throw EmployeeActivationToken.invalid(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
