CREATE TABLE "DepartureSaleTerms" (
    cruise_id uuid PRIMARY KEY REFERENCES "Cruise"(cruise_id),
    revision bigint NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','RETURNED')),
    terms text NOT NULL,
    submitted_hash varchar(64),
    maker_ids uuid[] NOT NULL,
    submitted_by uuid REFERENCES "User"(user_id),
    submitted_at timestamptz,
    reviewed_by uuid REFERENCES "User"(user_id),
    reviewed_at timestamptz
);
ALTER TABLE "Cruise" ADD COLUMN sale_approval_fingerprint varchar(64);
