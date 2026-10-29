# PATCH /ships/{shipId}/ble-anchors/{anchorId}

```mermaid
sequenceDiagram
    title PATCH /ships/{shipId}/ble-anchors/{anchorId} — BleAnchorController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BleAnchorController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /ships/{shipId}/ble-anchors/{anchorId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.update()

    Note over Controller,Service: Source branch: if !deck.getShip().getShipId().equals(ship.getShipId())
    Note over Controller,Service: Source branch: if !request.getTxPowerDbm().equals(device.getTxPowerDbm())) device.setCalibrated(false
    Note over Controller,Service: Source branch: if device.getDeviceType() != LocationDeviceType.BLE_BEACON && device.getDeviceType() != LocationDeviceType.BLE_RECEIVER
    Note over Controller,Service: Source branch: if device.getDeviceType() != LocationDeviceType.BLE_RECEIVER && device.getDeviceType() != LocationDeviceType.BLE_BEACON
    Note over Controller,Service: Source branch: if identifier != null && !identifier.equals(device.getBleIdentifier()
    Note over Controller,Service: Source branch: if request.getBleIdentifier() != null
    Note over Controller,Service: Source branch: if request.getDeckId() != null) device.setDeck(requireDeck(device.getShip(), request.getDeckId())
    Note over Controller,Service: Source branch: if request.getDeviceName() != null) device.setDeviceName(normalize(request.getDeviceName())
    Note over Controller,Service: Source branch: if request.getModelNodeKey() != null) device.setModelNodeKey(normalize(request.getModelNodeKey())
    Note over Controller,Service: Source branch: if request.getModelUnitsPerMeter() != null) device.setModelUnitsPerMeter(request.getModelUnitsPerMeter()
    Note over Controller,Service: Source branch: if request.getPathLossExponent() != null) device.setPathLossExponent(request.getPathLossExponent()
    Note over Controller,Service: Source branch: if request.getStatus() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: LocationDeviceRepository.existsByShipShipIdAndBleIdentifier()
            Service->>Repository: LocationDeviceRepository.save()
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
