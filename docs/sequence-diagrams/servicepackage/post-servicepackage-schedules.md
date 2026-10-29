# POST /servicepackage/schedules

```mermaid
sequenceDiagram
    title POST /servicepackage/schedules — ServicepackageController.createServiceSchedule
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/schedules + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createServiceSchedule(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: scheduleManagement.create()

    Note over Controller,Service: Source branch: if !service.isRequiresSchedule()) throw new IllegalStateException("Service is not configured for scheduled reservations"
    Note over Controller,Service: Source branch: if capacity == null || capacity < 1) throw new IllegalArgumentException("Schedule capacity must be positive"
    Note over Controller,Service: Source branch: if cruise != null && !cruise.getCruiseId().equals(stopCruise.getCruiseId())
    Note over Controller,Service: Source branch: if cruise!=null&&(startsAt.isBefore(cruise.getDepartureDatetime())||endsAt.isAfter(cruise.getReturnDatetime()))
    Note over Controller,Service: Source branch: if location == null || location.isBlank() || location.length() > 250
    Note over Controller,Service: Source branch: if schedule.getCreatedAt()==null)schedule.setCreatedAt(Instant.now()
    Note over Controller,Service: Source branch: if schedule.getScheduleCode()==null)schedule.setScheduleCode("SS-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase()
    Note over Controller,Service: Source branch: if startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)
    Note over Controller,Service: Source branch: if startsAt.isBefore(stop.getPlannedArrival()) || endsAt.isAfter(stop.getPlannedDeparture())
    Note over Controller,Service: Source branch: if stop != null
    Note over Controller,Service: Source branch: if stop.getStatus(
    Note over Controller,Service: Source branch: if supplier != null && (supplier.getStatus(
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
