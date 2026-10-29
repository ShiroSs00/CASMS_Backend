# POST /suppliers/{id}/validate

```mermaid
sequenceDiagram
    title POST /suppliers/{id}/validate — SupplierController.validate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as SupplierController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /suppliers/{id}/validate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke validate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: supplierService.validateSupplier()

    Note over Controller,Service: Source branch: if supplier.getContactName(
    Note over Controller,Service: Source branch: if supplier.getSupplierName(
    Note over Controller,Service: Source branch: if supplierId == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
