-- V44: BE-02 / G01: Luu source_version_revision trong Cruise va chinh sach ke thua duyet trong Itinerary
ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS source_version_revision BIGINT;
ALTER TABLE "Itinerary" ADD COLUMN IF NOT EXISTS approval_inherited BOOLEAN DEFAULT FALSE;
ALTER TABLE "Itinerary" ADD COLUMN IF NOT EXISTS content_modified_after_materialization BOOLEAN DEFAULT FALSE;

-- Cap nhat source_version_revision cho cac Cruise da lien ket source_version_id
UPDATE "Cruise" c
SET source_version_revision = v.revision
FROM "TripScheduleVersion" v
WHERE c.source_version_id = v.version_id
  AND c.source_version_revision IS NULL;

-- Cap nhat approval_inherited cho cac Itinerary da tao tu phien ban PUBLISHED
UPDATE "Itinerary" i
SET approval_inherited = TRUE
FROM "Cruise" c
JOIN "TripScheduleVersion" v ON c.source_version_id = v.version_id
WHERE i.cruise_id = c.cruise_id
  AND v.status = 'PUBLISHED'
  AND (i.approval_inherited IS NULL OR i.approval_inherited = FALSE);
