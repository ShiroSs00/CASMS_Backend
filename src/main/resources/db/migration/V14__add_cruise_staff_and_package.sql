-- Migration V14: Add CruiseStaff and CruiseTourPackage tables to support crew assignment and tour package linking
CREATE TABLE IF NOT EXISTS "CruiseStaff" (
    cruise_staff_id uuid PRIMARY KEY,
    cruise_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role_in_cruise varchar(100) NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status varchar(50) NOT NULL DEFAULT 'ACTIVE',
    notes text,
    CONSTRAINT fk_cruise_staff_cruise FOREIGN KEY (cruise_id) REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    CONSTRAINT fk_cruise_staff_user FOREIGN KEY (user_id) REFERENCES "User"(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_cruise_staff_user UNIQUE (cruise_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_cruise_staff_cruise_id ON "CruiseStaff"(cruise_id);
CREATE INDEX IF NOT EXISTS idx_cruise_staff_user_id ON "CruiseStaff"(user_id);

CREATE TABLE IF NOT EXISTS "CruiseTourPackage" (
    cruise_tour_package_id uuid PRIMARY KEY,
    cruise_id uuid NOT NULL,
    tour_package_id uuid NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cruise_tour_package_cruise FOREIGN KEY (cruise_id) REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    CONSTRAINT fk_cruise_tour_package_package FOREIGN KEY (tour_package_id) REFERENCES "TourPackage"(tour_package_id) ON DELETE CASCADE,
    CONSTRAINT uq_cruise_tour_package UNIQUE (cruise_id, tour_package_id)
);

CREATE INDEX IF NOT EXISTS idx_cruise_tour_package_cruise_id ON "CruiseTourPackage"(cruise_id);
