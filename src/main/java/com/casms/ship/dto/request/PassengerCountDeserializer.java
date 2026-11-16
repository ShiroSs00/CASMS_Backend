package com.casms.ship.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;

/** Accept a JSON integer without rounding a fractional passenger count. */
public final class PassengerCountDeserializer extends StdScalarDeserializer<Integer> {
    public PassengerCountDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Integer.class, "Số khách dự kiến phải là số nguyên.");
        }
        return parser.getIntValue();
    }
}
