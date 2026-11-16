ALTER TABLE "Cruise"
    ADD COLUMN operating_budget_vnd NUMERIC(18,0),
    ADD CONSTRAINT cruise_operating_budget_positive
        CHECK (operating_budget_vnd > 0);
