# POST /cabins/{cabinId}/block

```mermaid
sequenceDiagram
    title POST /cabins/{cabinId}/block — CabinController.blockCabin
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CabinController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cabins/{cabinId}/block + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke blockCabin(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.blockCabin()

    Note over Controller,Service: Source branch: if cabin.getStatus() != CabinStatus.AVAILABLE
    Note over Controller,Service: Source branch: if reasonStatus != CabinStatus.MAINTENANCE && reasonStatus != CabinStatus.OUT_OF_SERVICE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
