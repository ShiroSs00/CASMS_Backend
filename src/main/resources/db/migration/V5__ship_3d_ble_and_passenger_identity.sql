-- Metadata and coordinates only: model binaries stay in external asset storage.
ALTER TABLE "Deck" ADD COLUMN IF NOT EXISTS model_position_x double precision;
ALTER TABLE "Deck" ADD COLUMN IF NOT EXISTS model_position_y double precision;
ALTER TABLE "Deck" ADD COLUMN IF NOT EXISTS model_position_z double precision;
ALTER TABLE "Deck" ADD COLUMN IF NOT EXISTS model_rotation_x double precision;
ALTER TABLE "Deck" ADD COLUMN IF NOT EXISTS model_rotation_y double precision;
ALTER TABLE "Deck" ADD COLUMN IF NOT EXISTS model_rotation_z double precision;

ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_position_x double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_position_y double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_position_z double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_rotation_x double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_rotation_y double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_rotation_z double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_width double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_height double precision;
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS model_depth double precision;

CREATE TABLE IF NOT EXISTS "ShipModel" (
    ship_model_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ship_id uuid NOT NULL REFERENCES "Ship"(ship_id),
    version varchar(50) NOT NULL,
    file_uri varchar(1000) NOT NULL,
    file_format varchar(10) NOT NULL,
    checksum_sha256 varchar(64),
    coordinate_system varchar(30) NOT NULL DEFAULT 'MODEL_LOCAL',
    coordinate_unit varchar(30) NOT NULL DEFAULT 'model_unit',
    model_units_per_meter double precision NOT NULL DEFAULT 1,
    calibrated boolean NOT NULL DEFAULT false,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_ship_model_version UNIQUE(ship_id, version),
    CONSTRAINT ck_ship_model_format CHECK (lower(file_format) IN ('glb','gltf')),
    CONSTRAINT ck_ship_model_scale CHECK (model_units_per_meter BETWEEN 0.1 AND 10)
);
CREATE INDEX IF NOT EXISTS idx_ship_model_ship_status ON "ShipModel"(ship_id, status);

CREATE TABLE IF NOT EXISTS "ShipModelObject" (
    ship_model_object_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ship_model_id uuid NOT NULL REFERENCES "ShipModel"(ship_model_id),
    node_key varchar(200) NOT NULL,
    display_name varchar(200),
    object_type varchar(30) NOT NULL,
    deck_id uuid REFERENCES "Deck"(deck_id),
    cabin_id uuid REFERENCES "Cabin"(cabin_id),
    metadata_json text,
    position_x double precision,
    position_y double precision,
    position_z double precision,
    rotation_x double precision,
    rotation_y double precision,
    rotation_z double precision,
    scale_x double precision NOT NULL DEFAULT 1,
    scale_y double precision NOT NULL DEFAULT 1,
    scale_z double precision NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_ship_model_object_node UNIQUE(ship_model_id, node_key),
    CONSTRAINT ck_ship_model_object_metadata_json CHECK (metadata_json IS NULL OR jsonb_typeof(metadata_json::jsonb) IS NOT NULL),
    CONSTRAINT ck_ship_model_object_scale CHECK (scale_x > 0 AND scale_y > 0 AND scale_z > 0)
);
CREATE INDEX IF NOT EXISTS idx_ship_model_object_deck ON "ShipModelObject"(deck_id);
CREATE INDEX IF NOT EXISTS idx_ship_model_object_cabin ON "ShipModelObject"(cabin_id);

