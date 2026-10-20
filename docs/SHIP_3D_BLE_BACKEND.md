# CASMS ship 3D and BLE backend

## Audit and data mapping

The demo in `Demo BLE.rar` contains a React/Three.js viewer, a Python Bleak scanner and a small .NET proximity service. Its actual protocol is one laptop BLE receiver accepting advertisements from one iPhone and reporting RSSI. It applies a median plus EWMA filter and a one-meter calibration, then estimates distance. The viewer projects that range along a user-selected path. The demo explicitly says that one receiver cannot determine passenger x/y or deck; the path position is illustrative.

| Area | Existing backend | Demo contract | Backend change |
| --- | --- | --- | --- |
| Ship | `Ship` CRUD, ship code/status/capacity | Ship identity and a GLB asset | Kept `Ship`; added versioned model asset metadata and complete configuration endpoint |
| Deck | `Deck` CRUD and `ship_id` relationship | `DECK_04` through `DECK_09`, model-local transforms | Kept `Deck`; added model position/rotation, paginated query by ship |
| Room | `Cabin` CRUD and deck relationship | Model node mapped room/cabin with transform | Kept `Cabin`; added model position, rotation, size and by-ship/by-deck queries |
| Model nodes | `ShipModel` and `ShipModelObject` entities already existed, but had no CRUD API | GLB node keys, e.g. deck and cabin nodes | Added immutable READY model versions, node CRUD while draft, and configuration mapping |
| BLE hardware | `LocationDevice` and `LocationRecord` existed without usable 3D coordinates/API | `LAPTOP_RX_01` is a physical receiver manually placed in the model | Added scoped BLE anchor/receiver management, model position, radio calibration and explicit installation timestamp |
| Passenger identity | Passenger and BookingPassenger existed; no BLE lifecycle | A real tag identifier is associated with P001 | Added cruise-scoped active/revoked identity records, reassignment history and database uniqueness markers |
| Runtime | Generic location records, no BLE ingestion API | `{deviceId, deviceName, rssi, timestamp}` | Added authenticated observation ingestion, freshness/order checks and range-only snapshot |
| Authorization | CASMS Permission enum and role initializer | No CASMS auth in the demo bridge | Added permission checks on each endpoint and conservative role assignments |

`model_unit` is used because the GLB and demo coordinates are in Three.js model-local units; the demo does not establish a physical unit. Rotation fields are radians, as used by Three.js. `modelUnitsPerMeter` is a separate calibration factor. Asset data remains in external storage; CASMS stores only the URI and metadata and does not invent or bundle an asset URL.

## Data relationships

```text
Ship 1 ── * Deck 1 ── * Cabin
  │          │           │
  │          ├── * LocationDevice (BLE beacon/receiver)
  │          └── * ShipModelObject
  ├── * ShipModel 1 ── * ShipModelObject ── optional Deck/Cabin mapping
  └── * Cruise 1 ── * Booking 1 ── * BookingPassenger 1 ── * PassengerBleIdentity
LocationDevice 1 ── * LocationRecord (RSSI observations, not x/y positions)
```

An active passenger tag is unique per cruise and an active assignment is unique per passenger/cruise. Revoked rows keep the original tag and audit fields; separate nullable unique marker columns enforce active uniqueness without deleting history. Assignment locks the `BookingPassenger` row and checks that it is active on a confirmed booking for the requested cruise. BLE observations are accepted only for an in-progress cruise, a currently assigned tag and an installed, active receiver on that cruise's ship. The RSSI API never publishes the passenger name or derives x/y coordinates.

## API and permissions

All routes use the application's `/api` context path. Create routes return `201`, successful reads/updates return the existing `ApiResponse` wrapper, and destructive operations return `204`. DTOs are used; JPA entities are not exposed.

