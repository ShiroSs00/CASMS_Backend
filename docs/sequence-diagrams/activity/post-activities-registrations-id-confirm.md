# POST /activities/registrations/{id}/confirm

```mermaid
sequenceDiagram
    title POST /activities/registrations/{id}/confirm — ActivityController.confirm
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /activities/registrations/{id}/confirm + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirm(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.confirmRegistration()

    Note over Controller,Service: Source branch: if !a.getEndTime().isAfter(Instant.now()))throw new IllegalStateException("Activity has ended");if(occupied(a.getOnboardActivityId()
    Note over Controller,Service: Source branch: if !excursion.getPlannedReturnTime().isAfter(Instant.now()))throw new IllegalStateException("Excursion has ended"
    Note over Controller,Service: Source branch: if !r.getPassenger().getPassengerId().equals(r.getBookingPassenger().getPassenger().getPassengerId())
    Note over Controller,Service: Source branch: if a!=null
    Note over Controller,Service: Source branch: if a.getStatus()!=OnboardActivityStatus.OPEN&&a.getStatus()!=OnboardActivityStatus.FULL
    Note over Controller,Service: Source branch: if excursion!=null
    Note over Controller,Service: Source branch: if excursion.getStatus()!=ShoreExcursionStatus.OPEN&&excursion.getStatus()!=ShoreExcursionStatus.FULL
    Note over Controller,Service: Source branch: if occupiedExcursion(targetId)>excursion.getMaximumCapacity())throw new IllegalStateException("Excursion capacity exceeded"
    Note over Controller,Service: Source branch: if participations.findByRegistration_RegistrationId(id).isEmpty()
    Note over Controller,Service: Source branch: if r.getStatus()!=RegistrationStatus.PENDING)invalid(
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OnboardActivityRepository.lockActivity()
            Service->>Repository: ParticipationRepository.findByRegistration_RegistrationId()
            Service->>Repository: ParticipationRepository.save()
            Service->>Repository: RegistrationRepository.lockById()
            Service->>Repository: ShoreExcursionRepository.lockById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
