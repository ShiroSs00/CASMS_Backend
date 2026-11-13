# CASMS-75 integration evidence

Base is feat/CASMS-75-itinerary-management at f575a4ed15f26c8ee070ba821dcd04d5425f1f74. The merged schema uses main migrations V96–V101 and CASMS-75 migrations V109–V112. V110/V111 introduce the catalog. V112 replaces the global active employee index created by V100.

The standalone V107/V108 migrations were exercised in disposable test databases. The merged lineage removes those duplicates and retains main's V100/V101. Any separately deployed database that already applied V107/V108 requires a migration-history adoption plan before it can use this branch. An older V95 database with duplicate active employee memberships also requires reconciliation before it can pass unchanged V100.

Catalog and package seeds remain opt-in. The employee and tour records are staging only. Verified tours require reviewed evidence and remain separate from live ShoreExcursion. Draft quotas are proposals and must be approved and tested against purchase/fulfillment before activation.

Reproduce the catalog integration tests with a disposable loopback PostgreSQL server, an empty casms_catalog_api database, and CASMS_CATALOG_TEST_PG_PORT set to its port. The migration tests create isolated databases, migrate from empty and V95, and apply both scripts twice. The integration suite must never use the default shared application datasource.
