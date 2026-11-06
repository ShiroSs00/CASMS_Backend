# GET /cruise/{cruiseId}/cruise-package-offers/cabin-options

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/cruise-package-offers/cabin-options — DepartureCruisePackageController.cabinOptions
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DepartureCruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/cruise-package-offers/cabin-options + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke cabinOptions(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.cabinOptions()

    Note over Controller,Service: Source branch: if auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if ship==null) fail("FARE_SHIP_REQUIRED","Chuyến khởi hành chưa được phân công tàu."
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
