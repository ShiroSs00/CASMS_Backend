-- Forward-only: ownership of professional data stays in the existing section owner_role.
ALTER TABLE "Cruise" ADD COLUMN preparation_coordinator_id uuid REFERENCES "User"(user_id);
CREATE INDEX cruise_preparation_coordinator_idx ON "Cruise"(preparation_coordinator_id);
CREATE TABLE "CruisePreparationEvent" (
 event_id uuid PRIMARY KEY,
 cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id),
 section_code varchar(50),
 revision bigint NOT NULL,
 action varchar(40) NOT NULL,
 actor_id uuid NOT NULL REFERENCES "User"(user_id),
 occurred_at timestamptz NOT NULL DEFAULT now(),
 reason text,
 snapshot jsonb NOT NULL
);
CREATE INDEX cruise_preparation_event_idx ON "CruisePreparationEvent"(cruise_id,occurred_at,event_id);
