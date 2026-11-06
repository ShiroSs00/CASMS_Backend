# GET /cruise-package-templates/{id}/versions

```mermaid
sequenceDiagram
    title GET /cruise-package-templates/{id}/versions — CruisePackageController.versions
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise-package-templates/{id}/versions + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke versions(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.versions()

    Note over Controller,Service: Source branch: if auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if value==null) throw new BusinessException(HttpStatus.NOT_FOUND,"FARE_RESOURCE_NOT_FOUND","Không tìm thấy dữ liệu gói vé."
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePackageRepository.versions()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
