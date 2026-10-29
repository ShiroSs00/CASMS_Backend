# POST /cruises/{cruiseId}/passenger-ble-identities

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/passenger-ble-identities — PassengerBleIdentityController.assign
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PassengerBleIdentityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/passenger-ble-identities + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assign(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.assign()

    Note over Controller,Service: Source branch: if active != null && active.getBleIdentifier().equals(identifier)) return response(active
    Note over Controller,Service: Source branch: if active != null) revokeEntity(active, "Reassigned to a different BLE device", actor
    Note over Controller,Service: Source branch: if bp.getBooking().getBookingStatus(
    Note over Controller,Service: Source branch: if bp.getBooking().getCruise() == null || !bp.getBooking().getCruise().getCruiseId().equals(cruiseId)
    Note over Controller,Service: Source branch: if identities.existsByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier)
    Note over Controller,Service: Source branch: if value == null || value.isBlank() || value.trim().length() > 150
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.lockById()
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: PassengerBleIdentityRepository.existsByCruiseCruiseIdAndActiveBleIdentifier()
            Service->>Repository: PassengerBleIdentityRepository.findByCruiseCruiseIdAndBookingPassengerBookingPassengerIdAndStatus()
            Service->>Repository: PassengerBleIdentityRepository.saveAndFlush()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
