# POST /itineraries/{itineraryId}/submit

```mermaid
sequenceDiagram
    title POST /itineraries/{itineraryId}/submit — ItineraryController.submit
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /itineraries/{itineraryId}/submit + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke submit(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.submit()

    Note over Controller,Service: Source branch: for int i = 0; i < days.size(); i++
    Note over Controller,Service: Source branch: if cruise.getDepartureDatetime() != null && cruise.getReturnDatetime() != null
    Note over Controller,Service: Source branch: if days == null || days.isEmpty()
    Note over Controller,Service: Source branch: if days.get(i).getDate().isBefore(itinerary.getStartDate()
    Note over Controller,Service: Source branch: if days.get(i).getDayNumber() != i + 1
    Note over Controller,Service: Source branch: if days.size() != expectedDayCount
    Note over Controller,Service: Source branch: if itinerary.getCruise() != null
    Note over Controller,Service: Source branch: if itinerary.getDays() != null
    Note over Controller,Service: Source branch: if itinerary.getEndDate().isBefore(itinerary.getStartDate())
    Note over Controller,Service: Source branch: if itinerary.getStartDate() == null || itinerary.getEndDate() == null
    Note over Controller,Service: Source branch: if itinerary.getStartDate().isBefore(departureDate
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.DRAFT
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ItineraryRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```

## CASMS-75 behavior change

Draft create/update and feasibility checks retain calendar/day validation in ship timezone without full activity coverage. Submit/approve/publish call shared program completeness; mainflow plan/ready blockers and transition responses reflect current issues. Direct itinerary transitions serialize via Cruise then Itinerary locks. Departmental submissions remain independent. Existing maker-checker, pricing, readiness, state/revision checks and response shape remain; incomplete operations submission is 422 and no state is advanced.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasAuthority('ITINERARY_SUBMIT')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ItineraryController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: POST /itineraries/{itineraryId}/submit
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: submit request
    Service->>Service: Retain existing rules; collect current completeness issues at operations boundary
    Service->>Repository: Bound reads of programs/stops/areas/destinations/supplier; transition writes only if all gates pass
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```
