package com.casms.ship;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;

public final class ShipDeckCount {
    public static final int MIN = 8;
    public static final int MAX = 24;
    public static final String MESSAGE = "Passenger deck count must be an integer from 8 to 24";

    private ShipDeckCount() {}

    public static boolean isValid(Integer count) {
        return count != null && count >= MIN && count <= MAX;
    }

    public static final class Deserializer extends StdScalarDeserializer<Integer> {
        private final NumberDeserializers.IntegerDeserializer integerDeserializer =
                new NumberDeserializers.IntegerDeserializer(Integer.class, null);

        public Deserializer() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                return context.reportInputMismatch(Integer.class, MESSAGE);
            }
            return integerDeserializer.deserialize(parser, context);
        }
    }
}
