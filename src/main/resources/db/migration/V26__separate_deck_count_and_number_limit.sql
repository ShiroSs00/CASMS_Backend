ALTER TABLE "Ship"
    ADD COLUMN deck_number_limit integer;

UPDATE "Ship"
SET deck_number_limit = total_decks;

ALTER TABLE "Ship"
    ADD CONSTRAINT ck_ship_deck_number_limit_positive
    CHECK (deck_number_limit IS NULL OR deck_number_limit > 0);
