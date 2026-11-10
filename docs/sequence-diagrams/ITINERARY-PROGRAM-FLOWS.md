# CASMS-75: staged itinerary programs — reviewed change paths

Reviewed against controllers, ItineraryProgramService/Policy, ItineraryService, CruiseMainflowService, CruisePreparationService, legacy Activity/ShoreExcursion services, POS assignment services and V90/V92/V95 constraints. This review covers the CASMS-75 delta and shared callers below; unrelated generated route branches retain their previous review status. Runtime context path is `/api`.

## Draft, assignment and completion

Draft creation retains current itinerary/calendar validation and requires no program catalog coverage. Departmental preparation submission retains coordinator, actual CRM principal, permission, evidence, section revision and audit checks; it does not invoke global program completeness. Operations dossier submission is the separate completeness boundary.

```mermaid
sequenceDiagram
    actor CRM
    participant Security as Spring Security
    participant Controller as ItineraryProgramController
    participant Service as ItineraryProgramService
    participant Plans as CruiseMainflowService
    participant Policy as ItineraryProgramPolicy
    participant Repository as JdbcTemplate (bound SQL)
    participant DB as PostgreSQL
    CRM->>Security: Assign selected definitions / PUT schedule / DELETE occurrence
    Security->>Security: CRUISE_MANAGER and CRUISE_UPDATE_DRAFT
    alt Authentication or authorization fails
        Security-->>CRM: 401 / 403
    else Authorized request
        Security->>Controller: Validate UUIDs, If-Match and request fields
        Controller->>Service: assign / scheduleActivity / scheduleExcursion / remove
        Service->>Plans: assertEditable(cruiseId)
        Plans->>DB: Lock Cruise; verify editable review status
        Service->>Repository: Lock Itinerary; compare Cruise revision; require DRAFT
        Repository->>DB: SELECT FOR UPDATE with bound IDs
        alt Stale revision or frozen plan/itinerary
            Service-->>CRM: 409; rollback; no revision change
        else Assign selected definitions
            Service->>Repository: Lock source definitions FOR SHARE; verify source PLANNED
            opt Shore selection
                Service->>DB: Lock exact itinerary port call
                Service->>Policy: Active planned destination at this exact stop; match source port/destination
            end
            Service->>DB: INSERT copies with source IDs and no fabricated schedule
            Note over Service,DB: Additive batch; if any item fails, roll back all copies and Cruise revision
        else Complete onboard occurrence
            Service->>DB: Lock occurrence scoped to Cruise; require PLANNED
            Service->>Policy: Ordered times inside cruise; same-ship dedicated ACTIVITY_SPACE
            Policy->>DB: Lock area; require FACILITY, matching physical deck, no cabin/enabled services
            Service->>DB: Check allocated quantity; UPDATE schedule, area, capacity, explicit USD price
            Note over Service,DB: Same area and time may be shared; no exclusive venue guard
        else Complete shore occurrence
            Service->>DB: Lock occurrence and exact stop; require PLANNED
            Service->>Policy: Planned destination; cruise/call/tender/all-aboard bounds
            Service->>DB: Check allocated quantity/minimum; UPDATE schedule, meeting point, location, capacity, USD price
        else Remove draft occurrence
            Service->>DB: Lock scoped PLANNED occurrence; require no active registrations
            Service->>DB: Set CANCELLED; preserve identity/source/history
        end
        alt Invalid scope, window, area, destination, details or capacity
            Service-->>CRM: 404 or 422 business error; rollback
        else Successful mutation
            Service->>DB: Increment Cruise.revision in same transaction
            Service-->>Controller: New revision (and new IDs for assignment)
            Controller-->>CRM: ApiResponse; assign 201, other mutations 200
        end
    end
```

`GET /itineraries/{itineraryId}/programs` requires ITINERARY_READ or CRUISE_READ, loads itinerary scope, returns Cruise revision, all assigned occurrences (including CANCELLED history), text status, and current per-record completeness issues. A read does not change revisions or source definitions. Missing itinerary returns 404. Request constraints reject empty selection, null selection elements, excessive list length, missing fields, nonpositive capacity, negative/overprecision price and long location fields with 400. Service business validation returns 422.

## Operations gate and legacy callers

