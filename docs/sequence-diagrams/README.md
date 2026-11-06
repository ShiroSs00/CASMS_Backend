# API sequence documentation

Endpoints in this directory are relative to the `/api` context path. `API-INVENTORY.md` records every source mapping. Run `python scripts/sequence_docs.py --check` before completing API work.

The inventory and previously missing per-route files were reconstructed from the source scan during the service usage/catalog change. Automatically traced files are **not semantically verified**. They show direct calls and require review of transitive calls, security, transactions, database changes, branches and exception mappings. Existing hand-authored diagrams were preserved.

The reviewed end-to-end behavior for the service usage/activity catalog change is in [SERVICE-ACTIVITY-FLOWS.md](SERVICE-ACTIVITY-FLOWS.md). Affected per-route diagrams include an explicit semantic addendum linking the relevant section. The inventory distinguishes this review from the unrelated, unreviewed generated baseline. Do not interpret passing the checker as business correctness.

When changing an API, update the inventory, affected route files and end-to-end flow together. Follow shared services to every affected caller, including POS and cruise-scoped endpoints. Record request/response changes, authorization (including class annotations and mutation guards), transaction boundaries, locks, quota and financial behavior, and important errors. Never label an untouched generated stub as reviewed.

`python scripts/sequence_docs.py` regenerates files and can overwrite manual content; preserve reviewed files before using it.
