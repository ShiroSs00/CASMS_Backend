CREATE TABLE "EmployeeImportBatch" (
    batch_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by uuid NOT NULL REFERENCES "User"(user_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    confirmed_by uuid REFERENCES "User"(user_id),
    confirmed_at timestamptz
);

CREATE TABLE "EmployeeImportRow" (
    batch_id uuid NOT NULL REFERENCES "EmployeeImportBatch"(batch_id),
    row_number integer NOT NULL CHECK (row_number > 1),
    payload jsonb,
    validation_error varchar(200),
    result varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (result IN ('PENDING','INVALID','CREATED','FAILED')),
    result_code varchar(200),
    employee_id uuid REFERENCES "Employee"(employee_id),
    processed_at timestamptz,
    PRIMARY KEY (batch_id, row_number),
    CHECK ((result = 'CREATED') = (employee_id IS NOT NULL))
);
