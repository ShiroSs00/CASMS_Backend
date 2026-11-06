# Ship model create and update by code

Khi tạo tàu từ model, payload có thể cung cấp bốn trường hồ sơ riêng của tàu. Các trường này không lấy từ manifest và không được đổi khi import lại. Xem [hồ sơ Ship](SHIP-BASIC-PROFILE.md).

Endpoints are relative to /api. The client selects a model with modelKey. Storage identity and optional manifest revision are managed by the server.

| Method | Route | Input and behavior |
| --- | --- | --- |
| POST | /ship-model-assets | modelKey + ZIP. Store or select the uploaded bundle as current for the code. |
| GET | /ship-model-assets | One row per modelKey with the most recent upload time. |
| GET | /ship-model-assets/{modelKey}/preview | Preview the current bundle without creating a ship. |
| POST | /ship-model-assets/{modelKey}/register | Create an independent ship from the current bundle, with optional metadata supplements. |
| POST | /ships | Create manually or use JSON modelKey and optional missing metadata. |
| POST | /ships/{shipId}/models | JSON modelKey. Apply the current stored bundle to the ship and update a matching model in place. |
| POST | /ships/{shipId}/models/imports | modelKey + ZIP. Store and apply to the ship in a single transaction. |
| POST | /ships/{shipId}/models/metadata | Create a draft external-file reference with file metadata. The server assigns its identifier. |
| PUT | /ships/{shipId}/models/{modelId} | Edit a draft external-file reference by modelId. Use model-code import for stored ZIP models. |

The previous preview and register URLs containing assetVersion are retired. Model DTOs expose modelKey rather than model or asset version. The 3D configuration cache token is separate from the model identity. Preview and public manifest projections omit revision. A raw archive asset remains byte-identical to the uploaded file.

```mermaid
sequenceDiagram
    actor Client
    participant Auth as JWT and method security
    participant Controller as Ship and model controllers
    participant Service as ShipModelAssetService / ShipService
    participant Store as ShipModelAssetStore / repositories
    participant DB as PostgreSQL
    Client->>Auth: Upload, preview, or create a ship using modelKey
    Auth->>Controller: Require existing create authority for the action
    Controller->>Service: Validate modelKey, ZIP or optional metadata
    alt Upload to library
        Service->>Store: Hash ZIP, lock model code, validate contents/checksums
        Store->>DB: Store validated files or reuse exact bundle and select it as current
        Controller-->>Client: 201 code and file counts
    else Preview or create
        Service->>Store: Resolve current bundle by modelKey
        Store->>DB: Read latest manifest for model code
        alt Unknown model code
            Service-->>Client: 404
        else Preview
            Controller-->>Client: 200 metadata without revision
        else Create an independent ship
            Service->>Store: Validate supplements and save ship plus DRAFT model
            Store->>DB: Commit ship and model atomically
            Controller-->>Client: 201
        end
    end
```

```mermaid
sequenceDiagram
    actor Client
    participant Auth as JWT and method security
    participant C as ShipModelController
    participant S as ShipModelImportService
    participant R as Model, job, and mapping repositories
    participant DB as PostgreSQL
    Client->>Auth: Apply stored code or import ZIP by code
    Auth->>C: Check create/update permission
    C->>S: Open import transaction
    S->>R: Lock ship and find READY or DRAFT model with same code
    R->>DB: SELECT FOR UPDATE and read registrations
    S->>S: Require CREATE for new, UPDATE for existing, PUBLISH for READY update
    alt Unknown stored code, invalid ZIP or permission/state error
        S-->>Client: 400 / 403 / 404 / 409 and rollback
    else Resolved bundle
        S->>R: Upsert model and match existing decks, cabins and areas by stable identity
        R->>DB: Insert new rows and update changed values
        alt Any object import fails
            S-->>Client: 422 with issues and rollback
        else All objects valid
            R->>DB: Commit model, mappings and succeeded job
            C-->>Client: Existing model ID, 201 for stored apply or 200 for direct import
        end
    end
```

Applying the same model code keeps its model ID, stable node IDs and existing links. The code's current bundle is chosen once in the transaction. Uploading a bundle alone changes the library selection; already attached ships continue serving their current bundle until an apply/import succeeds. Download streams resolve the stored bundle once so content and Content-Length match if another update commits.
