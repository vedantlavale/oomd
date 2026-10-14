package com.vsms.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.exception.BusinessRuleException;
import org.junit.jupiter.api.function.Executable;

/** Assertion helpers for business-rule failures. */
public final class Rules {

    private Rules() {
    }

    /** The action must fail with a business-rule message that contains {@code text}. */
    public static BusinessRuleException assertRejected(String text, Executable action) {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, action);
        assertTrue(ex.getMessage().toLowerCase().contains(text.toLowerCase()),
                "Expected message containing '" + text + "' but was: " + ex.getMessage());
        return ex;
    }

    /** Same, and the message must belong to the given form field. */
    public static BusinessRuleException assertRejected(String field, String text, Executable action) {
        BusinessRuleException ex = assertRejected(text, action);
        assertEquals(field, ex.getField());
        return ex;
    }
}
