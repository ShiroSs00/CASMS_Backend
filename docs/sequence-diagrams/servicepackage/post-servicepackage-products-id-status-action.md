# POST /servicepackage/products/{id}/status/{action}

```mermaid
sequenceDiagram
    title POST /servicepackage/products/{id}/status/{action} — ServicepackageController.productStatus
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/products/{id}/status/{action} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke productStatus(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.productStatus()

    Note over Controller,Service: Source branch: if p.getStatus()!=ProductStatus.ACTIVE)invalid();p.setStatus(ProductStatus.DISCONTINUED
    Note over Controller,Service: Source branch: if p.getStatus()!=ProductStatus.INACTIVE)invalid();p.setStatus(ProductStatus.ACTIVE
    Note over Controller,Service: Source branch: switch action
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
