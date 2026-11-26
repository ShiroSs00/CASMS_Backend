ALTER TABLE "ImpactNotificationOutbox"
    ADD COLUMN notification_id uuid REFERENCES "Notification"(notification_id),
    ADD CONSTRAINT uk_impact_outbox_notification UNIQUE (notification_id);
