# GET /cruise-plans/{cruiseId}/program-options

```mermaid
sequenceDiagram
    title GET /cruise-plans/{cruiseId}/program-options — CruiseScopedProgramsController.options
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise-plans/{cruiseId}/program-options + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke options(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: plans.get()

    Note over Controller,Service: Source branch: for String table:List.of("Cabin","Deck","ShipRegulation")
    Note over Controller,Service: Source branch: for String table:List.of("CruiseStaff","CruisePreparationSection","CruiseRegulation")
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(captain))b.add("CAPTAIN_REQUIRED"
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(guidance))b.add("GUIDANCE_PUBLICATION_REQUIRED"
    Note over Controller,Service: Source branch: if !Set.of("DOMESTIC","INTERNATIONAL").contains(Objects.toString(c.get("planned_scope"),"")))b.add("SCOPE_COUNTRY_REQUIRED"
    Note over Controller,Service: Source branch: if !approved(id,r))b.add("PLAN_APPROVAL_REQUIRED"
    Note over Controller,Service: Source branch: if !operational.isReady()) b.addAll(operational.getBlockers()
    Note over Controller,Service: Source branch: if "SUBMITTED".equals(status(r)))actions.addAll(List.of("WITHDRAW","APPROVE","REJECT","REQUEST_REVISION")
    Note over Controller,Service: Source branch: if Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"CruisePricingPolicy\" WHERE cruise_id=?)",Boolean.class,id
    Note over Controller,Service: Source branch: if Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"ItineraryDay\" d JOIN \"Itinerary\" i USING(itinerary_id
    Note over Controller,Service: Source branch: if Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Itinerary\" i JOIN \"Cruise\" c USING(cruise_id
    Note over Controller,Service: Source branch: if Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Itinerary\" i WHERE i.cruise_id=? AND ((SELECT count(*
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
