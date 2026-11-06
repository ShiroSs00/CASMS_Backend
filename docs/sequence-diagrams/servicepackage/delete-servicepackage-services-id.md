# DELETE /servicepackage/services/{id}

```mermaid
sequenceDiagram
    title DELETE /servicepackage/services/{id} — ServicepackageController.deleteService
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /servicepackage/services/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke deleteService(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.deleteService()

    Note over Controller,Service: Source branch: if requestRepo.existsByService_ServiceId(id
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: FeedbackRepository.existsByService_ServiceId()
            Service->>Repository: ServiceRepository.delete()
            Service->>Repository: ServiceRequestRepository.existsByService_ServiceId()
            Service->>Repository: TourPackageServiceRepository.existsByService_ServiceId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#service-catalog). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

ServiceView returns purchase-stage flags, period policy/limit and time zone. POST/PUT validate stage and quota configuration; no occurrence date/time is stored. Generic availability remains a capacity check, not passenger quota availability.
