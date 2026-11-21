"""Provision a projected operating budget through the CRM API in a disposable Booking Agent QA clone."""

import re
from uuid import UUID


def validate_budget(amount):
    if type(amount) is not int or not 0 < amount < 10**18:
        raise ValueError("operatingBudgetVnd must be a positive integer of up to 18 digits")


def seed_operating_budget(api, database, cruise_id, coordinator, amount):
    validate_budget(amount)
    if (not re.fullmatch(r"casms_ba_qa_[a-f0-9]{14}", database)
            or not re.fullmatch(r"http://127\.0\.0\.1:[0-9]+/api", api.base)):
        raise ValueError("Operating budget fixtures require a local Booking Agent QA clone")
    route = f"/cruise/{UUID(cruise_id)}/preparation/operating-budget"
    budget = api.call("read QA projected budget", "GET", route, coordinator)
    if budget["amountVnd"] is not None:
        return "EXISTING"
    if not budget["canEdit"]:
        return "BLOCKED"
    saved = api.call("seed QA projected budget", "PUT", route, coordinator,
                     payload={"expectedRevision": budget["revision"], "amountVnd": str(amount)})
    if saved["amountVnd"] != str(amount):
        raise ValueError("Persisted QA operating budget differs from the requested fixture")
    return "SAVED"
