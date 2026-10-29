# POST /cabins/{cabinId}/validate

```mermaid
sequenceDiagram
    title POST /cabins/{cabinId}/validate — CabinController.validateCabin
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CabinController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cabins/{cabinId}/validate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke validateCabin(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.validateCabin()

    Note over Controller,Service: Source branch: if !cabin.getDeck().getShip().getShipId().equals(cabin.getShip().getShipId())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
