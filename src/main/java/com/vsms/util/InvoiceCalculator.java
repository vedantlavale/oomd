package com.vsms.util;

import com.vsms.exception.BusinessRuleException;
import java.math.BigDecimal;

/** Works out invoice amounts. All money is BigDecimal, two decimals, rounded half-up. */
public final class InvoiceCalculator {

    private InvoiceCalculator() {
    }

    public record Totals(BigDecimal laborCharge, BigDecimal partsTotal, BigDecimal subtotal,
                         BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal grandTotal) {
    }

    /**
     * subtotal = labour + parts; tax = subtotal x taxPercent / 100; grand total = subtotal + tax.
     *
     * @param taxPercent 0 to 100 (null means no tax)
     */
    public static Totals calculate(BigDecimal laborCharge, BigDecimal partsTotal, BigDecimal taxPercent) {
        BigDecimal labor = MoneyUtil.money(laborCharge);
        BigDecimal parts = MoneyUtil.money(partsTotal);
        BigDecimal rate = taxPercent == null ? BigDecimal.ZERO : taxPercent;
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("taxPercent", "The tax rate must be between 0 and 100.");
        }
        rate = rate.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal subtotal = labor.add(parts);
        BigDecimal tax = MoneyUtil.percentOf(subtotal, rate);
        return new Totals(labor, parts, subtotal, rate, tax, subtotal.add(tax));
    }
}
