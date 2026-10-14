package com.vsms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TextsTest {

    @Test
    void cleansWhitespace() {
        assertEquals("John Smith", Texts.clean("  John    Smith "));
        assertNull(Texts.clean("   "));
        assertNull(Texts.trimToNull(null));
    }

    @Test
    void registrationNumbersAreNormalised() {
        assertEquals("MH12AB1234", Texts.normalizeRegistration(" mh-12 ab 1234 "));
        assertNull(Texts.normalizeRegistration(" "));
    }

    @Test
    void searchPatternsAreLowerCaseAndWrapped() {
        assertEquals("%john smith%", Texts.like("  John   SMITH "));
        assertNull(Texts.like("   "));
        assertEquals("%mh12ab%", Texts.likeRegistration("MH 12-AB"));
    }

    @Test
    void numbersAreReadFromDocumentCodes() {
        assertEquals(12L, Texts.numberFromSearch("INV-000012"));
        assertEquals(7L, Texts.numberFromSearch("jc-7"));
        assertEquals(15L, Texts.numberFromSearch("15"));
        assertNull(Texts.numberFromSearch("john"));
        assertNull(Texts.numberFromSearch("MH12AB1234"));
        assertNull(Texts.numberFromSearch(null));
    }
}
