-- SOURCE ONLY. Never execute/apply in the coding session; no legacy backfill/default quota or unit.
-- Reuse the immutable purchased-benefit row as grant identity, Participation as activity fulfillment,
-- and the existing ServiceUsage table as service fulfillment. No parallel entitlement/reservation tables.
ALTER TABLE "FarePackageBenefit"
    ADD COLUMN usage_unit varchar(30),
    ADD COLUMN usage_policy varchar(20),
    ADD COLUMN service_unit_code varchar(30),
    ADD COLUMN requires_reservation boolean,
    ADD COLUMN usage_starts_at timestamptz,
    ADD COLUMN usage_ends_at timestamptz;
ALTER TABLE "FarePackageBenefit" ADD CONSTRAINT fare_benefit_usage_window_pair CHECK (
    (usage_starts_at IS NULL AND usage_ends_at IS NULL)
    OR (usage_starts_at IS NOT NULL AND usage_ends_at IS NOT NULL AND usage_starts_at < usage_ends_at));
ALTER TABLE "BookingFareBenefitSnapshot"
    ADD COLUMN usage_unit varchar(30),
    ADD COLUMN usage_policy varchar(20),
    ADD COLUMN service_unit_code varchar(30),
    ADD COLUMN requires_reservation boolean,
    ADD COLUMN usage_starts_at timestamptz,
    ADD COLUMN usage_ends_at timestamptz,
    ADD COLUMN entitlement_granted_at timestamptz;
ALTER TABLE "BookingFareBenefitSnapshot" ADD CONSTRAINT fare_snapshot_grant_inclusion
    CHECK (entitlement_granted_at IS NULL OR inclusion IN ('INCLUDED','LIMITED_INCLUDED'));
ALTER TABLE "BookingFareBenefitSnapshot" ADD CONSTRAINT fare_snapshot_usage_window_pair CHECK (
    (usage_starts_at IS NULL AND usage_ends_at IS NULL)
    OR (usage_starts_at IS NOT NULL AND usage_ends_at IS NOT NULL AND usage_starts_at < usage_ends_at));
ALTER TABLE "Registration" ADD COLUMN fare_benefit_snapshot_id uuid REFERENCES "BookingFareBenefitSnapshot"(snapshot_benefit_id);
CREATE INDEX registration_fare_grant ON "Registration"(fare_benefit_snapshot_id);
ALTER TABLE "ServiceRequest" ADD COLUMN fare_benefit_snapshot_id uuid REFERENCES "BookingFareBenefitSnapshot"(snapshot_benefit_id);
CREATE INDEX service_request_fare_grant ON "ServiceRequest"(fare_benefit_snapshot_id);
ALTER TABLE "ServiceUsage"
    ADD COLUMN fare_benefit_snapshot_id uuid REFERENCES "BookingFareBenefitSnapshot"(snapshot_benefit_id),
    ADD COLUMN unit_code_snapshot varchar(30),
    ADD COLUMN operation_identity varchar(100);
ALTER TABLE "ServiceUsage" ADD CONSTRAINT fare_service_usage_identity_required CHECK (
    fare_benefit_snapshot_id IS NULL OR (operation_identity IS NOT NULL AND unit_code_snapshot IS NOT NULL));
CREATE UNIQUE INDEX service_usage_fare_operation ON "ServiceUsage"(operation_identity) WHERE fare_benefit_snapshot_id IS NOT NULL;
CREATE UNIQUE INDEX service_usage_fare_request ON "ServiceUsage"(service_request_id) WHERE fare_benefit_snapshot_id IS NOT NULL;
CREATE INDEX service_usage_fare_grant ON "ServiceUsage"(fare_benefit_snapshot_id);
