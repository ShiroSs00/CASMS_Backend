# PUT /suppliers/{id}

```mermaid
sequenceDiagram
    title PUT /suppliers/{id} — SupplierController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as SupplierController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /suppliers/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: supplierService.update()

    Note over Controller,Service: Source branch: if request == null
    Note over Controller,Service: Source branch: if request.contactName(
    Note over Controller,Service: Source branch: if request.supplierName(
    Note over Controller,Service: Source branch: if supplierId == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: SupplierRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