| Methods and path | Function | Permission |
| --- | --- | --- |
| Existing `POST/GET /ships`, `GET/PATCH /ships/{shipId}`, `DELETE /ships/{shipId}/unused` | Ship CRUD | `SHIP_CREATE/READ/UPDATE/DELETE` |
| Existing `POST/GET /decks`, `GET/PATCH /decks/{deckId}`, `DELETE /decks/{deckId}/unused` | Deck CRUD; create/update DTOs now include model coordinates | `DECK_CREATE/READ/UPDATE/DELETE` |
| `GET /decks/by-ship/{shipId}` | Paginated deck lookup | `DECK_READ` |
| Existing `POST/GET /cabins`, `GET/PATCH /cabins/{cabinId}`, `DELETE /cabins/{cabinId}/unused` | Cabin CRUD; create/update DTOs now include model position/rotation/dimensions | `CABIN_CREATE/READ/UPDATE/DELETE` |
| `GET /cabins/by-ship/{shipId}`, `GET /cabins/by-deck/{deckId}` | Paginated cabin lookup | `CABIN_READ` |
| `POST/GET /ships/{shipId}/models`, `GET/PUT /ships/{shipId}/models/{modelId}`, `DELETE /ships/{shipId}/models/{modelId}/unused` | Register/read/update/delete draft asset metadata | `SHIP_MODEL_CREATE/READ/UPDATE/DELETE` |
| `POST /ships/{shipId}/models/{modelId}/ready`, `POST .../archive` | Publish an immutable model version or archive it | `SHIP_MODEL_PUBLISH` |
| `POST/GET /ships/{shipId}/models/{modelId}/objects`, `PUT/DELETE .../objects/{objectId}` | CRUD GLB node mappings while model is draft | `SHIP_MODEL_OBJECT_CREATE/READ/UPDATE/DELETE` |
| `GET /ships/{shipId}/3d-configuration` | Model metadata, decks, rooms, node mappings and BLE anchors; includes a configuration version and `updatedAt` | `SHIP_CONFIGURATION_READ` |
| `POST /ships/{shipId}/3d-configuration/validate` | Validate ready model/deck/anchor configuration | `SHIP_CONFIGURATION_VALIDATE` |
| `POST/GET /ships/{shipId}/ble-anchors`, `GET/PATCH /ships/{shipId}/ble-anchors/{anchorId}` | BLE beacon/receiver CRUD and radio configuration | `BLE_ANCHOR_CREATE/READ/UPDATE` |
| `POST .../{anchorId}/activate`, `/deactivate`, `/installation-confirmation`, `/retire`, `/validate`; `DELETE .../{anchorId}/unused` | State transitions, confirmed physical installation, validation and safe deletion | `BLE_ANCHOR_ACTIVATE/DEACTIVATE/INSTALL/RETIRE/VALIDATE/DELETE` |
| `POST /cruises/{cruiseId}/passenger-ble-identities` | Assign or reassign an identifier | `PASSENGER_BLE_ASSIGN` |
| `GET /cruises/{cruiseId}/passenger-ble-identities`, `/active?bookingPassengerId=...` | Read active assignments | `PASSENGER_BLE_READ` |
| `GET /cruises/{cruiseId}/passenger-ble-identities/history?bookingPassengerId=...` | Read retained lifecycle history | `PASSENGER_BLE_HISTORY` |
| `POST /cruises/{cruiseId}/passenger-ble-identities/{identityId}/revoke` | Revoke without deleting history | `PASSENGER_BLE_REVOKE` |
| `POST /cruises/{cruiseId}/ble-receivers/{receiverId}/observations` | Ingest `{deviceId, deviceName, rssi, timestamp}` | `BLE_TELEMETRY_RECEIVE` |
| `GET /cruises/{cruiseId}/ble-receivers/{receiverId}/tracking?bleIdentifier=...` | Read waiting/live/stale/offline range-only snapshot | `BLE_TELEMETRY_READ` |
| `POST /cruises/{cruiseId}/ble-receivers/{receiverId}/calibrate-at-one-meter?bleIdentifier=...` | Calibrate from 10 fresh RSSI observations at a measured 1 m | `BLE_ANCHOR_UPDATE` |

The service roles receive only necessary permissions: `ADMIN` gets all; `SHIP_MANAGER` manages ship/deck/cabin/model/anchors and can receive runtime BLE events; `OPERATIONS_MANAGER` reads 3D configuration and range snapshots; `CHECKIN_STAFF` assigns, reads, revokes and audits passenger BLE tags; `ONBOARD_STAFF` can read range snapshots. `PASSENGER` cannot manage or enumerate BLE identities. No ship/cruise assignment-scope system exists in the audited backend, so the implementation enforces cruise membership and receiver-to-ship scope but cannot enforce a staff-to-ship assignment that the current data model does not contain.

## Example payloads

Register a model asset (use a real, reachable asset reference):

```json
{
  "version": "2026.10.1",
  "fileUri": "/assets/aurelia.glb",
  "fileFormat": "glb",
  "coordinateSystem": "MODEL_LOCAL",
  "coordinateUnit": "model_unit",
  "modelUnitsPerMeter": 1.0
}
```

Assign a passenger tag:

```json
{
  "bookingPassengerId": "booking-passenger-uuid",
  "bleIdentifier": "AA:BB:CC:DD:EE:FF",
  "deviceName": "CASMS-P001"
}
```

The 3D configuration response groups room and anchor arrays under each deck. Room model-node mappings are supplied through `ShipModelObject`; `validate` requires a GLB node mapping for each cabin it returns and complete transforms when optional positions/dimensions are supplied. BLE observations return RSSI and estimated distance only. The demo's route projection remains a client-side illustration and must not be described as absolute passenger positioning. `installedAt` records a staff-confirmed physical installation; `lastSeenAt` is set only after a real observation arrives. An `ACTIVE` configuration status alone does not mean the receiver is online.

## Schema and operations

`V5__ship_3d_ble_and_passenger_identity.sql` adds model coordinate columns, model/object/location/identity tables, active-identity uniqueness and the permission allowlist. It deliberately stores no model binary. The current `application.yml` has Flyway disabled and previously used `ddl-auto: create`, which recreated the schema on each startup. The setting is now `ddl-auto: update` so local data survives a restart; this is a development compatibility setting, not a substitute for controlled deployment migrations. Apply the versioned migrations in a managed database deployment and move its Hibernate setting to `validate` after confirming the existing baseline's PostgreSQL enum mapping. No external object-storage provider or asset URL was configured in the audited project.

## Demo integration boundary

The archive's bridge targets its standalone .NET routes (`/api/proximity/reading`, `/api/tracking`) and sends no CASMS JWT. CASMS keeps the BLE ingress authenticated and adds cruise and receiver IDs to its route, so the bridge must be configured with a CASMS service token and mapped to the active passenger tag before it can call the new endpoint. Do not make the route public: accepting anonymous BLE observations would let a caller spoof a passenger's tracking data. The archive's single receiver cannot identify direction/deck, and no location algorithm or physical installation data was inferred.
