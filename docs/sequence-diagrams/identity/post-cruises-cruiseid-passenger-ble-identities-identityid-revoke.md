# POST /cruises/{cruiseId}/passenger-ble-identities/{identityId}/revoke

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/passenger-ble-identities/{identityId}/revoke — PassengerBleIdentityController.revoke
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PassengerBleIdentityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/passenger-ble-identities/{identityId}/revoke + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke revoke(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.revoke()

    Note over Controller,Service: Source branch: if identity.getStatus() == PassengerBleIdentityStatus.REVOKED) return response(identity
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: PassengerBleIdentityRepository.findById()
            Service->>Repository: PassengerBleIdentityRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
