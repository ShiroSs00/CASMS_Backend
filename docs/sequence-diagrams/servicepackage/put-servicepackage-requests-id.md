# PUT /servicepackage/requests/{id}

```mermaid
sequenceDiagram
    title PUT /servicepackage/requests/{id} — ServicepackageController.updateRequest
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /servicepackage/requests/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke updateRequest(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: serviceRequests.updatePending()

    Note over Controller,Service: Source branch: if quantity==null||quantity<1)throw new IllegalArgumentException("Quantity must be positive"
    Note over Controller,Service: Source branch: if r.getStatus()!=ServiceRequestStatus.REQUESTED)throw new IllegalStateException("Only requested service requests may be edited"
    Note over Controller,Service: Source branch: if service.getCapacity() == null
    Note over Controller,Service: Source branch: if staff)return; if(r.getPassenger().getUser()==null||!r.getPassenger().getUser().getUserId().equals(userId)
    Note over Controller,Service: Source branch: if used + requestedQuantity > service.getCapacity()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ServiceRepository.findByIdForUpdate()
            Service->>Repository: ServiceRequestRepository.lockById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
