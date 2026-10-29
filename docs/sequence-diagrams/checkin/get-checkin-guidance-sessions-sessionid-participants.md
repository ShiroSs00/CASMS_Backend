# GET /checkin/guidance-sessions/{sessionId}/participants

```mermaid
sequenceDiagram
    title GET /checkin/guidance-sessions/{sessionId}/participants — GuidanceSessionController.listParticipants
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as GuidanceSessionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /checkin/guidance-sessions/{sessionId}/participants + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke listParticipants(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: guidanceSessionService.listParticipants()

    Note over Controller,Service: Source branch: if !sessionRepository.existsById(sessionId)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: GuidanceSessionParticipantRepository.findByGuidanceSession_GuidanceSessionId()
            Service->>Repository: GuidanceSessionRepository.existsById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
