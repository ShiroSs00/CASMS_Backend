ALTER TABLE "Service" ADD COLUMN operating_periods jsonb;

ALTER TABLE "Notification" ADD COLUMN port_call_alert_key varchar(120);
CREATE UNIQUE INDEX uk_notification_port_call_alert
    ON "Notification"(recipient_user_id, port_call_alert_key);