CREATE TABLE IF NOT EXISTS "LocationDevice" (
    location_device_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ship_id uuid NOT NULL REFERENCES "Ship"(ship_id),
    device_code varchar(100) NOT NULL UNIQUE,
    device_name varchar(120),
    ble_identifier varchar(150),
    model_node_key varchar(200),
    device_type varchar(30) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'INACTIVE',
    deck_id uuid,
    model_position_x double precision,
    model_position_y double precision,
    model_position_z double precision,
    model_rotation_x double precision NOT NULL DEFAULT 0,
    model_rotation_y double precision NOT NULL DEFAULT 0,
    model_rotation_z double precision NOT NULL DEFAULT 0,
    tx_power_dbm double precision NOT NULL DEFAULT -59,
    path_loss_exponent double precision NOT NULL DEFAULT 2.2,
    model_units_per_meter double precision NOT NULL DEFAULT 1,
    installed_at timestamptz,
    installed_by varchar(100),
    last_seen_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_location_device_ship_identifier UNIQUE(ship_id, ble_identifier),
    CONSTRAINT uk_location_device_ship_id UNIQUE(location_device_id, ship_id),
    CONSTRAINT fk_location_device_deck_ship FOREIGN KEY(deck_id, ship_id) REFERENCES "Deck"(deck_id, ship_id),
    CONSTRAINT ck_location_device_type CHECK (device_type IN ('BLE_BEACON','BLE_RECEIVER','GPS','OTHER')),
    CONSTRAINT ck_location_device_position CHECK (device_type NOT IN ('BLE_BEACON','BLE_RECEIVER') OR (model_position_x IS NOT NULL AND model_position_y IS NOT NULL AND model_position_z IS NOT NULL)),
    CONSTRAINT ck_location_device_tx_power CHECK (tx_power_dbm BETWEEN -100 AND -20),
    CONSTRAINT ck_location_device_path_loss CHECK (path_loss_exponent BETWEEN 1 AND 5),
    CONSTRAINT ck_location_device_scale CHECK (model_units_per_meter BETWEEN 0.1 AND 10)
);
CREATE INDEX IF NOT EXISTS idx_location_device_ship_status ON "LocationDevice"(ship_id,status);

CREATE TABLE IF NOT EXISTS "LocationRecord" (
    location_record_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    location_device_id uuid NOT NULL,
    ship_id uuid NOT NULL,
    deck_id uuid,
    cabin_id uuid,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    latitude numeric(10,7),
    longitude numeric(10,7),
    zone_key varchar(200),
    accuracy_meters numeric(8,2),
    rssi_dbm integer,
    estimated_distance_meters numeric(8,2),
    ble_identifier varchar(150),
    CONSTRAINT fk_location_record_device_ship FOREIGN KEY(location_device_id, ship_id) REFERENCES "LocationDevice"(location_device_id, ship_id),
    CONSTRAINT ck_location_record_rssi CHECK (rssi_dbm IS NULL OR rssi_dbm BETWEEN -127 AND -1),
    CONSTRAINT ck_location_record_distance CHECK (estimated_distance_meters IS NULL OR estimated_distance_meters >= 0)
);
CREATE INDEX IF NOT EXISTS idx_location_record_device_time ON "LocationRecord"(location_device_id,recorded_at);
CREATE INDEX IF NOT EXISTS idx_location_record_ship_time ON "LocationRecord"(ship_id,recorded_at);
CREATE INDEX IF NOT EXISTS idx_location_record_ble_latest ON "LocationRecord"(location_device_id,ble_identifier,recorded_at DESC);

