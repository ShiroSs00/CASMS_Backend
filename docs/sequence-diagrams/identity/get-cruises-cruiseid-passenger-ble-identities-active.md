# GET /cruises/{cruiseId}/passenger-ble-identities/active

```mermaid
sequenceDiagram
    title GET /cruises/{cruiseId}/passenger-ble-identities/active — PassengerBleIdentityController.active
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PassengerBleIdentityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruises/{cruiseId}/passenger-ble-identities/active + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke active(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.active()


        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
