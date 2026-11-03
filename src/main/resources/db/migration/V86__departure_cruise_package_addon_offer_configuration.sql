-- SOURCE ONLY. No purchase/payment/registration state, seed data, history rewrite or migration application.
CREATE TABLE "CruisePackageAddonOffer" (
 addon_offer_id uuid PRIMARY KEY,
 cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id),
 ship_id uuid NOT NULL REFERENCES "Ship"(ship_id),
 onboard_activity_id uuid REFERENCES "OnboardActivity"(onboard_activity_id),
 shore_excursion_id uuid REFERENCES "ShoreExcursion"(shore_excursion_id),
 service_id uuid REFERENCES "Service"(service_id),
 resource_name varchar(250) NOT NULL,
 amount numeric(19,4) NOT NULL CHECK(amount>=0),
 currency_code varchar(3) NOT NULL CHECK(currency_code='USD'),
 pricing_unit varchar(30) NOT NULL,
 service_unit_code varchar(30),
 sales_windows jsonb NOT NULL,
 eligibility text NOT NULL,
 created_by uuid NOT NULL REFERENCES "User"(user_id),
 created_at timestamptz NOT NULL,
 published_by uuid REFERENCES "User"(user_id),
 published_at timestamptz,
 revision bigint NOT NULL DEFAULT 0,
 CHECK(num_nonnulls(onboard_activity_id,shore_excursion_id,service_id)=1),
 CHECK((published_by IS NULL)=(published_at IS NULL))
);
CREATE INDEX cruise_package_addon_cruise ON "CruisePackageAddonOffer"(cruise_id);
