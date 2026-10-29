# POST /auth/login

```mermaid
sequenceDiagram
    title POST /auth/login — AuthController.login
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as AuthController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /auth/login + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke login(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: authService.login()

    Note over Controller,Service: Source branch: for Permission p : rp.getPermissions()
    Note over Controller,Service: Source branch: for Role role : user.getRoles()
    Note over Controller,Service: Source branch: if rp.getPermissions() != null
    Note over Controller,Service: Source branch: if user == null
    Note over Controller,Service: Source branch: if user.getRoles() != null && rolePermissionRepository != null
    Note over Controller,Service: Source branch: if user.getStatus() != UserStatus.ACTIVE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: UserRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
