package com.vsms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** All money is BigDecimal with two decimal places, rounded half-up. */
public final class MoneyUtil {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtil() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** {@code percent}% of {@code amount}, rounded to two decimals. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null) {
            return ZERO;
        }
        return amount.multiply(percent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }
}
