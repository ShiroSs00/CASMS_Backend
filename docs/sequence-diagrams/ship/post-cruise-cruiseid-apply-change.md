# POST /cruise/{cruiseId}/apply-change

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/apply-change — CruiseController.applyChange
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/apply-change + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke applyChange(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.applyChange()

    Note over Controller,Service: Source branch: for CabinAssignment ca : activeAssignments
    Note over Controller,Service: Source branch: for CruiseStop stop : stops
    Note over Controller,Service: Source branch: for ItineraryDay day : days
    Note over Controller,Service: Source branch: for OnboardActivity act : activities
    Note over Controller,Service: Source branch: for ShoreExcursion exc : excursions
    Note over Controller,Service: Source branch: if !impact.getGeneratedTasks().isEmpty()
    Note over Controller,Service: Source branch: if !impact.isCanProceed()
    Note over Controller,Service: Source branch: if act.getDeck() != null && act.getDeck().getShip(
    Note over Controller,Service: Source branch: if act.getEndTime() != null) act.setEndTime(act.getEndTime().plus(shift)
    Note over Controller,Service: Source branch: if act.getStartTime() != null) act.setStartTime(act.getStartTime().plus(shift)
    Note over Controller,Service: Source branch: if activeBookings > 0
    Note over Controller,Service: Source branch: if ca.getCabin() != null && ca.getCabin().getDeck() != null && ca.getCabin().getDeck().getShip(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingRepository.countByCruiseCruiseId()
            Service->>Repository: CabinAssignmentRepository.findActiveAssignmentsByCruiseId()
            Service->>Repository: CabinAssignmentRepository.save()
            Service->>Repository: CruiseRepository.findByIdForUpdate()
            Service->>Repository: CruiseRepository.flush()
            Service->>Repository: CruiseRepository.save()
            Service->>Repository: OnboardActivityRepository.findByCruise_CruiseId()
            Service->>Repository: OnboardActivityRepository.save()
            Service->>Repository: PortRepository.findById()
            Service->>Repository: ShipRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
