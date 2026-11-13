# CASMS-75 integration evidence

Base is feat/CASMS-75-itinerary-management at f575a4ed15f26c8ee070ba821dcd04d5425f1f74. The HR and staffing port retains CASMS-75 itinerary planning and uses V97–V99 and V107–V112. V110/V111 are the catalog changes.

V107/V108 overlap objects from main V100/V101 even though the versions differ. Before merging lineages, design and test a migration adoption plan against both Flyway histories. V112 assumes the V18 partial index is present before it creates the replacement. Use a database backup for rollback and reconcile active assignments before restoring global per-employee uniqueness.

Catalog and package seeds remain opt-in. The employee and tour records are staging only. Verified tours require reviewed evidence and remain separate from live ShoreExcursion. Draft quotas are proposals and must be approved and tested against purchase/fulfillment before activation.

Reproduce the catalog integration tests with a disposable loopback PostgreSQL server, an empty casms_catalog_api database, and CASMS_CATALOG_TEST_PG_PORT set to its port. The migration tests create isolated databases, migrate from empty and V95, and apply both scripts twice. The integration suite must never use the default shared application datasource.
