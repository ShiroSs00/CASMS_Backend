# Cruise preparation and operations review

```mermaid
sequenceDiagram
    actor Owner as Assigned department member
    actor OPS as Operations reviewer
    participant Security as JWT, scope and section permissions
    participant Controller as Preparation and review controllers
    participant Service as CruisePreparationService
    participant Repository as Cruise and section repositories
    participant DB as PostgreSQL
    Owner->>Security: Submit assigned section or operating budget
    Security->>Controller: Check role and voyage scope
    Controller->>Service: Validate owner and expected revision
    Service->>Repository: Lock voyage and target record
    Repository->>DB: Read current assignment and version
    alt Wrong assignment or stale revision
        Service-->>Owner: Denied or conflict
    else Valid submission
        Service->>DB: Write state transition and audit snapshot
        Service-->>Owner: Submitted revision
    end
    OPS->>Security: Confirm, return or reject submitted revision
    Security->>Controller: Scoped operations reviewer
    Controller->>Service: Check version, review reason and actor
    alt Stale, self-review or invalid state
        Service-->>OPS: Denied or conflict
    else Accepted decision
        Service->>DB: Save target review and append audit event atomically
        Service-->>OPS: Review result
    end
```

Budget remains one manually entered VND total. The budget endpoints expose submission and review, and the coordinator can revise and resubmit after a negative review. Section submission uses the existing four-section matrix and verifies ownership of the selected section. The CRM bulk operation submits CRM-owned sections. Department handoffs remain independent for ASM and SHIP and append event history. Review events expose snapshots and dependency changes can mark affected submitted or confirmed sections stale.
