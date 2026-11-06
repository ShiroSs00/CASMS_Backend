ALTER TABLE "Service"
    ADD COLUMN pre_cruise_usage_policy varchar(20) NOT NULL DEFAULT 'UNLIMITED',
    ADD COLUMN pre_cruise_usage_limit integer,
    ADD COLUMN usage_time_zone varchar(100) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    ADD CONSTRAINT service_usage_policy_check CHECK (
        (pre_cruise_usage_policy = 'UNLIMITED' AND pre_cruise_usage_limit IS NULL) OR
        (pre_cruise_usage_policy IN ('PER_DAY','PER_CRUISE') AND pre_cruise_usage_limit IS NOT NULL AND pre_cruise_usage_limit > 0));

ALTER TABLE "ServiceRequest"
    ADD COLUMN usage_mode varchar(20) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN usage_policy_snapshot varchar(20) NOT NULL DEFAULT 'UNLIMITED',
    ADD COLUMN usage_limit_snapshot integer,
    ADD COLUMN usage_time_zone_snapshot varchar(100) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    ADD CONSTRAINT service_request_usage_mode_check CHECK (usage_mode IN ('LEGACY','PRE_CRUISE','ONBOARD'));

CREATE INDEX service_request_quota_idx ON "ServiceRequest" (booking_passenger_id, service_id, usage_mode);
CREATE INDEX service_usage_request_time_idx ON "ServiceUsage" (service_request_id, used_at);

-- Existing timed activities remain operational occurrences. New catalog entries have no schedule.
ALTER TABLE "OnboardActivity"
    ALTER COLUMN start_time DROP NOT NULL,
    ALTER COLUMN end_time DROP NOT NULL,
    ADD COLUMN catalog_entry boolean NOT NULL DEFAULT false,
    ADD COLUMN source_activity_id uuid REFERENCES "OnboardActivity"(onboard_activity_id),
    ADD CONSTRAINT activity_catalog_schedule_check CHECK (
        (catalog_entry AND start_time IS NULL AND end_time IS NULL AND cruise_id IS NULL
            AND deck_id IS NULL AND cabin_id IS NULL AND location IS NULL) OR
        (NOT catalog_entry AND start_time IS NOT NULL AND end_time IS NOT NULL));
CREATE INDEX activity_catalog_idx ON "OnboardActivity" (catalog_entry);

ALTER TABLE "OnboardActivity" ADD COLUMN responsible_user_id uuid REFERENCES "User"(user_id);
ALTER TABLE "ServiceSchedule" ADD COLUMN responsible_user_id uuid REFERENCES "User"(user_id);

CREATE UNIQUE INDEX service_usage_completion_identity ON "ServiceUsage" (operation_identity)
    WHERE operation_identity LIKE 'SERVICE_COMPLETE:%';
