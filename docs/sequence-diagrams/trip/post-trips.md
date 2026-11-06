# POST /trips

```mermaid
sequenceDiagram
    title POST /trips — TripController.createTrip
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trips + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createTrip(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: idempotencyService.execute()
        Controller->>Service: tripService.createTrip()

    Note over Controller,Service: Source branch: for int i = 1; i <= durationDays; i++
    Note over Controller,Service: Source branch: if !durationGroup.isValidDays(request.getDurationDays())
    Note over Controller,Service: Source branch: if !versions.isEmpty()
    Note over Controller,Service: Source branch: if actorId != null
    Note over Controller,Service: Source branch: if auditService != null
    Note over Controller,Service: Source branch: if durationGroup == null
    Note over Controller,Service: Source branch: if idempotencyKey == null || idempotencyKey.trim().isEmpty()
    Note over Controller,Service: Source branch: if request.getDurationDays() != null
    Note over Controller,Service: Source branch: if request.getDurationDays() != null && request.getDurationDays() >= 3
    Note over Controller,Service: Source branch: if trip.getCreatedByUser() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TripRepository.save()
            Service->>Repository: TripScheduleVersionRepository.save()
            Service->>Repository: UserRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
