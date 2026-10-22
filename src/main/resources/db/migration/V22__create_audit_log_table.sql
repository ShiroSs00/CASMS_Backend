-- AuditLog is already an application entity; add its missing persistent table additively.
CREATE TABLE IF NOT EXISTS "audit_log" (
  "audit_id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "actor_user_id" uuid REFERENCES "User"("user_id"),
  "action" varchar(100) NOT NULL,
  "entity_type" varchar(100),
  "entity_id" uuid,
  "result" varchar(50),
  "detail" text,
  "occurred_at" timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS "audit_log_occurred_at_idx" ON "audit_log" ("occurred_at");
CREATE INDEX IF NOT EXISTS "audit_log_actor_occurred_at_idx" ON "audit_log" ("actor_user_id", "occurred_at");
