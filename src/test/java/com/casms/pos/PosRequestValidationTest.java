package com.casms.pos;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PosRequestValidationTest {
    @Test void nullMemberCapIsRejectedBeforeAccountAllocation() {
        try (var factory=Validation.buildDefaultValidatorFactory()) {
            var input=new PosDtos.AllowanceInput(BigDecimal.TEN,Arrays.asList((PosDtos.MemberLimit)null));
            assertFalse(factory.getValidator().validate(input).isEmpty());
        }
    }
}
