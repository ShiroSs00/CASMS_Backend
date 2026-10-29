# POST /ships/{shipId}/ble-anchors/{anchorId}/installation-confirmation

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/ble-anchors/{anchorId}/installation-confirmation — BleAnchorController.confirmInstallation
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BleAnchorController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/ble-anchors/{anchorId}/installation-confirmation + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirmInstallation(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.confirmInstallation()

    Note over Controller,Service: Source branch: if device.getDeviceType() != LocationDeviceType.BLE_BEACON && device.getDeviceType() != LocationDeviceType.BLE_RECEIVER
    Note over Controller,Service: Source branch: if device.getInstalledAt() == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: LocationDeviceRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
