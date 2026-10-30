-- Business assignments are separate from immutable 3D assets and catalogue definitions.
CREATE TABLE "AreaServiceAssignment" (
    assignment_id uuid PRIMARY KEY,
    area_object_id uuid NOT NULL REFERENCES "ShipModelObject"(ship_model_object_id),
    service_id uuid NOT NULL REFERENCES "Service"(service_id),
    permissions text[] NOT NULL DEFAULT '{}',
    enabled boolean NOT NULL DEFAULT true,
    UNIQUE(area_object_id, service_id),
    CHECK (permissions <@ ARRAY['SERVICE_READ','SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN',
        'SERVICE_REQUEST_EXECUTE','SERVICE_SALE']::text[])
);
CREATE TABLE "PosTerminal" (
    terminal_id uuid PRIMARY KEY,
    ship_id uuid NOT NULL REFERENCES "Ship"(ship_id),
    area_object_id uuid NOT NULL REFERENCES "ShipModelObject"(ship_model_object_id),
    terminal_code varchar(60) NOT NULL UNIQUE,
    terminal_name varchar(200) NOT NULL,
    secret_hash varchar(255) NOT NULL,
    permissions text[] NOT NULL DEFAULT '{}',
    enabled boolean NOT NULL DEFAULT true,
    CHECK (permissions <@ ARRAY['SERVICE_READ','SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN',
        'SERVICE_REQUEST_EXECUTE','SERVICE_SALE']::text[])
);
CREATE INDEX idx_pos_terminal_area ON "PosTerminal"(area_object_id);
CREATE TABLE "PosSession" (
    token_hash char(64) PRIMARY KEY,
    terminal_id uuid NOT NULL REFERENCES "PosTerminal"(terminal_id),
    user_id uuid NOT NULL REFERENCES "User"(user_id),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CHECK (expires_at > created_at)
);
CREATE INDEX idx_pos_session_terminal ON "PosSession"(terminal_id);
CREATE TABLE "PosSale" (
    sale_id uuid PRIMARY KEY,
    terminal_id uuid NOT NULL REFERENCES "PosTerminal"(terminal_id),
    area_object_id uuid NOT NULL REFERENCES "ShipModelObject"(ship_model_object_id),
    service_id uuid NOT NULL REFERENCES "Service"(service_id),
    service_request_id uuid NOT NULL UNIQUE REFERENCES "ServiceRequest"(service_request_id),
    onboard_account_id uuid NOT NULL REFERENCES "OnboardAccount"(onboard_account_id),
    booking_passenger_id uuid NOT NULL REFERENCES "BookingPassenger"(booking_passenger_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(19,4) NOT NULL CHECK (unit_price > 0),
    amount numeric(19,4) NOT NULL CHECK (amount > 0),
    currency char(3) NOT NULL,
    created_by_user_id uuid NOT NULL REFERENCES "User"(user_id),
    charge_id uuid NOT NULL UNIQUE REFERENCES "Charge"(charge_id),
    created_at timestamptz NOT NULL,
    reversed_at timestamptz,
    reversal_transaction_id uuid UNIQUE REFERENCES "OnboardTransaction"(transaction_id),
    idempotency_key varchar(100) NOT NULL,
    request_fingerprint char(64) NOT NULL,
    UNIQUE(terminal_id, idempotency_key),
    CHECK ((reversal_transaction_id IS NULL AND reversed_at IS NULL) OR (reversal_transaction_id IS NOT NULL AND reversed_at IS NOT NULL))
);
CREATE INDEX idx_pos_sale_account ON "PosSale"(onboard_account_id,created_at);
CREATE TABLE "OnboardAllowanceChange" (
    change_id uuid PRIMARY KEY,
    onboard_account_id uuid NOT NULL REFERENCES "OnboardAccount"(onboard_account_id),
    actor_user_id uuid NOT NULL REFERENCES "User"(user_id),
    allocated_amount numeric(19,4) NOT NULL CHECK (allocated_amount >= 0),
    member_limits_json text NOT NULL,
    recorded_at timestamptz NOT NULL
);
