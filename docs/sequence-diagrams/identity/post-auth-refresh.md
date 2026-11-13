# POST /auth/refresh

```mermaid
sequenceDiagram
    title POST /auth/refresh — AuthController.refreshToken
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as AuthController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /auth/refresh + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke refreshToken(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: authService.refreshToken()

    Note over Controller,Service: Source branch: for Permission p : rp.getPermissions()
    Note over Controller,Service: Source branch: for Role role : user.getRoles()
    Note over Controller,Service: Source branch: if !jwtService.isValid(accessToken)
    Note over Controller,Service: Source branch: if rp.getPermissions() != null
    Note over Controller,Service: Source branch: if user == null || user.hasRole(Role.POS)
    Note over Controller,Service: Source branch: if user.getRoles() != null && rolePermissionRepository != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
