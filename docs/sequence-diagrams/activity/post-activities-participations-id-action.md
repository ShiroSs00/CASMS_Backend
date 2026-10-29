# POST /activities/participations/{id}/{action}

```mermaid
sequenceDiagram
    title POST /activities/participations/{id}/{action} — ActivityController.attendance
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /activities/participations/{id}/{action} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke attendance(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.attendance()

    Note over Controller,Service: Source branch: if p.getOnboardActivity()!=null&&p.getOnboardActivity().getStatus()!=OnboardActivityStatus.IN_PROGRESS)invalid(
    Note over Controller,Service: Source branch: if p.getShoreExcursion()!=null&&p.getShoreExcursion().getStatus()!=ShoreExcursionStatus.IN_PROGRESS)invalid(
    Note over Controller,Service: Source branch: if p.getStatus()!=ParticipationStatus.CHECKED_IN) invalid(); p.setStatus(ParticipationStatus.IN_PROGRESS); p.setCheckOutTime(now
    Note over Controller,Service: Source branch: if p.getStatus()!=ParticipationStatus.IN_PROGRESS) invalid(); p.setStatus(ParticipationStatus.COMPLETED); p.getRegistration(
    Note over Controller,Service: Source branch: if p.getStatus()!=ParticipationStatus.REGISTERED || (p.getOnboardActivity()!=null && p.getOnboardActivity().getEndTime().isAfter(now)
    Note over Controller,Service: Source branch: if p.getStatus()!=ParticipationStatus.REGISTERED || p.getRegistration().getStatus()!=RegistrationStatus.CONFIRMED) invalid(
    Note over Controller,Service: Source branch: switch action
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ParticipationRepository.lockById()
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
