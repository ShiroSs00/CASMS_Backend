# POST /checkin/guidance-sessions/{sessionId}/participants/auto-populate

```mermaid
sequenceDiagram
    title POST /checkin/guidance-sessions/{sessionId}/participants/auto-populate — GuidanceSessionController.autoPopulate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as GuidanceSessionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /checkin/guidance-sessions/{sessionId}/participants/auto-populate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke autoPopulate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: guidanceSessionService.autoPopulateParticipants()

    Note over Controller,Service: Source branch: for BookingPassenger bp : passengers
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.findActivePassengersByCruiseId()
            Service->>Repository: GuidanceSessionParticipantRepository.existsByGuidanceSession_GuidanceSessionIdAndBookingPassenger_BookingPassengerId()
            Service->>Repository: GuidanceSessionParticipantRepository.save()
            Service->>Repository: GuidanceSessionRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
