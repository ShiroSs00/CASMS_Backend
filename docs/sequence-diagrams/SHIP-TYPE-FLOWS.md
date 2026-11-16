# Ship type, physical ship and operational profile

The routes use the existing bearer authentication flow. The type write endpoints require `ADMIN`, the physical ship creation and profile endpoints require `SHIP_MANAGER`, and the existing ship operations retain their permission annotations. The record-scope interceptor allows a Ship Manager who created a ship or has an active assignment to that ship to use ship-scoped endpoints, subject to the route permission.

```mermaid
sequenceDiagram
    actor Admin
    actor Manager as Ship Manager
    participant Security as JWT and method security
    participant Controller as ShipTypeController
    participant Service as ShipTypeService
    participant Store as ShipModelAssetStore
    participant Repositories as ShipType, Ship, ShipModel repositories
    participant Importer as ShipModelImportService
    participant DB as PostgreSQL

    Admin->>Security: POST /ship-types (modelKey, ZIP)
    Security->>Controller: ADMIN
    Controller->>Service: Create type (write transaction)
    Service->>Store: Store by bundle digest and read manifest
    Store->>DB: Model key advisory lock and asset blob writes
    alt Missing or invalid required model fields
        Service-->>Admin: 400 with invalid field, transaction rolls back
    else Complete model
        Service->>Repositories: Insert type and pin asset revision
        Repositories->>DB: Unique type code and manifest foreign key
        Service-->>Admin: 201 type, no physical ship created
    end

    Manager->>Security: POST /ship-types/{typeId}/ships
    Security->>Controller: SHIP_MANAGER
    Controller->>Service: Create physical ship (write transaction)
    Service->>Repositories: Lock type and check ACTIVE
    alt No such type or inactive
        Service-->>Manager: 404 or 409
    else Active type
        Service->>Repositories: Generate SH code and insert DRAFT ship with profile
        Repositories->>DB: Check FK and unique IMO and registration indexes
        Service->>Repositories: Register ship-specific ShipModel for pinned asset
        Service->>Importer: Import deck, cabin, area and mappings
        Importer->>DB: Read shared asset and write ship-scoped structure and import job
        alt Import rejects any object
            Service-->>Manager: Import error and rollback of ship, model and structure
        else Import succeeds
            Service->>Repositories: Validate mappings and activate model
            Service-->>Manager: 201 physical ship
        end
    end
```

- Type manifest fields include `modelId`, `name`, `shipManufacturerName`, `engineType`, `passengerCapacity`, `deckCount`, `length` and `beam`. The supplied model key identifies the uploaded bundle and becomes the initial type code.
- Admin list and detail include inactive types. Ship Manager list and detail resolve active types only. Admin model replacement and status or delete run in write transactions and take the type lock. Replacing the type model updates the selection for subsequently created ships. Existing ship model rows retain their registered asset version. Deleting a referenced type returns 409.
- Per-ship `ShipModel` and object rows hold the mappings and model lifecycle. Physical ships reuse the binary assets through the existing model-key and asset-version reference. Ship-specific operations retain manager-entered vessel names when they register or re-import a model from the same manifest identity.
- The operational profile accepts references to certification, warranty, maintenance and repair records. The profile payload includes the manufacturer's vessel code, IMO, shipyard, build and registration fields. The ship's existing `shipCode` remains the distinct server-generated business code and `shipId` remains its database primary key.
- `PUT /ships/{shipId}/operational-profile` replaces the profile for a typed ship in a write transaction. The ship row is locked to serialize replacement against a status change. PostgreSQL checks uniqueness of normalized IMO and country/registration-number pairs. The API returns the saved profile. The scoped GET on the same endpoint returns the ship profile, including an empty result when it has yet to be entered.
- `POST /ships/{shipId}/status` locks the ship. A same-state request returns the current ship. For a typed ship with missing certification, warranty or maintenance data, it returns 422 with `missingGroups`. For a complete profile it continues the existing active-cruise guard and saves the new status. Legacy ships retain their existing lifecycle.
- The authenticated ship list and detail expose the type ID for typed ships. Managers can see the ships they created as well as active assignments. The two former direct-creation endpoints return 410 and guide authorized callers to the type APIs. Existing ships keep their IDs and may continue legacy operations.
