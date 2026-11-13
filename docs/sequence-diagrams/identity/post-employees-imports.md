# POST /employees/imports

```mermaid
sequenceDiagram
    title POST /employees/imports — EmployeeImportController.upload
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as EmployeeImportController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /employees/imports + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke upload(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.upload()

    Note over Controller,Service: Source branch: for var row : parsed
    Note over Controller,Service: Source branch: if !codes.add(code)
    Note over Controller,Service: Source branch: if !emails.add(email)
    Note over Controller,Service: Source branch: if !roles.contains(input.role())
    Note over Controller,Service: Source branch: if !violations.isEmpty()
    Note over Controller,Service: Source branch: if error == null) error = validate(input, roles, codes, emails
    Note over Controller,Service: Source branch: if file == null || file.isEmpty() || file.getSize() > EmployeeWorkbook.MAX_UPLOAD_BYTES
    Note over Controller,Service: Source branch: if users.findByNormalizedEmail(email).isPresent() || users.existsByUsername(email)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
