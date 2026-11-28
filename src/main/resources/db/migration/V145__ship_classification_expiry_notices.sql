CREATE TABLE ship_classification_expiry_notice (
    ship_id uuid NOT NULL REFERENCES "Ship" (ship_id) ON DELETE CASCADE,
    expiry_date date NOT NULL,
    PRIMARY KEY (ship_id, expiry_date)
);
