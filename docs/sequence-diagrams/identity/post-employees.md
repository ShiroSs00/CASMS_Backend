# POST /employees

```mermaid
sequenceDiagram
    title POST /employees — EmployeeAccountController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as EmployeeAccountController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /employees + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: if !assignableRoles(actor).contains(input.role())
    Note over Controller,Service: Source branch: if !email.equals(before.email())
    Note over Controller,Service: Source branch: if "PENDING_ACTIVATION".equals(before.status())
    Note over Controller,Service: Source branch: if users.findByNormalizedEmail(email).isPresent() || users.existsByUsername(email)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: UserRepository.existsByUsername()
            Service->>Repository: UserRepository.findByNormalizedEmail()
            Service->>Repository: UserRepository.saveAndFlush()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
