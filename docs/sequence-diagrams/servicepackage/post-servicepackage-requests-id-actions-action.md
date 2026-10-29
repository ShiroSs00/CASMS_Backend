# POST /servicepackage/requests/{id}/actions/{action}

```mermaid
sequenceDiagram
    title POST /servicepackage/requests/{id}/actions/{action} — ServicepackageController.serviceRequestAction
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/requests/{id}/actions/{action} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke serviceRequestAction(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: serviceRequests.transition()

    Note over Controller,Service: Source branch: if r.getStatus()!=ServiceRequestStatus.ACCEPTED||r.getAssignedUser()==null||!r.getAssignedUser().getUserId().equals(userId))invalid(
    Note over Controller,Service: Source branch: if r.getStatus()!=ServiceRequestStatus.IN_PROGRESS||r.getAssignedUser()==null||!r.getAssignedUser().getUserId().equals(userId)
    Note over Controller,Service: Source branch: if r.getStatus()!=ServiceRequestStatus.REQUESTED&&r.getStatus()!=ServiceRequestStatus.ACCEPTED)invalid(
    Note over Controller,Service: Source branch: if r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid(); User user=users.findById(userId).orElseThrow(()->missing("User",userId)
    Note over Controller,Service: Source branch: if r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid();r.setStatus(ServiceRequestStatus.ACCEPTED
    Note over Controller,Service: Source branch: if r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid();r.setStatus(ServiceRequestStatus.REJECTED
    Note over Controller,Service: Source branch: switch action
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ServiceRequestRepository.lockById()
            Service->>Repository: UserRepository.findById()
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
