# GET /cruise/{cruiseId}/sellability

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/sellability — CruiseController.getPublicSellability
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/sellability + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getPublicSellability(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.getPublicSellability()

    Note over Controller,Service: Source branch: for CabinType type : CabinType.values()
    Note over Controller,Service: Source branch: for CruiseTourPackage ctp : ctpList
    Note over Controller,Service: Source branch: if !hasInventory || totalAvailableCabinCapacity == 0
    Note over Controller,Service: Source branch: if !isDepartureFuture
    Note over Controller,Service: Source branch: if !isItineraryOpen || policyOpt.isEmpty() || cruise.getOpenedForSaleAt() == null || !mainflow.isReady(cruiseId
    Note over Controller,Service: Source branch: if cruise.getStatus() == CruiseStatus.CANCELLED
    Note over Controller,Service: Source branch: if cruise.getStatus() == CruiseStatus.DRAFT
    Note over Controller,Service: Source branch: if depositPercentage != null && depositPercentage.compareTo(BigDecimal.ONE) > 0
    Note over Controller,Service: Source branch: if isStopped
    Note over Controller,Service: Source branch: if pkg == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.countConfirmedPassengersByCruiseId()
            Service->>Repository: BookingPassengerRepository.countHeldPassengersByCruiseId()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise()
            Service->>Repository: CabinRepository.sumAvailableCapacityByShipId()
            Service->>Repository: CabinRepository.sumAvailableCapacityByShipIdAndCabinType()
            Service->>Repository: CruisePricingPolicyRepository.findByCruise_CruiseId()
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: CruiseTourPackageRepository.findByCruise_CruiseId()
            Service->>Repository: ItineraryRepository.findByCruiseCruiseId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
