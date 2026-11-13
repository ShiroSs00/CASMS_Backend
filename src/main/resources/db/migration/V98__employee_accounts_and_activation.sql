ALTER TABLE "UserRole" DROP CONSTRAINT "UserRole_role_check";
ALTER TABLE "UserRole" ADD CONSTRAINT "UserRole_role_check" CHECK (role::text IN (
    'ADMIN', 'CRUISE_MANAGER', 'SHIP_MANAGER', 'OPERATIONS_MANAGER', 'BOOKING_AGENT',
    'ONBOARD_STAFF', 'FINANCE_OFFICER', 'POS', 'PASSENGER', 'ACTIVITY_SERVICE_MANAGER', 'HR_MANAGER'
));
ALTER TABLE "RolePermission" DROP CONSTRAINT "RolePermission_role_check";
ALTER TABLE "RolePermission" ADD CONSTRAINT "RolePermission_role_check" CHECK (role::text IN (
    'ADMIN', 'CRUISE_MANAGER', 'SHIP_MANAGER', 'OPERATIONS_MANAGER', 'BOOKING_AGENT',
    'ONBOARD_STAFF', 'FINANCE_OFFICER', 'POS', 'PASSENGER', 'ACTIVITY_SERVICE_MANAGER', 'HR_MANAGER'
));

CREATE TABLE "Employee" (
    employee_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL UNIQUE REFERENCES "User"(user_id),
    employee_code varchar(50) NOT NULL UNIQUE,
    full_name varchar(180) NOT NULL,
    phone varchar(30),
    gender varchar(50),
    date_of_birth date,
    address varchar(350),
    department varchar(150),
    position varchar(150),
    status varchar(24) NOT NULL CHECK (status IN ('PENDING_ACTIVATION','ACTIVE','INACTIVE')),
    created_by uuid NOT NULL REFERENCES "User"(user_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "EmployeeActivationOutbox" (
    activation_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id uuid NOT NULL UNIQUE REFERENCES "Employee"(employee_id),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '7 days'),
    consumed_at timestamptz,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    sent_at timestamptz,
    last_retry_at timestamptz,
    last_retry_by uuid REFERENCES "User"(user_id)
);

CREATE INDEX employee_activation_pending ON "EmployeeActivationOutbox" (next_attempt_at, activation_id)
    WHERE sent_at IS NULL AND consumed_at IS NULL AND attempts < 5;
