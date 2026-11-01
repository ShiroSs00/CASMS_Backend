-- Additive only. No hours/closures seeded, no legacy backfill. Not applied by this task.
CREATE TABLE "PortCallWindow" (
 window_id uuid PRIMARY KEY, window_type varchar(255) NOT NULL CHECK (window_type IN ('ARRIVAL','DEPARTURE')),
 local_start_time time NOT NULL, local_end_time time NOT NULL, overnight_allowed boolean, max_stay_minutes integer CHECK(max_stay_minutes > 0),
 port_id uuid NOT NULL REFERENCES "Port"(port_id), valid_from date NOT NULL, valid_to date,
 source_type varchar(255) NOT NULL CHECK (source_type IN ('PORT_AUTHORITY','PORT_AGENT')), source_reference varchar(2000) NOT NULL,
 source_effective_date date, verification_status varchar(255) NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED','VERIFIED')),
 lifecycle_status varchar(255) NOT NULL DEFAULT 'INACTIVE' CHECK (lifecycle_status IN ('ACTIVE','INACTIVE')),
 verified_by uuid REFERENCES "User"(user_id), verified_at timestamptz, verified_time_zone_id varchar(255),
 revision bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK (valid_to IS NULL OR valid_to >= valid_from), CHECK(local_end_time > local_start_time)
);
CREATE TABLE port_call_window_weekday (window_id uuid NOT NULL REFERENCES "PortCallWindow"(window_id), weekday varchar(255) NOT NULL, PRIMARY KEY(window_id,weekday));
CREATE TABLE "PortCallWindowException" (
 exception_id uuid PRIMARY KEY, exception_type varchar(255) NOT NULL DEFAULT 'CLOSURE' CHECK(exception_type='CLOSURE'), reason varchar(2000) NOT NULL,
 port_id uuid NOT NULL REFERENCES "Port"(port_id), valid_from date NOT NULL, valid_to date,
 source_type varchar(255) NOT NULL CHECK (source_type IN ('PORT_AUTHORITY','PORT_AGENT')), source_reference varchar(2000) NOT NULL,
 source_effective_date date, verification_status varchar(255) NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED','VERIFIED')),
 lifecycle_status varchar(255) NOT NULL DEFAULT 'INACTIVE' CHECK (lifecycle_status IN ('ACTIVE','INACTIVE')),
 verified_by uuid REFERENCES "User"(user_id), verified_at timestamptz, verified_time_zone_id varchar(255),
 revision bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK (valid_to IS NULL OR valid_to >= valid_from), CHECK(valid_to IS NOT NULL)
);
CREATE INDEX port_call_window_port_idx ON "PortCallWindow"(port_id,valid_from,valid_to);
CREATE INDEX port_call_closure_port_idx ON "PortCallWindowException"(port_id,valid_from,valid_to);
