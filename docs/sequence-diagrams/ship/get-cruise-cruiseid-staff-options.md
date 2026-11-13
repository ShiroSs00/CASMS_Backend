# GET /cruise/{cruiseId}/staff/options

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/staff/options — CruiseController.staffOptions
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/staff/options + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke staffOptions(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: staffService.staffOptions()

    Note over Controller,Service: Source branch: if !cruiseRepository.existsById(cruiseId)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.existsById()
            Service->>Repository: UserRepository.findByStatus()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```

## Shipboard roles

Requires CRUISE_STAFF_ASSIGN at the controller. Optional shipboardOnly=true returns only SHIP_MANAGER, ONBOARD_STAFF and FINANCE_OFFICER and includes active users with at least one matching role. Omitting the filter returns the full list. See the [reviewed flow](../ITINERARY-PROGRAM-FLOWS.md).

## HR assignment lifecycle

Only eligible accounts without any ACTIVE cruise assignment are included. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
