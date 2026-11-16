CREATE TABLE "CruiseServiceLocationRequest" (
    cruise_id UUID NOT NULL REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES "Service"(service_id),
    deck_id UUID NOT NULL REFERENCES "Deck"(deck_id),
    requested_by UUID NOT NULL REFERENCES "User"(user_id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    note VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'RETURNED')),
    reviewed_by UUID REFERENCES "User"(user_id),
    review_note VARCHAR(500),
    revision BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (cruise_id, service_id)
);

CREATE VIEW "CruiseAvailablePackageService" AS
SELECT DISTINCT c.cruise_id, s.service_id
FROM "Cruise" c
JOIN "Deck" d USING(ship_id)
JOIN "DeckServiceAssignment" a USING(deck_id)
JOIN "Service" s USING(service_id)
LEFT JOIN "CruiseServiceLocationRequest" r ON r.cruise_id=c.cruise_id AND r.service_id=s.service_id
WHERE s.status='ACTIVE'
  AND (r.service_id IS NULL OR (r.status='CONFIRMED' AND d.deck_id=r.deck_id));
