-- Additive only: source finalization, plan approval, preparation, Ready and sale remain separate.
ALTER TABLE "Cruise" ADD COLUMN itinerary_mode varchar(12) NOT NULL DEFAULT 'FIXED'
    CHECK (itinerary_mode IN ('FIXED','CUSTOM'));
ALTER TABLE "Cruise" ADD COLUMN planned_scope varchar(20) CHECK (planned_scope IN ('DOMESTIC','INTERNATIONAL'));
ALTER TABLE "Cruise" ADD COLUMN booking_opens_at timestamptz;
ALTER TABLE "Cruise" ADD COLUMN booking_closes_at timestamptz;
ALTER TABLE "Cruise" ADD COLUMN guidance_opens_at timestamptz;
ALTER TABLE "Cruise" ADD COLUMN guidance_closes_at timestamptz;
ALTER TABLE "Cruise" ADD CONSTRAINT cruise_booking_window CHECK
 (booking_opens_at IS NULL OR booking_closes_at IS NULL OR (booking_opens_at < booking_closes_at AND booking_closes_at < departure_datetime));
ALTER TABLE "Cruise" ADD CONSTRAINT cruise_guidance_window CHECK
 (guidance_opens_at IS NULL OR guidance_closes_at IS NULL OR (guidance_opens_at < guidance_closes_at AND guidance_closes_at <= departure_datetime));
CREATE TABLE "CruisePlanReview" (
 cruise_id uuid PRIMARY KEY REFERENCES "Cruise"(cruise_id),
 revision bigint NOT NULL DEFAULT 1,
 status varchar(24) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','REVISION_REQUESTED','WITHDRAWN')),
 snapshot jsonb, fingerprint varchar(64),
 submitted_by uuid REFERENCES "User"(user_id), submitted_at timestamptz,
 reviewed_by uuid REFERENCES "User"(user_id), reviewed_at timestamptz, reason text,
 ready_by uuid REFERENCES "User"(user_id), ready_at timestamptz, ready_fingerprint varchar(64), ready_evidence jsonb
);
CREATE TABLE "CruisePlanEvent" (
 event_id uuid PRIMARY KEY, cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id),
 revision bigint NOT NULL, action varchar(32) NOT NULL, actor_id uuid NOT NULL REFERENCES "User"(user_id),
 occurred_at timestamptz NOT NULL DEFAULT now(), reason text, snapshot jsonb NOT NULL
);
CREATE INDEX cruise_plan_queue_idx ON "CruisePlanReview" (submitted_at) WHERE status='SUBMITTED';
ALTER TABLE "GuidanceSession" ADD COLUMN content text;
ALTER TABLE "GuidanceSession" ADD COLUMN published_at timestamptz;
ALTER TABLE "GuidanceSession" ADD COLUMN published_by uuid REFERENCES "User"(user_id);
ALTER TABLE "GuidanceSession" ADD COLUMN revision bigint NOT NULL DEFAULT 1;
