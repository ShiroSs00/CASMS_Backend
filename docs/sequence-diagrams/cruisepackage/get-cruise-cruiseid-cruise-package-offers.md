# GET /cruise/{cruiseId}/cruise-package-offers

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/cruise-package-offers — DepartureCruisePackageController.offers
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DepartureCruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/cruise-package-offers + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke offers(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.offers()

    Note over Controller,Service: Source branch: if auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"CRUISE_NOT_FOUND","Không tìm thấy chuyến khởi hành."
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePackageRepository.offers()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
