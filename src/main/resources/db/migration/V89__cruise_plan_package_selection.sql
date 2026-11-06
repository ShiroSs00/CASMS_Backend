CREATE TABLE "CruisePlanPackageSelection" (
    cruise_id uuid PRIMARY KEY REFERENCES "Cruise"(cruise_id),
    revision bigint NOT NULL CHECK (revision >= 0),
    changed_by uuid NOT NULL REFERENCES "User"(user_id),
    changed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "CruisePlanPackageSelectionItem" (
    cruise_id uuid NOT NULL REFERENCES "CruisePlanPackageSelection"(cruise_id),
    offer_id uuid NOT NULL,
    price_revision_id uuid NOT NULL,
    template_version_id uuid NOT NULL,
    offer_revision bigint NOT NULL,
    commercial_revision bigint NOT NULL,
    PRIMARY KEY (cruise_id, offer_id),
    FOREIGN KEY (price_revision_id, offer_id, template_version_id)
        REFERENCES "DepartureFarePackagePrice"(price_revision_id, offer_id, template_version_id)
);
