# GET /cruises/{cruiseId}/ble-receivers/{receiverId}/tracking

```mermaid
sequenceDiagram
    title GET /cruises/{cruiseId}/ble-receivers/{receiverId}/tracking — BleRuntimeController.snapshot
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BleRuntimeController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruises/{cruiseId}/ble-receivers/{receiverId}/tracking + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke snapshot(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.snapshot()

    Note over Controller,Service: Source branch: if cruise.getShip() == null) throw new BusinessException("CRUISE_HAS_NO_SHIP", "This cruise has no assigned ship"
    Note over Controller,Service: Source branch: if cruise.getStatus() != CruiseStatus.IN_PROGRESS
    Note over Controller,Service: Source branch: if identifier == null || identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier).isEmpty()
    Note over Controller,Service: Source branch: if newest != null && status.equals("LIVE") && median != null
    Note over Controller,Service: Source branch: if receiver.getDeck() == null || receiver.getModelPositionX() == null || receiver.getModelPositionY(
    Note over Controller,Service: Source branch: if receiver.getDeviceType() != LocationDeviceType.BLE_RECEIVER
    Note over Controller,Service: Source branch: if receiver.getInstalledAt() == null
    Note over Controller,Service: Source branch: if receiver.getStatus() != LocationDeviceStatus.ACTIVE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: LocationRecordRepository.findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc()
            Service->>Repository: PassengerBleIdentityRepository.findByCruiseCruiseIdAndActiveBleIdentifier()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
