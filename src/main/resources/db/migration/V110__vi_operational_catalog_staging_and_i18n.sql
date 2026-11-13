-- CASMS-75 branch (base f575a4ed15f26c8ee070ba821dcd04d5425f1f74).
-- Additive, no production data activated. Version intentionally > main V101.
CREATE TABLE "CommercialPolicyCatalog" (
  policy_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_code varchar(50) NOT NULL UNIQUE,
  policy_name varchar(200) NOT NULL,
  policy_group varchar(100) NOT NULL,
  description text NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','APPROVED','RETIRED')),
  effective_from timestamptz,
  approved_by uuid REFERENCES "User"(user_id),
  approved_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status <> 'APPROVED' OR
         (approved_by IS NOT NULL AND approved_at IS NOT NULL AND effective_from IS NOT NULL))
);
CREATE TABLE "OperationalSeedEmployee" (
  employee_code varchar(50) PRIMARY KEY,
  full_name varchar(180) NOT NULL,
  department varchar(150) NOT NULL,
  position varchar(150) NOT NULL,
  proposed_role varchar(50) NOT NULL,
  proposed_email varchar(100) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'DRAFT'
    CHECK(status IN ('DRAFT','IMPORTED','REJECTED')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE "OperationalSeedTour" (
  tour_code varchar(50) PRIMARY KEY,
  tour_name varchar(250) NOT NULL,
  port_region varchar(150) NOT NULL,
  duration_minutes int NOT NULL CHECK(duration_minutes > 0),
  capacity int NOT NULL CHECK(capacity > 0),
  reference_price_vnd numeric(19,4) NOT NULL CHECK(reference_price_vnd >= 0),
  status varchar(20) NOT NULL DEFAULT 'DRAFT'
    CHECK(status IN ('DRAFT','VERIFIED','REJECTED')),
  verified_port_id uuid REFERENCES "Port"(port_id),
  verified_supplier_id uuid REFERENCES "Supplier"(supplier_id),
  reviewed_by uuid REFERENCES "User"(user_id),
  reviewed_at timestamptz,
  port_evidence_url text,
  supplier_evidence_url text,
  review_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((reviewed_by IS NULL) = (reviewed_at IS NULL)),
  CHECK(status <> 'VERIFIED' OR (
    verified_port_id IS NOT NULL AND verified_supplier_id IS NOT NULL
    AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
    AND port_evidence_url IS NOT NULL AND supplier_evidence_url IS NOT NULL
    AND review_note IS NOT NULL AND char_length(btrim(review_note)) BETWEEN 12 AND 2000
    AND port_evidence_url ~* '^https?://[^ ]+'
    AND supplier_evidence_url ~* '^https?://[^ ]+'))
);
CREATE INDEX operational_seed_tour_status ON "OperationalSeedTour"(status,port_region,tour_code);
CREATE TABLE "CatalogTranslation" (
  entity_kind varchar(30) NOT NULL CHECK (entity_kind IN
     ('SERVICE_CATEGORY','SERVICE','ACTIVITY','PACKAGE','POLICY','SHORE_TOUR')),
  entity_code varchar(50) NOT NULL,
  locale varchar(16) NOT NULL CHECK (locale ~ '^[a-z]{2}(-[A-Z]{2})?$'),
  display_name varchar(250) NOT NULL,
  description text,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(entity_kind,entity_code,locale)
);
CREATE INDEX catalog_translation_locale_idx ON "CatalogTranslation"(locale,entity_kind);
-- Source-of-truth Vietnamese names remain in domain tables. Translations are overrides.
