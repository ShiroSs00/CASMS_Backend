# POST /ships/{shipId}/status

The manual typed-ship profile guard checks expiry against today's date in Vietnam, inclusive. After renewing the profile, a manager with the required authority can use this route to change status; [classification expiry automation](../SHIP-TYPE-FLOWS.md#automatic-maintenance-for-classification-expiry) only changes status to MAINTENANCE.

Ship profile: ShipResponse sau đổi trạng thái chứa bốn trường. Xem [luồng đã rà soát](../SHIP-BASIC-PROFILE.md).

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/status — ShipController.changeStatus
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/status + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke changeStatus(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.changeStatus()

    Note over Controller,Service: Source branch: if !ShipStatus.isOperational(newStatus)
    Note over Controller,Service: Source branch: if currentCruise.isPresent()
    Note over Controller,Service: Source branch: if newStatus == null
    Note over Controller,Service: Source branch: if ship.getStatus() == newStatus
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findCurrentActiveCruise()
            Service->>Repository: ShipRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```

## Ship-type change

The Ship row is locked. A typed ship must complete certification, warranty, and maintenance groups before an actual state change. Missing groups return 422. Existing guard behavior and six states are preserved.

See [ship type flow](../SHIP-TYPE-FLOWS.md).
