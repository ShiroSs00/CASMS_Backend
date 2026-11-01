package com.casms.pos;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosRequestValidationTest {
    @Test void loginRequiresExactlyOneMachineCredential() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertFalse(validator.validate(new PosDtos.LoginInput(null,null,null,null)).isEmpty());
            assertFalse(validator.validate(new PosDtos.LoginInput("secret",null,null,"token")).isEmpty());
            assertTrue(validator.validate(new PosDtos.LoginInput("secret",null,null,null)).isEmpty());
            assertTrue(validator.validate(new PosDtos.LoginInput(null,null,null,"token")).isEmpty());
        }
    }
    @Test void nullMemberCapIsRejectedBeforeAccountAllocation() {
        try (var factory=Validation.buildDefaultValidatorFactory()) {
            var input=new PosDtos.AllowanceInput(BigDecimal.TEN,Arrays.asList((PosDtos.MemberLimit)null));
            assertFalse(factory.getValidator().validate(input).isEmpty());
        }
    }
}
