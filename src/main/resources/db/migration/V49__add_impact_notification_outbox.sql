-- V49__add_impact_notification_outbox.sql
-- BE-19: Reliable Impact Notification Outbox for Cruise Schedule/Ship/Sale changes

CREATE TABLE "ImpactNotificationOutbox" (
    "outbox_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "cruise_id" UUID NOT NULL REFERENCES "Cruise"("cruise_id") ON DELETE CASCADE,
    "task_id" UUID REFERENCES "CruiseImpactTask"("task_id") ON DELETE SET NULL,
    "booking_id" UUID NOT NULL REFERENCES "Booking"("booking_id") ON DELETE CASCADE,
    "recipient_user_id" UUID NOT NULL REFERENCES "User"("user_id") ON DELETE CASCADE,
    "event_identity" VARCHAR(100) NOT NULL,
    "notification_type" VARCHAR(50) NOT NULL,
    "title" VARCHAR(255) NOT NULL,
    "content" TEXT NOT NULL,
    "status" VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    "sent_at" TIMESTAMP WITH TIME ZONE,
    "acknowledged_at" TIMESTAMP WITH TIME ZONE,
    "retry_count" INT NOT NULL DEFAULT 0,
    "failure_reason" VARCHAR(500),
    "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_impact_notification_outbox_event_booking_type" UNIQUE ("event_identity", "booking_id", "notification_type")
);

CREATE INDEX "idx_impact_outbox_cruise" ON "ImpactNotificationOutbox"("cruise_id");
CREATE INDEX "idx_impact_outbox_recipient" ON "ImpactNotificationOutbox"("recipient_user_id");
CREATE INDEX "idx_impact_outbox_status" ON "ImpactNotificationOutbox"("status");

COMMENT ON TABLE "ImpactNotificationOutbox" IS 'Hàng đợi và lịch sử gửi thông báo tác động tới khách hàng khi có thay đổi lịch/tàu/dừng bán (BE-19)';
COMMENT ON COLUMN "ImpactNotificationOutbox"."status" IS 'Trạng thái phát thông báo: PENDING, SENT, FAILED, ACK';
