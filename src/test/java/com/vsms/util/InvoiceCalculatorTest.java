package com.vsms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vsms.exception.BusinessRuleException;
import com.vsms.util.InvoiceCalculator.Totals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoiceCalculatorTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void addsLabourAndPartsThenTax() {
        Totals totals = InvoiceCalculator.calculate(d("3999.00"), d("2580.00"), d("18"));
        assertEquals(d("6579.00"), totals.subtotal());
        assertEquals(d("1184.22"), totals.taxAmount());
        assertEquals(d("7763.22"), totals.grandTotal());
        assertEquals(d("18.00"), totals.taxPercent());
    }

    @Test
    void zeroTaxMeansNoTax() {
        Totals totals = InvoiceCalculator.calculate(d("1000"), d("250.50"), BigDecimal.ZERO);
        assertEquals(d("1250.50"), totals.subtotal());
        assertEquals(d("0.00"), totals.taxAmount());
        assertEquals(d("1250.50"), totals.grandTotal());
    }

    @Test
    void missingTaxRateIsTreatedAsNoTax() {
        assertEquals(d("100.00"), InvoiceCalculator.calculate(d("100"), d("0"), null).grandTotal());
    }

    @Test
    void roundsTaxHalfUpToTwoDecimals() {
        // 33.33 * 5% = 1.6665 -> 1.67
        Totals totals = InvoiceCalculator.calculate(d("33.33"), d("0"), d("5"));
        assertEquals(d("1.67"), totals.taxAmount());
        assertEquals(d("35.00"), totals.grandTotal());
    }

    @Test
    void doesNotLoseCentsWithBinaryFloatingPointTraps() {
        // 0.1 + 0.2 must be exactly 0.30 when done with BigDecimal
        Totals totals = InvoiceCalculator.calculate(d("0.10"), d("0.20"), BigDecimal.ZERO);
        assertEquals(d("0.30"), totals.grandTotal());
    }

    @Test
    void rejectsTaxRatesOutsideZeroToHundred() {
        assertThrows(BusinessRuleException.class, () -> InvoiceCalculator.calculate(d("1"), d("1"), d("-0.01")));
        assertThrows(BusinessRuleException.class, () -> InvoiceCalculator.calculate(d("1"), d("1"), d("100.01")));
        assertEquals(d("2.00"), InvoiceCalculator.calculate(d("1"), d("0"), d("100")).grandTotal());
    }
}
