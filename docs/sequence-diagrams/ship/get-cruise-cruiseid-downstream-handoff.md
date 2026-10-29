# GET /cruise/{cruiseId}/downstream-handoff

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/downstream-handoff — CruiseController.getDownstreamHandoff
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/downstream-handoff + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getDownstreamHandoff(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.getDownstreamHandoff()


        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.countActivePassengersByCruiseId()
            Service->>Repository: BookingRepository.countByCruiseCruiseId()
            Service->>Repository: BookingRepository.countByCruiseCruiseIdAndBookingStatus()
            Service->>Repository: CruiseCheckinRepository.countBoardedPassengersByCruiseId()
            Service->>Repository: CruiseCheckinRepository.countCompletedCheckinsByCruiseId()
            Service->>Repository: CruiseStaffRepository.existsByCruise_CruiseIdAndRoleInCruiseAndStatus()
            Service->>Repository: CruiseStaffRepository.findByCruise_CruiseId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
