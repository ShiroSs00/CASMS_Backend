-- Preserve historical USD amounts; new offers are explicitly created in VND by the API.
-- No exchange rate, amount rewrite, or currency relabeling is performed.
ALTER TABLE "CruisePackageAddonOffer"
    DROP CONSTRAINT "CruisePackageAddonOffer_currency_code_check";
ALTER TABLE "CruisePackageAddonOffer"
    ADD CONSTRAINT "CruisePackageAddonOffer_currency_code_check"
    CHECK (currency_code IN ('USD', 'VND'));
