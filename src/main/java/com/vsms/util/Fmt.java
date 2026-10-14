package com.vsms.util;

import com.vsms.config.VsmsProperties;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Formatting helpers for Thymeleaf pages: use as {@code ${@fmt.money(amount)}}. */
@Component("fmt")
public class Fmt {

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private final String symbol;

    public Fmt(VsmsProperties properties) {
        this.symbol = properties.billing().currencySymbol();
    }

    /** e.g. ₹12,345.50 */
    public String money(BigDecimal amount) {
        return symbol + number(amount);
    }

    /** e.g. 12,345.50 (no currency symbol) */
    public String number(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(INDIA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    /** Share of {@code part} in {@code whole} as a whole-number percentage, e.g. "42%". */
    public String percent(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) {
            return "0%";
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, java.math.RoundingMode.HALF_UP) + "%";
    }

    public String symbol() {
        return symbol;
    }
}
