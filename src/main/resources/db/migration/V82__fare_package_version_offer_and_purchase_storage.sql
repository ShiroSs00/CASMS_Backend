-- SOURCE ONLY: additive Fare Package storage. Do not apply during Coding/Integration.
-- No legacy TourPackage reinterpretation, data backfill, grants or sample records.
CREATE TABLE "FarePackageTemplate" (
    template_id uuid PRIMARY KEY,
    template_code varchar(50) NOT NULL UNIQUE,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    revision bigint NOT NULL DEFAULT 0
);
CREATE TABLE "FarePackageTemplateVersion" (
    template_version_id uuid PRIMARY KEY,
    template_id uuid NOT NULL REFERENCES "FarePackageTemplate"(template_id),
    version_number integer NOT NULL CHECK (version_number > 0),
    name varchar(200) NOT NULL,
    description text,
    logical_categories jsonb NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','SUPERSEDED')),
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    published_by uuid,
    UNIQUE (template_id,version_number)
);
CREATE UNIQUE INDEX fare_template_one_published ON "FarePackageTemplateVersion"(template_id) WHERE status='PUBLISHED';
CREATE TABLE "FarePackageBenefit" (
    benefit_id uuid PRIMARY KEY,
    template_version_id uuid NOT NULL REFERENCES "FarePackageTemplateVersion"(template_version_id),
    inclusion varchar(25) NOT NULL CHECK (inclusion IN ('INCLUDED','LIMITED_INCLUDED','OPTIONAL','NOT_INCLUDED')),
    onboard_activity_id uuid REFERENCES "OnboardActivity"(onboard_activity_id),
    shore_excursion_id uuid REFERENCES "ShoreExcursion"(shore_excursion_id),
    service_id uuid REFERENCES "Service"(service_id),
    quantity_limit integer CHECK (quantity_limit > 0),
    eligibility text NOT NULL,
    CHECK (num_nonnulls(onboard_activity_id,shore_excursion_id,service_id)=1),
    CHECK (inclusion <> 'LIMITED_INCLUDED' OR quantity_limit IS NOT NULL)
);
CREATE INDEX fare_benefit_version ON "FarePackageBenefit"(template_version_id);
CREATE TABLE "DepartureFarePackage" (
    offer_id uuid PRIMARY KEY,
    cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id),
    template_version_id uuid NOT NULL REFERENCES "FarePackageTemplateVersion"(template_version_id),
    ship_id uuid NOT NULL REFERENCES "Ship"(ship_id),
    logical_category varchar(100) NOT NULL,
    proposed_cabin_type varchar(30) NOT NULL,
    commercial_revision bigint NOT NULL DEFAULT 0,
    sale_enabled boolean NOT NULL DEFAULT false,
    sale_requested boolean NOT NULL DEFAULT false,
    sales_start timestamptz NOT NULL,
    sales_end timestamptz NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED')),
    published_at timestamptz,
    published_by uuid,
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    CHECK (sales_start < sales_end)
);
CREATE INDEX departure_fare_cruise ON "DepartureFarePackage"(cruise_id);
CREATE INDEX departure_fare_version ON "DepartureFarePackage"(template_version_id);
CREATE INDEX departure_fare_ship ON "DepartureFarePackage"(ship_id);
-- Explicit definition -> real occurrence binding; no copied activity/service catalog.
CREATE TABLE "DepartureFarePackageBenefit" (
    binding_id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES "DepartureFarePackage"(offer_id),
    benefit_id uuid NOT NULL REFERENCES "FarePackageBenefit"(benefit_id),
    onboard_activity_id uuid REFERENCES "OnboardActivity"(onboard_activity_id),
    shore_excursion_id uuid REFERENCES "ShoreExcursion"(shore_excursion_id),
    service_id uuid REFERENCES "Service"(service_id),
    CHECK (num_nonnulls(onboard_activity_id,shore_excursion_id,service_id)=1)
);
CREATE UNIQUE INDEX fare_binding_activity ON "DepartureFarePackageBenefit"(offer_id,benefit_id,onboard_activity_id) WHERE onboard_activity_id IS NOT NULL;
CREATE UNIQUE INDEX fare_binding_excursion ON "DepartureFarePackageBenefit"(offer_id,benefit_id,shore_excursion_id) WHERE shore_excursion_id IS NOT NULL;
CREATE UNIQUE INDEX fare_binding_service ON "DepartureFarePackageBenefit"(offer_id,benefit_id,service_id) WHERE service_id IS NOT NULL;
CREATE INDEX fare_binding_benefit ON "DepartureFarePackageBenefit"(benefit_id);
CREATE TABLE "DepartureFarePackagePrice" (
    price_revision_id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES "DepartureFarePackage"(offer_id),
    template_version_id uuid NOT NULL REFERENCES "FarePackageTemplateVersion"(template_version_id),
    revision_number integer NOT NULL CHECK (revision_number > 0),
    offer_commercial_revision bigint NOT NULL,
    amount numeric(19,4) NOT NULL CHECK (amount >= 0),
    currency_code varchar(3) NOT NULL CHECK (currency_code='USD'),
    price_unit varchar(10) NOT NULL CHECK (price_unit='PERSON'),
    status varchar(20) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','RETURNED')),
    revision bigint NOT NULL DEFAULT 0,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    submitted_by uuid,
    submitted_at timestamptz,
    reviewed_by uuid,
    reviewed_at timestamptz,
    review_reason text,
    UNIQUE (offer_id,revision_number),
    UNIQUE (price_revision_id,offer_id,template_version_id)
);
CREATE INDEX departure_fare_price_offer ON "DepartureFarePackagePrice"(offer_id);
CREATE INDEX departure_fare_price_version ON "DepartureFarePackagePrice"(template_version_id);
-- No snapshot is backfilled. Only real Booking confirmation may insert these rows.
CREATE TABLE "BookingFareSnapshot" (
    booking_passenger_id uuid PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES "Booking"(booking_id),
    offer_id uuid NOT NULL REFERENCES "DepartureFarePackage"(offer_id),
    template_version_id uuid NOT NULL REFERENCES "FarePackageTemplateVersion"(template_version_id),
    price_revision_id uuid NOT NULL REFERENCES "DepartureFarePackagePrice"(price_revision_id),
    ship_id uuid NOT NULL REFERENCES "Ship"(ship_id),
    logical_category varchar(100) NOT NULL,
    cabin_type varchar(30) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency_code varchar(3) NOT NULL CHECK (currency_code='USD'),
    price_unit varchar(10) NOT NULL CHECK (price_unit='PERSON'),
    template_name varchar(200) NOT NULL,
    template_description text,
    purchased_at timestamptz NOT NULL,
    FOREIGN KEY (booking_passenger_id,booking_id) REFERENCES "BookingPassenger"(booking_passenger_id,booking_id),
    FOREIGN KEY (price_revision_id,offer_id,template_version_id)
        REFERENCES "DepartureFarePackagePrice"(price_revision_id,offer_id,template_version_id)
);
CREATE INDEX booking_fare_booking ON "BookingFareSnapshot"(booking_id);
CREATE INDEX booking_fare_offer ON "BookingFareSnapshot"(offer_id);
CREATE INDEX booking_fare_price ON "BookingFareSnapshot"(price_revision_id);
CREATE INDEX booking_fare_version ON "BookingFareSnapshot"(template_version_id);
CREATE INDEX booking_fare_ship ON "BookingFareSnapshot"(ship_id);
CREATE TABLE "BookingFareBenefitSnapshot" (
    snapshot_benefit_id uuid PRIMARY KEY,
    booking_passenger_id uuid NOT NULL REFERENCES "BookingFareSnapshot"(booking_passenger_id),
    source_benefit_id uuid NOT NULL REFERENCES "FarePackageBenefit"(benefit_id),
    inclusion varchar(25) NOT NULL,
    onboard_activity_id uuid REFERENCES "OnboardActivity"(onboard_activity_id),
    shore_excursion_id uuid REFERENCES "ShoreExcursion"(shore_excursion_id),
    service_id uuid REFERENCES "Service"(service_id),
    quantity_limit integer,
    eligibility text NOT NULL,
    resource_name varchar(250) NOT NULL,
    occurrence_bindings jsonb NOT NULL,
    CHECK (num_nonnulls(onboard_activity_id,shore_excursion_id,service_id)=1),
    UNIQUE (booking_passenger_id,source_benefit_id)
);
CREATE INDEX booking_fare_benefit_passenger ON "BookingFareBenefitSnapshot"(booking_passenger_id);
CREATE INDEX booking_fare_benefit_definition ON "BookingFareBenefitSnapshot"(source_benefit_id);
