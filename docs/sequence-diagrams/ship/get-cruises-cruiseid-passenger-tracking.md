# GET /cruises/{cruiseId}/passenger-tracking

```mermaid
sequenceDiagram
    title GET /cruises/{cruiseId}/passenger-tracking — PassengerTrackingController.snapshot
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PassengerTrackingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruises/{cruiseId}/passenger-tracking + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke snapshot(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.passengerOverview()

    Note over Controller,Service: Source branch: for var identity : active
    Note over Controller,Service: Source branch: for var receiver : receivers
    Note over Controller,Service: Source branch: if !active.isEmpty() && !receivers.isEmpty()
    Note over Controller,Service: Source branch: if best == null || (live && !bestLive) || (live && bestLive && candidate.proximity().distanceMeters() < best.proximity(
    Note over Controller,Service: Source branch: if cruise.getShip() == null) throw new BusinessException("CRUISE_HAS_NO_SHIP", "Cruise has no assigned ship"
    Note over Controller,Service: Source branch: if cruise.getStatus() != CruiseStatus.IN_PROGRESS
    Note over Controller,Service: Source branch: if latest.isEmpty()
    Note over Controller,Service: Source branch: if newest != null && status.equals("LIVE") && median != null
    Note over Controller,Service: Source branch: if record.getBleIdentifier() != null && record.getRssiDbm() != null) grouped.computeIfAbsent(record.getDevice().getLocationDeviceId(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: LocationDeviceRepository.findByShipShipId()
            Service->>Repository: LocationRecordRepository.findByShipShipIdAndRecordedAtBetweenOrderByRecordedAtAsc()
            Service->>Repository: PassengerBleIdentityRepository.findByCruiseCruiseIdAndStatusOrderByAssignedAtDesc()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
