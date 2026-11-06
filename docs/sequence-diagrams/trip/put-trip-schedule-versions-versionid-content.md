# PUT /trip-schedule-versions/{versionId}/content

```mermaid
sequenceDiagram
    title PUT /trip-schedule-versions/{versionId}/content — TripScheduleVersionController.saveContent
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripScheduleVersionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /trip-schedule-versions/{versionId}/content + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke saveContent(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: versionService.saveDraftById()

    Note over Controller,Service: Source branch: for TripPlanDayDraftDto dayDto : request.getDays()
    Note over Controller,Service: Source branch: for TripPlanEntryDraftDto entryDto : entryDtos
    Note over Controller,Service: Source branch: for TripPlanStop existingStop : version.getStops()
    Note over Controller,Service: Source branch: for TripPlanStopDraftDto stopDto : stopDtos
    Note over Controller,Service: Source branch: for int i = 0; i < stopDtos.size(); i++
    Note over Controller,Service: Source branch: for int i = 1; i <= trip.getDurationDays(); i++
    Note over Controller,Service: Source branch: for int j = i + 1; j < stopDtos.size(); j++
    Note over Controller,Service: Source branch: for var dto : stopDto.getVisits()
    Note over Controller,Service: Source branch: if !activeStopKeyMap.containsKey(existingStop.getClientKey())
    Note over Controller,Service: Source branch: if !dayNumbers.contains(i)
    Note over Controller,Service: Source branch: if !entryKeys.add(entryDto.getClientKey())
    Note over Controller,Service: Source branch: if !portCache.containsKey(stopDto.getPortId())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
