# DELETE /ships/{shipId}/models/{modelId}/unused

The endpoint removes a ship-specific model record. Registered asset bundles are shared and remain available.

```mermaid
sequenceDiagram
    actor Client
    participant Auth as JWT and method security
    participant Scope as CruisePlanOperationsAccess
    participant Controller as ShipModelController
    participant Service as ShipModelService
    participant Repo as Model, object and import job repositories
    participant DB as PostgreSQL
    Client->>Auth: DELETE with bearer token
    alt JWT invalid or absent
        Auth-->>Client: 401
    else Authenticated
        Auth->>Scope: Check ship record scope for non-admin ship managers
        Scope->>DB: For scoped manager, check ship creator or active cruise assignment
        alt Manager outside ship scope
            Scope-->>Client: 403
        else Scope permits request
            Scope->>Auth: Continue to method security
            alt Missing SHIP_MODEL_DELETE
                Auth-->>Client: 403
            else Permission granted
                Auth->>Controller: Invoke deleteDraft
                Controller->>Service: deleteDraft(shipId, modelId)
                Note over Service,DB: Transaction begins
                Service->>Repo: Lock model by ship ID and model ID
                Repo->>DB: Select matching model for update
                alt Model absent or belongs to another ship
                    Service-->>Client: 404 RESOURCE_NOT_FOUND
                else Status does not allow deletion
                    Service-->>Client: 422 SHIP_MODEL_IN_USE
                else Status allows deletion
                    Service->>Repo: Check for mapped objects and import jobs
                    Repo->>DB: Query model references
                    alt Object mapping or import history exists
                        Service-->>Client: 422 SHIP_MODEL_HAS_OBJECTS or SHIP_MODEL_IN_USE
                    else No references
                        Service->>Repo: Delete model
                        Repo->>DB: Delete model row at flush
                        Note over Service,DB: Commit
                        Service-->>Controller: Deletion completed
                        Controller-->>Client: 204 No Content
                    end
                end
            end
        end
    end
```

The role initializer grants `SHIP_MODEL_DELETE` to `SHIP_MANAGER` at startup. A ship manager without the admin role can delete only a `DRAFT`. Other authorized actors may also delete a `FAILED` model. Refusals in the service roll back the transaction and the global exception handler serializes the error.
