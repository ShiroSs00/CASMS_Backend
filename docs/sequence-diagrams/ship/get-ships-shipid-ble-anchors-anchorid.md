# GET /ships/{shipId}/ble-anchors/{anchorId}

```mermaid
sequenceDiagram
    title GET /ships/{shipId}/ble-anchors/{anchorId} — BleAnchorController.detail
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BleAnchorController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ships/{shipId}/ble-anchors/{anchorId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke detail(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.detail()

    Note over Controller,Service: Source branch: if device.getDeviceType() != LocationDeviceType.BLE_BEACON && device.getDeviceType() != LocationDeviceType.BLE_RECEIVER
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