CREATE TABLE IF NOT EXISTS "PassengerBleIdentity" (
    passenger_ble_identity_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id),
    booking_passenger_id uuid NOT NULL REFERENCES "BookingPassenger"(booking_passenger_id),
    ble_identifier varchar(150) NOT NULL,
    active_ble_identifier varchar(150),
    active_booking_passenger_id uuid,
    status varchar(20) NOT NULL,
    device_name varchar(80),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by varchar(100),
    revoked_at timestamptz,
    revoked_by varchar(100),
    revocation_reason varchar(500),
    CONSTRAINT uk_passenger_ble_active_tag UNIQUE(cruise_id, active_ble_identifier),
    CONSTRAINT uk_passenger_ble_active_passenger UNIQUE(cruise_id, active_booking_passenger_id),
    CONSTRAINT ck_passenger_ble_status CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT ck_passenger_ble_active_fields CHECK ((status = 'ACTIVE' AND active_ble_identifier IS NOT NULL AND active_booking_passenger_id IS NOT NULL) OR (status = 'REVOKED' AND active_ble_identifier IS NULL AND active_booking_passenger_id IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_passenger_ble_history ON "PassengerBleIdentity"(cruise_id,booking_passenger_id,assigned_at DESC);

-- Expand the persisted permission allowlist for the new configuration and BLE operations.
ALTER TABLE "RolePermissionMapping" DROP CONSTRAINT IF EXISTS "RolePermissionMapping_permission_check";
ALTER TABLE "RolePermissionMapping" ADD CONSTRAINT "RolePermissionMapping_permission_check" CHECK (
    (permission)::text = ANY (ARRAY[
        'SHIP_CREATE', 'SHIP_READ', 'SHIP_UPDATE', 'SHIP_DELETE', 'SHIP_STATUS_CHANGE', 'SHIP_SCHEDULE_CHECK',
        'SHIP_CAPACITY_SUMMARY', 'SHIP_AFFECTED_CRUISES', 'SHIP_CURRENT_CRUISE', 'SHIP_VALIDATE', 'SHIP_READINESS', 'SHIP_MODEL_CREATE',
        'SHIP_MODEL_READ', 'SHIP_MODEL_UPDATE', 'SHIP_MODEL_DELETE', 'SHIP_MODEL_PUBLISH', 'SHIP_MODEL_OBJECT_CREATE', 'SHIP_MODEL_OBJECT_UPDATE',
        'SHIP_MODEL_OBJECT_DELETE', 'SHIP_CONFIGURATION_READ', 'SHIP_CONFIGURATION_VALIDATE', 'BLE_ANCHOR_CREATE', 'BLE_ANCHOR_READ', 'BLE_ANCHOR_UPDATE',
        'BLE_ANCHOR_DELETE', 'BLE_ANCHOR_ACTIVATE', 'BLE_ANCHOR_DEACTIVATE', 'BLE_ANCHOR_RETIRE', 'BLE_ANCHOR_INSTALL', 'BLE_ANCHOR_VALIDATE',
        'BLE_TELEMETRY_RECEIVE', 'BLE_TELEMETRY_READ', 'PASSENGER_BLE_ASSIGN', 'PASSENGER_BLE_READ', 'PASSENGER_BLE_REVOKE', 'PASSENGER_BLE_HISTORY',
        'DECK_CREATE', 'DECK_READ', 'DECK_UPDATE', 'DECK_DELETE', 'DECK_CABINS_VIEW', 'DECK_VALIDATE',
        'CABIN_CREATE', 'CABIN_READ', 'CABIN_UPDATE', 'CABIN_DELETE', 'CABIN_SEARCH', 'CABIN_STATUS_CHANGE',
        'CABIN_BLOCK', 'CABIN_RELEASE', 'CABIN_ASSIGNMENTS_VIEW', 'CABIN_AVAILABILITY', 'CABIN_VALIDATE', 'CRUISE_CREATE',
        'CRUISE_READ', 'CRUISE_UPDATE_DRAFT', 'CRUISE_DELETE_DRAFT', 'CRUISE_ASSIGN_SHIP', 'CRUISE_CHANGE_SHIP', 'CRUISE_UNASSIGN_SHIP',
        'CRUISE_VALIDATE', 'CRUISE_PUBLISH', 'CRUISE_START', 'CRUISE_COMPLETE', 'CRUISE_CANCEL', 'CRUISE_STATUS_CHANGE',
        'ITINERARY_CREATE', 'ITINERARY_READ', 'ITINERARY_UPDATE_DRAFT', 'ITINERARY_DELETE_DRAFT', 'ITINERARY_VALIDATE', 'ITINERARY_SUBMIT',
        'ITINERARY_APPROVE', 'ITINERARY_REJECT', 'ITINERARY_APPLY_CHANGE', 'ITINERARY_PUBLISH', 'ITINERARY_CANCEL', 'ITINERARY_DAYS_MANAGE',
        'ITINERARY_DAY_CREATE', 'ITINERARY_DAY_READ', 'ITINERARY_DAY_UPDATE', 'ITINERARY_DAY_DELETE', 'ITINERARY_DAY_TYPE_SET', 'ITINERARY_DAY_TIMEZONE_SET',
        'CRUISE_STOP_ADD', 'CRUISE_STOP_READ', 'CRUISE_STOP_UPDATE', 'CRUISE_STOP_REMOVE_DRAFT', 'CRUISE_STOP_REORDER', 'CRUISE_STOP_ALL_ABOARD',
        'CRUISE_STOP_ACTUAL_ARRIVAL', 'CRUISE_STOP_ACTUAL_DEPARTURE', 'CRUISE_STOP_OMIT', 'CRUISE_STOP_SUBSTITUTE', 'CRUISE_STOP_VALIDATE', 'CRUISE_STOP_TIME_MANAGE',
        'PORT_CREATE', 'PORT_READ', 'PORT_UPDATE', 'PORT_DELETE_UNUSED', 'PORT_ACTIVATE', 'PORT_DEACTIVATE',
        'PORT_VALIDATE', 'DESTINATION_CREATE', 'DESTINATION_READ', 'DESTINATION_UPDATE', 'DESTINATION_DELETE_UNUSED', 'DESTINATION_PORTS_VIEW',
        'DESTINATION_VALIDATE', 'BOOKING_CREATE', 'BOOKING_READ', 'BOOKING_UPDATE', 'BOOKING_CANCEL', 'BOOKING_CONTRACT_MANAGE',
        'BOOKING_CABIN_ASSIGN', 'CHECKIN_CREATE', 'CHECKIN_READ', 'CHECKIN_UPDATE', 'CHECKIN_STEP_UPDATE', 'CHECKIN_IDENTITY_VERIFY',
        'CHECKIN_DOCUMENT_VERIFY', 'CHECKIN_PAYMENT_VERIFY', 'CHECKIN_BOARDING', 'CHECKOUT_PROCESS', 'PAYMENT_PROCESS', 'PAYMENT_REFUND',
        'PAYMENT_REPORT', 'INVOICE_GENERATE', 'SETTLEMENT_PROCESS', 'ACTIVITY_CREATE', 'ACTIVITY_READ', 'ACTIVITY_UPDATE',
        'ACTIVITY_REGISTRATION', 'ACTIVITY_ATTENDANCE', 'EXCURSION_CREATE', 'EXCURSION_READ', 'EXCURSION_UPDATE', 'EXCURSION_DELETE_DRAFT',
        'EXCURSION_STATUS_CHANGE', 'EXCURSION_VALIDATE', 'EXCURSION_BOOKING', 'SERVICE_CREATE', 'SERVICE_READ', 'SERVICE_UPDATE',
        'SERVICE_DELETE_UNUSED', 'SERVICE_ACTIVATE', 'SERVICE_SUSPEND', 'SERVICE_REQUEST_CREATE', 'SERVICE_REQUEST_READ', 'SERVICE_REQUEST_ASSIGN',
        'SERVICE_REQUEST_EXECUTE', 'PRODUCT_MANAGE', 'PRODUCT_SALE', 'FEEDBACK_SUBMIT', 'FEEDBACK_READ', 'FEEDBACK_REVIEW',
        'TOUR_PACKAGE_MANAGE', 'TOUR_PACKAGE_ASSIGN', 'PACKAGE_COMPONENT_MANAGE', 'ACTIVITY_LIFECYCLE', 'REGISTRATION_MANAGE', 'PARTICIPATION_ATTENDANCE',
        'SUPPLIER_CREATE', 'SUPPLIER_READ', 'SUPPLIER_UPDATE', 'SUPPLIER_DELETE', 'SUPPLIER_STATUS_CHANGE', 'SUPPLIER_VERIFY',
        'SUPPLIER_VALIDATE', 'TOUR_GROUP_CREATE', 'TOUR_GROUP_READ', 'TOUR_GROUP_UPDATE', 'TOUR_GROUP_GUIDE_ASSIGN', 'TOUR_GROUP_STATUS_CHANGE',
        'TOUR_GROUP_VALIDATE', 'TOUR_GROUP_PASSENGER_ASSIGN', 'TOUR_GROUP_PASSENGER_READ', 'TOUR_GROUP_PASSENGER_MOVE', 'TOUR_GROUP_PASSENGER_REMOVE', 'TOUR_GROUP_PASSENGER_ATTENDANCE',
        'TOUR_GROUP_PASSENGER_VALIDATE', 'NOTIFICATION_SEND', 'NOTIFICATION_READ', 'USER_CREATE', 'USER_READ', 'USER_UPDATE',
        'USER_ROLE_ASSIGN', 'USER_STATUS_CHANGE', 'REPORT_GENERATE', 'REPORT_EXPORT', 'OPERATIONAL_ISSUE_CREATE', 'OPERATIONAL_ISSUE_READ',
        'OPERATIONAL_ISSUE_UPDATE', 'OPERATIONAL_ISSUE_RESOLVE', 'OPERATIONAL_ISSUE_ESCALATE', 'OPERATIONAL_ISSUE_ASSIGN', 'REGULATION_CREATE', 'REGULATION_READ',
        'REGULATION_UPDATE', 'REGULATION_DELETE', 'REGULATION_ACTIVATE', 'REGULATION_DEACTIVATE', 'REGULATION_VALIDATE', 'SHIP_REGULATION_ASSIGN',
        'SHIP_REGULATION_READ', 'SHIP_REGULATION_REMOVE', 'CRUISE_REGULATION_ASSIGN', 'CRUISE_REGULATION_READ', 'CRUISE_REGULATION_REMOVE', 'ITINERARY_CHANGE_RECORD',
        'ITINERARY_CHANGE_READ'
    ]::text[])
);
