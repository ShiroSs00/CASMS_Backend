# GET /cruise/{cruiseId}/inventory-summary

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/inventory-summary — CruiseController.getInventorySummary
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/inventory-summary + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getInventorySummary(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.getInventorySummary()

    Note over Controller,Service: Source branch: for CabinType type : CabinType.values()
    Note over Controller,Service: Source branch: if cruiseId != null
    Note over Controller,Service: Source branch: if policyOpt.isPresent()
    Note over Controller,Service: Source branch: if shipId != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.countConfirmedPassengersByCruiseId()
            Service->>Repository: BookingPassengerRepository.countHeldPassengersByCruiseId()
            Service->>Repository: CabinAssignmentRepository.countConfirmedAssignmentsByCabinTypeAndCruise()
            Service->>Repository: CabinAssignmentRepository.countHeldAssignmentsByCabinTypeAndCruise()
            Service->>Repository: CabinRepository.countByShipShipId()
            Service->>Repository: CabinRepository.countByShipShipIdAndCabinType()
            Service->>Repository: CabinRepository.countByShipShipIdAndCabinTypeAndHasConnectingDoorTrue()
            Service->>Repository: CabinRepository.countByShipShipIdAndCabinTypeAndIsAccessibleTrue()
            Service->>Repository: CabinRepository.countByShipShipIdAndCabinTypeAndIsFamilyCabinTrue()
            Service->>Repository: CabinRepository.countByShipShipIdAndCabinTypeAndStatus()
            Service->>Repository: CabinRepository.countByShipShipIdAndStatus()
            Service->>Repository: CabinRepository.sumCapacityByShipIdAndCabinType()
            Service->>Repository: CruiseTourPackageRepository.countByCruise_CruiseIdAndActiveTrue()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
