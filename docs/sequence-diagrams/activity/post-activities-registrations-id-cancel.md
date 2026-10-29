# POST /activities/registrations/{id}/cancel

```mermaid
sequenceDiagram
    title POST /activities/registrations/{id}/cancel — ActivityController.cancel
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /activities/registrations/{id}/cancel + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke cancel(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.cancelRegistration()

    Note over Controller,Service: Source branch: if !r.getPassenger().getPassengerId().equals(r.getBookingPassenger().getPassenger().getPassengerId())
    Note over Controller,Service: Source branch: if r.getOnboardActivity()!=null && !r.getOnboardActivity().getStartTime().isAfter(Instant.now())
    Note over Controller,Service: Source branch: if r.getOnboardActivity()!=null && r.getOnboardActivity().getStatus()==OnboardActivityStatus.FULL && occupied(r.getOnboardActivity(
    Note over Controller,Service: Source branch: if r.getShoreExcursion()!=null&&!r.getShoreExcursion().getStartTime().isAfter(Instant.now())
    Note over Controller,Service: Source branch: if r.getShoreExcursion()!=null&&r.getShoreExcursion().getStatus()==ShoreExcursionStatus.FULL&&occupiedExcursion(r.getShoreExcursion(
    Note over Controller,Service: Source branch: if r.getStatus()!=RegistrationStatus.PENDING && r.getStatus()!=RegistrationStatus.CONFIRMED) invalid(
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ParticipationRepository.findByRegistration_RegistrationId()
            Service->>Repository: RegistrationRepository.lockById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
