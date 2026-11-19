CREATE TABLE "PassengerCardClaim" (
    booking_passenger_id uuid PRIMARY KEY REFERENCES "BookingPassenger"(booking_passenger_id),
    code_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz
);

CREATE TABLE "PassengerNfcCard" (
    booking_passenger_id uuid PRIMARY KEY REFERENCES "BookingPassenger"(booking_passenger_id),
    cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id),
    card_identifier varchar(64) NOT NULL,
    confirmed_by uuid NOT NULL REFERENCES "User"(user_id),
    confirmed_at timestamptz NOT NULL,
    UNIQUE (cruise_id, card_identifier)
);
