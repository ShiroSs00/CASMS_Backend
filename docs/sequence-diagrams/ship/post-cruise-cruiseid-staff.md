# POST /cruise/{cruiseId}/staff

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/staff — CruiseController.assignStaff
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/staff + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assignStaff(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: staffService.assignStaff()

    Note over Controller,Service: Source branch: if !ALLOWED_ASSIGNMENT_ROLES.contains(assignmentRole)
    Note over Controller,Service: Source branch: if !conflicts.isEmpty()
    Note over Controller,Service: Source branch: if !user.isStaff(
    Note over Controller,Service: Source branch: if "ONBOARD_STAFF".equals(assignmentRole
    Note over Controller,Service: Source branch: if user.getStatus() != UserStatus.ACTIVE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus()
            Service->>Repository: CruiseStaffRepository.findConflictingAssignments()
            Service->>Repository: CruiseStaffRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```

## Shipboard roles

SHIP_MANAGER, ONBOARD_STAFF and FINANCE_OFFICER require the matching account role. The service rejects an invalid role with INVALID_CRUISE_STAFF_ROLE before saving the assignment. See the [reviewed flow](../ITINERARY-PROGRAM-FLOWS.md).

## Assignment email

The service flushes the assignment and enqueues rendered HTML and text in the same transaction. The response includes assignmentEmailStatus. A worker only sends committed active assignments with a usable, current recipient. See [email assignment flow](../ITINERARY-PROGRAM-FLOWS.md#email-phân-công-nhân-viên).

Assignment locks the target account row before active status validation so employee inactivation and assignment cannot commit against stale state.

## HR assignment lifecycle

Service checks actor, locks cruise and account, rejects existing ACTIVE assignments globally, and preserves the previous specialty and overlap checks. V100 enforces a partial unique index. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
