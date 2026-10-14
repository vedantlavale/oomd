package com.vsms.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsLongEnoughPasswordsWithLetterAndDigit() {
        assertNull(PasswordPolicy.check("Workshop2026"));
        assertNull(PasswordPolicy.check("a1a1a1a1"));
    }

    @Test
    void rejectsShortOrSimplePasswords() {
        assertNotNull(PasswordPolicy.check(null));
        assertNotNull(PasswordPolicy.check("abc123"));
        assertNotNull(PasswordPolicy.check("onlyletters"));
        assertNotNull(PasswordPolicy.check("12345678"));
        assertNotNull(PasswordPolicy.check("a1".repeat(40)));
    }
}
