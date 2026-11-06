# POST /cruise/preview-schedule

```mermaid
sequenceDiagram
    title POST /cruise/preview-schedule — CruiseController.previewSchedule
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/preview-schedule + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke previewSchedule(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: scheduleMaterializationService.previewSchedule()

    Note over Controller,Service: Source branch: for CruiseSchedulePreviewResponse.ProjectedStopDto s : projectedStops
    Note over Controller,Service: Source branch: for TripPlanEntry entry : planEntries
    Note over Controller,Service: Source branch: for TripPlanStop stop : planStops
    Note over Controller,Service: Source branch: for int d = 1; d <= totalDays; d++
    Note over Controller,Service: Source branch: for int i = 0; i < projectedStops.size(); i++
    Note over Controller,Service: Source branch: for int j = i + 1; j < projectedStops.size(); j++
    Note over Controller,Service: Source branch: for var projected : projectedStops
    Note over Controller,Service: Source branch: if !projectedReturn.isAfter(departureDatetime)
    Note over Controller,Service: Source branch: if a.getPlannedArrival().isBefore(b.getPlannedDeparture()
    Note over Controller,Service: Source branch: if a.getStopOrder() != null && b.getStopOrder() != null
    Note over Controller,Service: Source branch: if a.getStopOrder() < b.getStopOrder(
    Note over Controller,Service: Source branch: if allAboard.isBefore(plannedArr) || allAboard.isAfter(plannedDep)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```


Changed branches for adjacent port identity, terminal role and time checks are reviewed in [Port call sequence](../PORT-CALL-SEQUENCE.md). This addendum does not certify the static trace for unrelated behavior.

Preview applies the offset from template departure LocalTime before checking actual port-call windows. See [departure and delay flows](../CRUISE-DEPARTURE-DELAY.md).
