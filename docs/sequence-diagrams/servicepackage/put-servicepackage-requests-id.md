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

    Note over Controller,Service: Source branch: if !schedule.getService().getServiceId().equals(service.getServiceId()
    Note over Controller,Service: Source branch: if other + quantity > schedule.getCapacity()
    Note over Controller,Service: Source branch: if posLedger.isPosRequest(id)
    Note over Controller,Service: Source branch: if quantity == null || quantity < 1
    Note over Controller,Service: Source branch: if r.getFareBenefitSnapshotId() != null
    Note over Controller,Service: Source branch: if r.getPassenger().getUser(
    Note over Controller,Service: Source branch: if r.getServiceSchedule() == null
    Note over Controller,Service: Source branch: if r.getStatus() != ServiceRequestStatus.REQUESTED
    Note over Controller,Service: Source branch: if service.getCapacity() == null
    Note over Controller,Service: Source branch: if staff
    Note over Controller,Service: Source branch: if used + requestedQuantity > service.getCapacity()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ServiceRepository.findByIdForUpdate()
            Service->>Repository: ServiceRequestRepository.lockById()
            Service->>Repository: ServiceRequestRepository.sumActiveQuantityForSchedule()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws com.casms.common.exception.BusinessException; HTTP mapping requires exception-handler review
    end
```

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#prepaid-reservation-and-completion). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

Request reads expose the purchase/usage snapshot and fulfilled quantity. Reservation checks explicit fare or legacy INCLUDED service grants for paid services, plus used/pending period quota. Completion writes usage once in the same transaction; cancellation releases pending quota and reverses linked POS charge where applicable.
