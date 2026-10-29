# GET /auth/me

```mermaid
sequenceDiagram
    title GET /auth/me — AuthController.me
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as AuthController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /auth/me + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke me(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: authService.getCurrentUser()

    Note over Controller,Service: Source branch: for Permission p : rp.getPermissions()
    Note over Controller,Service: Source branch: for Role role : user.getRoles()
    Note over Controller,Service: Source branch: if principal == null || principal.getUser() == null
    Note over Controller,Service: Source branch: if rp.getPermissions() != null
    Note over Controller,Service: Source branch: if user == null
    Note over Controller,Service: Source branch: if user.getRoles() != null && rolePermissionRepository != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: PassengerRepository.findByUser_UserId()
            Service->>Repository: UserRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
