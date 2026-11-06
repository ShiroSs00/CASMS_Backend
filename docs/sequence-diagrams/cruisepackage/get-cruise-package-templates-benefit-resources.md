# GET /cruise-package-templates/benefit-resources

```mermaid
sequenceDiagram
    title GET /cruise-package-templates/benefit-resources — CruisePackageController.resources
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise-package-templates/benefit-resources + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke resources(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.resources()

    Note over Controller,Service: Source branch: if auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: switch type
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
