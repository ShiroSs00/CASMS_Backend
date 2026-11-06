# DELETE /cruise-plans/{cruiseId}/activities/{id}

```mermaid
sequenceDiagram
    title DELETE /cruise-plans/{cruiseId}/activities/{id} — CruiseScopedProgramsController.delete
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /cruise-plans/{cruiseId}/activities/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke delete(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: activities.activity()
        Controller->>Service: activities.deleteActivity()

    Note over Controller,Service: Source branch: if a.getStatus(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OnboardActivityRepository.delete()
            Service->>Repository: OnboardActivityRepository.findById()
            Service->>Repository: OnboardActivityRepository.isReferencedSource()
            Service->>Repository: RegistrationRepository.existsByOnboardActivity_OnboardActivityId()
            Service->>Repository: TourPackageActivityRepository.existsByOnboardActivity_OnboardActivityId()
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

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#activity-catalog-itinerary-and-occurrence). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

Catalog and timed occurrences share storage but have explicit catalogEntry identity. Catalog POST rejects schedule fields. Scheduling copies metadata and retains sourceActivityId. Operational activity reads return catalogEntry/sourceActivityId/category/responsibleUserId. Catalog lifecycle/registration is rejected; source deletion is protected. Cruise-scoped reads/writes require the existing cruise access and revision guards.
