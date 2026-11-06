# POST /cruises/{cruiseId}/ble-receivers/{receiverId}/calibrate-at-one-meter

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/ble-receivers/{receiverId}/calibrate-at-one-meter — BleRuntimeController.calibrate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BleRuntimeController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/ble-receivers/{receiverId}/calibrate-at-one-meter + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke calibrate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.calibrateAtOneMeter()

    Note over Controller,Service: Source branch: if cruise.getShip() == null
    Note over Controller,Service: Source branch: if cruise.getStatus() != CruiseStatus.IN_PROGRESS
    Note over Controller,Service: Source branch: if fresh.size(
    Note over Controller,Service: Source branch: if receiver.getDeck(
    Note over Controller,Service: Source branch: if receiver.getDeviceType() != LocationDeviceType.BLE_RECEIVER
    Note over Controller,Service: Source branch: if receiver.getInstalledAt() == null
    Note over Controller,Service: Source branch: if receiver.getStatus() != LocationDeviceStatus.ACTIVE
    Note over Controller,Service: Source branch: if records.isEmpty()
    Note over Controller,Service: Source branch: if tx < -100 || tx > -20
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: LocationDeviceRepository.save()
            Service->>Repository: LocationRecordRepository.findTop100ByDeviceLocationDeviceIdOrderByRecordedAtDesc()
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
