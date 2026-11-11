CREATE TABLE "CruiseSaleTermsRequest" (
    cruise_id UUID PRIMARY KEY REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES "User"(user_id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
