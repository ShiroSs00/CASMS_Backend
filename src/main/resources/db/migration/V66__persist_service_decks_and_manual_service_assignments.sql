-- Technical/service levels are physical decks, distinct from positive passenger decks.
ALTER TABLE "Deck" ADD COLUMN service_level boolean NOT NULL DEFAULT false;
ALTER TABLE "Deck" ADD COLUMN model_metadata_json text;
ALTER TABLE "Deck" DROP CONSTRAINT IF EXISTS "Deck_deck_number_check";
ALTER TABLE "Deck" ADD CONSTRAINT deck_number_kind_check
    CHECK ((service_level AND deck_number <= 0) OR (NOT service_level AND deck_number > 0));

-- Manual catalogue assignments survive model reimport and are never populated from GLB areas.
CREATE TABLE "DeckServiceAssignment" (
    assignment_id uuid PRIMARY KEY,
    deck_id uuid NOT NULL REFERENCES "Deck"(deck_id),
    service_id uuid NOT NULL REFERENCES "Service"(service_id),
    created_at timestamptz NOT NULL,
    CONSTRAINT uk_deck_service_assignment UNIQUE (deck_id, service_id)
);
CREATE INDEX idx_deck_service_assignment_service ON "DeckServiceAssignment"(service_id);
