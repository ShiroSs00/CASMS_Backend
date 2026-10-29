# POST /cruise/{cruiseId}/preview-change-impact

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/preview-change-impact — CruiseController.previewChangeImpact
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/preview-change-impact + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke previewChangeImpact(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: cruiseImpactAssessmentService.previewChangeImpact()

    Note over Controller,Service: Source branch: for CabinType type : CabinType.values()
    Note over Controller,Service: Source branch: if Math.abs(diff.toMillis()) > 1000
    Note over Controller,Service: Source branch: if activitiesOnDeck > 0
    Note over Controller,Service: Source branch: if bookedPassengers > 0
    Note over Controller,Service: Source branch: if bookedSlotsForType > 0
    Note over Controller,Service: Source branch: if cruise.getDepartureDatetime() != null
    Note over Controller,Service: Source branch: if decks == 0 || cabins == 0
    Note over Controller,Service: Source branch: if newDep != null
    Note over Controller,Service: Source branch: if newRet != null && !newRet.isAfter(newDep)
    Note over Controller,Service: Source branch: if newShip.getStatus() != ShipStatus.ACTIVE
    Note over Controller,Service: Source branch: if newShip.getStatus() == ShipStatus.INACTIVE
    Note over Controller,Service: Source branch: if newShip.getStatus() == ShipStatus.MAINTENANCE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.countActivePassengersByCruiseId()
            Service->>Repository: BookingRepository.countByCruiseCruiseId()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCruiseId()
            Service->>Repository: CabinRepository.countByShipShipId()
            Service->>Repository: CabinRepository.sumCapacityByShipIdAndCabinType()
            Service->>Repository: CruiseRepository.existsOverlappingCruisesExcluding()
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: CruiseTourPackageRepository.countByCruise_CruiseIdAndActiveTrue()
            Service->>Repository: DeckRepository.countByShipShipId()
            Service->>Repository: OnboardActivityRepository.countByCruise_CruiseId()
            Service->>Repository: OnboardActivityRepository.countByCruise_CruiseIdAndDeckIsNotNull()
            Service->>Repository: ShipRepository.findById()
            Service->>Repository: ShoreExcursionRepository.countByCruiseId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
