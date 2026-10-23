CREATE TABLE "ModelImportJob" (
    model_import_job_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ship_model_id uuid NOT NULL REFERENCES "ShipModel"(ship_model_id),
    status varchar(20) NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    requested_by varchar(120),
    total_objects integer NOT NULL DEFAULT 0 CHECK (total_objects >= 0),
    decks_imported integer NOT NULL DEFAULT 0 CHECK (decks_imported >= 0),
    cabins_imported integer NOT NULL DEFAULT 0 CHECK (cabins_imported >= 0),
    objects_succeeded integer NOT NULL DEFAULT 0 CHECK (objects_succeeded >= 0),
    objects_failed integer NOT NULL DEFAULT 0 CHECK (objects_failed >= 0),
    objects_skipped integer NOT NULL DEFAULT 0 CHECK (objects_skipped >= 0),
    error_message varchar(1000),
    CONSTRAINT ck_model_import_job_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED'))
);

CREATE INDEX idx_model_import_job_model_created
    ON "ModelImportJob"(ship_model_id, started_at DESC);

CREATE TABLE "ModelImportIssue" (
    model_import_issue_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    model_import_job_id uuid NOT NULL REFERENCES "ModelImportJob"(model_import_job_id) ON DELETE CASCADE,
    object_identifier varchar(200),
    object_type varchar(30),
    error_code varchar(80) NOT NULL,
    message varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_model_import_issue_job
    ON "ModelImportIssue"(model_import_job_id, created_at);