```mermaid
sequenceDiagram
    actor Staff
    participant Security as Existing route security
    participant Controller as Itinerary / Mainflow / Preparation controller
    participant Service as Transition service
    participant Policy as ItineraryProgramPolicy
    participant Repository as JDBC / JPA repositories
    participant DB as PostgreSQL
    Staff->>Security: Submit itinerary or operations plan; approve; publish; ready
    Security->>Controller: Existing role/authority/principal checks
    Controller->>Service: Existing transition request and expected revision where defined
    Service->>DB: Lock Cruise (then Itinerary on direct itinerary transitions)
    Service->>Service: Existing state, maker-checker, revision and evidence rules
    Service->>Policy: Require complete / collect planBlockers
    Policy->>Repository: Read Cruise, active activities, active stops and excursions
    Repository->>DB: Bound queries for times, area usage, services, destinations and supplier
    Policy->>Policy: Validate all selected noncancelled occurrences; ordered arrival/departure
    Note over Policy,DB: Onboard within cruise; shore within cruise and exact call with tender and strict return deadline
    Note over Policy,DB: Destination explicitly planned for stop; supplier ACTIVE/VERIFIED; positive capacity, explicit price/USD, location/meeting point
    alt Program or call incomplete/invalid
        Policy-->>Service: Per-record issues / distinct blocker codes
        Service-->>Staff: 422 ITINERARY_PROGRAMS_INCOMPLETE or existing PLAN_INCOMPLETE; no state change
    else Program completeness passes
        Service->>Service: Other readiness/pricing/preparation checks remain required
        Service->>Repository: Persist transition, snapshot/audit and existing dossier actions
        Repository->>DB: Commit transaction
        Service-->>Staff: Existing ApiResponse / mainflow View
    end
    opt Independent departmental submission
        Staff->>Controller: Submit technical/hotel/entertainment/personnel section
        Controller->>Service: Existing section request
        Service->>DB: Lock Cruise and section; validate coordinator, revision, evidence; audit
        Note over Service,Policy: Global program-completeness gate is not called for a single section
        Service-->>Staff: Existing section response; unrelated incomplete activities do not block
    end
```

The mainflow View's `planBlockers` and `readyBlockers` now include these program-policy codes. Shared `get()` responses after windows and transitions therefore expose them too. Mainflow submit/review/ready recheck current data; existing snapshot fingerprints include occurrence rows and preserve approval invalidation. Direct itinerary submit, approve and publish take Cruise→Itinerary locks before reading transition state. Draft creation, update and feasibility validation retain calendar/day rules (ship timezone), without requiring every catalog definition.

ActivityService create/update/catalog-schedule/validation callers retain ordered-time, pricing, venue, capacity and lifecycle rules but no longer reject an otherwise valid activity solely because another activity uses the same location/time. `scheduleConflicts` checks existence and returns zero for this removed exclusive-venue rule. Cruise-scoped time repair retains permission, editable plan, If-Match, commitment and trip bounds but removes only that venue-overlap rejection. ShoreExcursionService create/update/validation callers similarly remove the same-stop overlap prohibition while preserving port window/tender/return, supplier and capacity validations. Passenger availability and registration capacity are not relaxed. Legacy complete occurrences still need dedicated area/planned destination completeness before operations approval.

## Area classification and persistence guards

```mermaid
sequenceDiagram
    actor Manager
    participant Security as Spring Security
    participant Controller as ShipAreaUsageController / existing POS controller
    participant Service as Inline JDBC or POS assignment service
    participant Repository as JdbcTemplate
    participant DB as PostgreSQL + V95 triggers
    Manager->>Security: PUT area usage or enabled service/activity assignment
    Security->>Controller: Usage: SHIP_MODEL_OBJECT_UPDATE (default Admin); POS: existing POS_MANAGE/account checks
    Controller->>Service: Validate input and physical ship/area scope
    Service->>Repository: Lock physical area; retain existing model/deck/resource checks
    Repository->>DB: UPDATE usage / INSERT or UPDATE enabled assignment
    DB->>DB: Lock shared area row in assignment trigger
    alt Service assigned to ACTIVITY_SPACE or activity assigned elsewhere
        DB-->>Service: 23514 integrity violation; rollback
        Service-->>Manager: Existing integrity handler: conflict response
    else Classification conflicts with active programs or enabled assignments
        DB-->>Service: 23514 integrity violation; rollback
        Service-->>Manager: Existing integrity handler: conflict response
    else Valid compatible classification/assignment
        DB-->>Service: Commit
        Service-->>Manager: Existing ApiResponse
    end
```

`GET /ships/{shipId}/areas/usages` permits SHIP_CONFIGURATION_READ or CRM+CRUISE_UPDATE_DRAFT and returns physical FACILITY usage/model status and whether enabled services exist; it does not mutate configuration. Usage PUT locks the exact ship's FACILITY (404 otherwise), explicitly rejects enabled services before ACTIVITY_SPACE with 422 AREA_HAS_SERVICES, then DB guards enforce reverse/reclassification conflicts. Usage does not make an archived/unbound model a schedulable venue.

V95 backfills SERVICE for enabled service assignments, then ACTIVITY_SPACE for enabled activity assignments only where no service exists; remaining unknown areas stay OTHER. It does not delete legacy assignments or guess from names. Catalog definitions remain unscheduled; PLANNED/CANCELLED occurrences may be incomplete, but operational states require time/capacity at DB level. Existing positivity and chronological CHECK constraints remain. No migration is applied to shared systems by this change.

## Validation boundary

PostgreSQL tests exercise real Flyway and guards, request authorization/validation, scope/revision, additive scheduling, cancellation and rollback. See [QA evidence](../../specs/CASMS-75-staged-itinerary-programs/qa.md). `sequence_docs.py --check` checks route and file coverage plus Mermaid block structure; it does not establish semantic correctness or validate unchanged generated branches.

Draft availability: shore occurrences with unknown capacity are treated as unavailable (zero availability) using the existing unavailable business exception; the availability route does not unbox null capacity. This does not open drafts for registration.
